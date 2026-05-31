# Полнотекстовый поиск

## Конфигурация измерений

Корпус: Wikipedia en `20231101`, `data/wikipedia.jsonl`, 3.22 GB decimal ≈ 3.0 GiB, 866 868 статей.  
- Benchmark-срез: 500 000 статей, 2 312 349 термов, 128 897 811 posting-ов, 314 927 398 токенов.  
- Замер: 3 прогрева, 10 расчетных попыток.

Чтобы быстрые операции не измерялись на долях миллисекунды, один расчетный раунд повторяет workload до примерно 250 ms, а в таблицах показана latency одного исходного batch-а запросов. 
JVM: OpenJDK 17, `-Xmx30g`.

Для сравнения `AND/ADJ/NEAR` workload строится на одинаковых парах термов: левый терм берется из частотной зоны словаря, правый — из среднечастотной. 
Это убирает перекос, когда `AND` получает легкую пару, а позиционные операторы получают две очень частые posting list-а.

## Сжатие: размер vs скорость

Профили кодеков пишутся как `docId/freq/pos`, `blockSize=256`. `docId` кодируются как block-rebase и delta поверх базового кодека.

### По коэффициенту сжатия

| Профиль | postings, MB |    Сжатие | AND qps  | ADJ qps  | OR qps | BM25 qps |
|---|---:|----------:|---:|---:|----------------:|---:|
| `pfor/bitpack/pfor` | 635.1 | **3.63x** | **71 652 ± 1 068** | 65 719 ± 896 |151 ± 1 | 285 ± 3 |
| `bitpack/bitpack/bitpack` | 652.8 |     3.53x | 69 164 ± 999 | 60 368 ± 565 | 145 ± 2 | 283 ± 4 |
| `vbyte/bitpack/vbyte` | 695.8 |     3.31x | 68 621 ± 770 | 58 159 ± 371 | 141 ± 3 | 281 ± 2 |
| `pfor/vbyte/pfor` | 699.5 |     3.30x | 68 901 ± 1 663 | 64 784 ± 529 | 145 ± 1 | **286 ± 1** |
| `vbyte/vbyte/vbyte` | 760.2 |     3.03x | 66 664 ± 4 541 | 59 084 ± 1 434 | 149 ± 2 | 285 ± 2 |

![Compression size](charts/compression_size.png)
![Compression tradeoff](charts/compression_tradeoff.png)

Лучший размер среди всех профилей и лучший `AND/OR` среди сжатых профилей дает `pfor/bitpack/pfor`.

Он уменьшает postings до 635.1 MB, то есть сжимает raw-поток в 3.63 раза.

По скорости `AND` лучшим среди сжатых профилей тоже стал `pfor/bitpack/pfor`.

`OR qps` намного ниже, чем `AND/ADJ`, потому что `OR` должен вернуть объединение posting list-ов.

BM25 перебирает большой набор кандидатов, читает частоты и длины документов, считает score и поддерживает top-K. 

Поэтому он ближе по стоимости к широкому `OR`, а не к короткому `AND`.

Skip-таблицы во всех экспериментах кодируются фиксированно через DeltaCodec(VarByteCodec)

## Размер блока и skip-list

| blockSize | postings, MB | AND ms ± 95% CI | AND qps ± 95% CI |
|---:|---:|---:|---:|
| 16 | 668.2 | 15.718 ± 0.196 | 10 179 ± 127 |
| 32 | 652.1 | 10.939 ± 0.035 | 14 627 ± 46 |
| 64 | 642.9 | 5.870 ± 0.020 | 27 259 ± 92 |
| 128 | 637.7 | 3.415 ± 0.015 | 46 851 ± 207 |
| 256 | 635.1 | 2.317 ± 0.073 | 69 053 ± 2 167 |
| 512 | 634.0 | **1.842 ± 0.020** | **86 867 ± 953** |

![Block size](charts/blocksize.png)

На `pfor/bitpack/pfor` размер postings продолжает немного снижаться до `512`: служебных skip-записей
становится меньше, а блочное кодирование позиций уже не добавляет лишний заголовок на каждый документ.

По скорости `AND` лучшая точка в этом прогоне — `blockSize=512`. 
Маленькие блоки проигрывают из-за частых переходов по skip-таблице и большого числа мелких декодов. 

## Backend: память vs mmap

Сравнение идет на одинаковом workload-е и одном сжатии `pfor/bitpack/pfor`, `blockSize=256`.

`memory` — распакованный `MemoryIndex`; `mmap-*` — on-disk индекс, где меняется размер mmap-сегмента: 128, 512 и 1024 MiB.

| backend | AND ms ± 95% CI | OR ms ± 95% CI | ADJ ms ± 95% CI | NEAR ms ± 95% CI | BM25 ms ± 95% CI |
|---|---:|---:|---:|---:|---:|
| `memory` | **0.398 ± 0.007** | **836 ± 8** | **0.325 ± 0.002** | **0.386 ± 0.005** | **462 ± 1** |
| `mmap-128m` | 2.213 ± 0.018 | **1162 ± 1** | 2.575 ± 0.085 | 2.564 ± 0.027 | **526 ± 1** |
| `mmap-512m` | **2.209 ± 0.028** | 1164 ± 4 | **2.421 ± 0.024** | **2.491 ± 0.030** | 531 ± 4 |
| `mmap-1024m` | 2.242 ± 0.023 | 1175 ± 3 | 2.469 ± 0.026 | 2.541 ± 0.030 | 537 ± 9 |

![Backend latency split](charts/backend_latency.png)

Размеры mmap-сегмента меняют результат умеренно. 
В этом прогоне 128 MiB выглядит наиболее ровной точкой для `AND/NEAR`, 512 MiB — для `ADJ/BM25`, а 1024 MiB не дает устойчивого выигрыша. 
Разница между mmap-конфигурациями меньше разрыва между memory и mmap.

На позиционных запросах основная цена не в размере сегмента, а в декоде позиций.

## Recall / QPS: WAND 

WAND пытается не считать полный BM25 для документов, которые уже не смогут попасть в top-K.
Для каждого терма запроса берется верхняя оценка его вклада.
Во время поиска WAND складывает такие верхние оценки у текущих курсоров и сравнивает сумму с текущим порогом top-K.
Если даже максимум не дотягивает до порога, документ пропускается без полного скоринга.

Параметр `F` управляет строгостью отсечения: проверяется не просто `upperBound > threshold`, а
`upperBound > threshold * F`. 
При `F=1.0` отсечение точное и recall совпадает с exhaustive. 
При `F>1.0` алгоритм пропускает больше кандидатов, поэтому QPS растет, а recall может падать.

| Режим | recall@10 | ms ± 95% CI | qps ± 95% CI |
|---|---:|---:|---:|
| exhaustive | 1.000 | 466.9 ± 0.8 | 343 ± 1 |
| WAND `F=1.00` | 1.000 | 513.4 ± 2.9 | 312 ± 2 |
| WAND `F=1.02` | 0.972 | 470.9 ± 2.9 | 340 ± 2 |
| WAND `F=1.05` | 0.780 | 314.7 ± 5.6 | 508 ± 9 |
| WAND `F=1.10` | 0.511 | 131.1 ± 0.3 | 1 220 ± 3 |
| WAND `F=1.20` | 0.392 | 21.7 ± 0.1 | 7 380 ± 36 |
| WAND `F=1.40` | 0.378 | 3.2 ± 0.0 | 49 404 ± 423 |
| WAND `F=1.70` | 0.374 | 1.1 ± 0.0 | 139 777 ± 693 |
| WAND `F=2.00` | 0.374 | 0.8 ± 0.0 | 208 034 ± 728 |
| WAND `F=3.00` | 0.372 | 0.5 ± 0.0 | 326 148 ± 3 597 |

![Recall pareto](charts/recall_pareto.png)
![Recall factor](charts/recall_vs_factor.png)

`F=1.0` точен, но не быстрее exhaustive на этом корпусе: верхние границы BM25 слишком слабые, отсечь почти нечего. 
При росте `F` QPS резко растет, но recall быстро падает.

## Профили async-profiler

Профили сняты на выбранной сбалансированной конфигурации: `pfor/bitpack/pfor`, `blockSize=256`,`MAXDOCS=500000`.

### Query operations

Каждый профиль ниже снят отдельным потоком запросов: 

`AND` содержит только `AND`, `OR` только `OR`, `ADJ` только точную близость `ADJ/1`, `NEAR` только оконную близость, `BM25` только ранжирование.

| Операция | CPU HTML | Alloc HTML |
|---|---|---|
| AND | [cpu](profiles/query-balanced-and-cpu.html) | [alloc](profiles/query-balanced-and-alloc.html) |
| OR | [cpu](profiles/query-balanced-or-cpu.html) | [alloc](profiles/query-balanced-or-alloc.html) |
| ADJ | [cpu](profiles/query-balanced-adj-cpu.html) | [alloc](profiles/query-balanced-adj-alloc.html) |
| NEAR | [cpu](profiles/query-balanced-near-cpu.html) | [alloc](profiles/query-balanced-near-alloc.html) |
| BM25 | [cpu](profiles/query-balanced-bm25-cpu.html) | [alloc](profiles/query-balanced-bm25-alloc.html) |

**AND CPU**
Оригинал: [HTML](profiles/query-balanced-and-cpu.html).

![AND CPU](profiles/query-balanced-and-cpu.png)

**AND allocation**
Оригинал: [HTML](profiles/query-balanced-and-alloc.html).

![AND alloc](profiles/query-balanced-and-alloc.png)

**OR CPU**
Оригинал:
[HTML](profiles/query-balanced-or-cpu.html).

![OR CPU](profiles/query-balanced-or-cpu.png)

**OR allocation**
Оригинал: [HTML](profiles/query-balanced-or-alloc.html).

![OR alloc](profiles/query-balanced-or-alloc.png)

**ADJ CPU**
Оригинал: [HTML](profiles/query-balanced-adj-cpu.html).

![ADJ CPU](profiles/query-balanced-adj-cpu.png)

**ADJ allocation**
[HTML](profiles/query-balanced-adj-alloc.html).

![ADJ alloc](profiles/query-balanced-adj-alloc.png)

**NEAR CPU**
[HTML](profiles/query-balanced-near-cpu.html).

![NEAR CPU](profiles/query-balanced-near-cpu.png)

**NEAR allocation**
[HTML](profiles/query-balanced-near-alloc.html).

![NEAR alloc](profiles/query-balanced-near-alloc.png)

**BM25 CPU** 
[HTML](profiles/query-balanced-bm25-cpu.html).

![BM25 CPU](profiles/query-balanced-bm25-cpu.png)

**BM25 allocation**
Оригинал:
[HTML](profiles/query-balanced-bm25-alloc.html).

![BM25 alloc](profiles/query-balanced-bm25-alloc.png)

Основная интерпретация совпадает с benchmark-ами: `AND` легкий из-за skip/advance, `OR` обязан сканировать большие списки, а `ADJ/NEAR` добавляют чтение и проверку позиций.

## Итоговая конфигурация

Для демонстрации используется:

```text
./run.sh balanced
docId/freq/pos = pfor/bitpack/pfor
blockSize = 256
ranking = WAND F=1.0
```

Для точного exhaustive BM25 на той же конфигурации:

```text
./run.sh max-recall
docId/freq/pos = pfor/bitpack/pfor
blockSize = 256
ranking = exhaustive
```
