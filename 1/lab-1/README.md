# Лабораторная работа 1

## Алгоритмы

1. **FileSystemHashTable** — extensible hashing на mmap-файлах
2. **PerfectHashTable** — двухуровневое хеширование
3. **LSHIndex** — поиск дубликатов

## Датасеты

| Алгоритм | Файл | Ключ / Значение | Записей |
|---|---|---|---|
| FileSystemHashTable | `corpus_train.csv` | title / abstract | 17 189 |
| PerfectHashTable | `corpus_train.csv` | title / abstract | 16 420 |
| LSHIndex | `claims_train.csv` + `claims_test.csv` или `corpus_train.csv` | claim / title+abstract | 1 261 + 300 или 17 189 |

---

## 1. FileSystemHashTable (Extensible Hashing)

- Directory из 2^d указателей на бакеты, каждый бакет — mmap-файл.
- При переполнении бакета (> pageSize) — split: записи перераспределяются по следующему биту хеша, directory удваивается при необходимости.
- Записи в формате `[keyLen:4][key][valLen:4][val][flag:1]`.
- Удаление — один байт-флаг.

### Throughput (ops/s)

| Операция | 1 KB page | 4 KB page | 16 KB page | B/op |
|---|---:|---:|---:|---:|
| put | 7 171 | 35 127 | 137 776 | 14K–331K |
| getExisting | 8 671 665 | 5 640 201 | 3 426 841 | 347 |
| getMissing | 16 591 646 | 5 695 537 | 2 838 728 | 40 |
| updateExisting | 7 417 006 | 4 362 950 | 1 674 689 | 188–197 |
| deleteAndReinsert | 213 | 959 | 3 528 | 601K–11M |

### Пакетные (SingleShot, 17 189 записей)

| Операция | 1 KB | 4 KB | 16 KB | Alloc |
|---|---:|---:|---:|---:|
| putAll | 5 487 ms | 229 ms | 62 ms | 9.3 GB / 24 MB / 14 MB |
| getAll (1 000) | 0.36 ms | 0.58 ms | 0.75 ms | ~369–392 KB |

### Графики

#### Throughput

![put](graphs/put_thrpt_vs_pageSize.png)
![getExisting](graphs/getExisting_thrpt_vs_pageSize.png)
![getMissing](graphs/getMissing_thrpt_vs_pageSize.png)
![updateExisting](graphs/updateExisting_thrpt_vs_pageSize.png)
![deleteAndReinsert](graphs/deleteAndReinsert_thrpt_vs_pageSize.png)

#### SingleShot

![putAllDataset](graphs/putAllDataset_ss_vs_pageSize.png)
![getAllDataset](graphs/getAllDataset_ss_vs_pageSize.png)

#### Профилирование CPU (put)

![put CPU](graphs/fsht_put_prof.png)

Почти всё время — flush в диск (`FileChannel.write` → `UnixFileDispatcherImpl.write0`).

#### Профилирование Memory (put)

![put Mem](graphs/fsht_put_mem.png)

Аллокации при создании бакетов и split-операциях.

#### Профилирование CPU (getExisting)

![get CPU](graphs/fsht_get_prof.png)

Время уходит на линейное сканирование записей в бакете и побайтовое сравнение ключей.

#### Профилирование Memory (getExisting)

![get Mem](graphs/fsht_get_mem.png)


### Анализ

- **Page size создаёт трейдофф между чтением и записью.** Внутри каждого бакета записи хранятся последовательно, `get()` выполняет линейный проход по всем записям до нахождения ключа — индекса внутри бакета нет.
  - **GET деградирует при крупных страницах** (1 KB: 8.7M ops/s → 16 KB: 3.4M) — большая страница вмещает больше записей до split, поэтому линейный проход длиннее.
  - **PUT растёт с увеличением страницы** (1 KB: 7.2K → 16 KB: 138K ops/s) — при мелких страницах частые split-операции замедляют вставку.
- putAll при 1 KB — 5.5 секунды и 9.3 GB аллокаций (массовые splits), при 16 KB — 62 ms.

### Оптимизации

- Offset-индекс внутри бакета — массив `[hash:4][offset:4]` для каждой записи, отсортированный по хэшу; поиск по бинарному поиску вместо линейного прохода — устранит деградацию GET при крупных страницах
- Батчевание `saveMeta()` — сейчас каждый split делает синхронную запись метаданных; отложенный flush снизит I/O при массовых вставках (видно нап профиле)
- Перед решением о split убирать tombstones, сдвигая живые записи - это позволит реже совершать дорогую операцию сплита
---

## 2. PerfectHashTable

Primary hash → m=n бакетов, для бакета с k коллизиями — вторичная таблица k² с бесколлизионной хеш-функцией.

### Lookup (us/op)

| Операция | 1K | 10K | 45K | B/op |
|---|---:|---:|---:|---:|
| getExisting | 0.422 | 0.438 | 0.443 | ≈ 0 |
| getMissing | 0.187 | 0.191 | 0.193 | ≈ 0 |

### Build (SingleShot)

| Размер | Время |
|---:|---:|
| 1K | 0.90 ms |
| 10K | 6.96 ms |
| 45K | 11.08 ms |

### Графики

#### Lookup (avgt)

![getExisting avgt](graphs/getExisting_avgt_vs_size.png)
![getMissing avgt](graphs/getMissing_avgt_vs_size.png)

#### Lookup (thrpt)

![getExisting thrpt](graphs/getExisting_thrpt_vs_size.png)
![getMissing thrpt](graphs/getMissing_thrpt_vs_size.png)

#### Build (SingleShot)

![buildIndex](graphs/buildIndex_ss_vs_size.png)

#### Профилирование CPU (getExisting)

![get CPU](graphs/perfect_get_prof.png)

Всё CPU уходит на вычисление хеша (`polyHash`, `Math.floorMod`) — на primary и secondary уровнях.

#### Профилирование Memory (getExisting)

Flame graph пуст — async-profiler не зафиксировал ни одной аллокации. Lookup без аллокаций: два вычисления хеша + два обращения к массиву.

#### Профилирование CPU (buildIndex)

![build CPU](graphs/perfect_build_prof.png)

#### Профилирование Memory (buildIndex)

![build Mem](graphs/perfect_build_mem.png)

Аллокации — на вторичные таблицы и внутренние ArrayList.

### Анализ

- Lookup ~0.43 us независимо от размера — O(1).
- getMissing в ~2.3x быстрее: промах виден на первом уровне.
- Build линейный.

### Оптимизации

- MurmurHash3 вместо polynomial. (судя по профилю вычисление хэша довольно дорогое)
  Полиномиальный хэш на каждом символе делает целочисленное деление - это ~20-40 тактов CPU. Так на каждый символ ключа.
  MurmurHash3 использует только shift, xor, multiply — каждая операция 1-3 такта. Деления нет вообще - хэш будет считаться быстрее.
- Primitive arrays вместо Object[][]

---

## 3. LSHIndex

Текст → k-граммы (k=3) → MinHash int[100] (20 bands × 5 rows) → banding.

Используются два датасета: **claims** (1 261 train + 300 test) и **corpus** (17 189 документов, title+abstract). 

### Throughput (us/op)

| dataset | addDocument | queryCandidates | querySimilar |
|---|---:|---:|---:|
| claims | 14.3 (7.1K B/op) | 13.9 (6.6K B/op) | 14.1 (6.7K B/op) |
| corpus | 24.6 (11.0K B/op) | 24.6 (10.5K B/op) | 24.0 (10.7K B/op) |

### SingleShot

| dataset | buildIndex | findAllDuplicates (LSH) | findAllDuplicatesBruteForce |
|---|---:|---:|----------------------------:|
| claims (1 261 docs) | 19.0 ms (10.4 MB) | 1.1 ms (0.7 MB) |                    721.5 ms |
| corpus (17 189 docs) | 390.4 ms (225 MB) | 8.2 ms (0.7 MB) |                  251 880 ms |

Brute-force на claims: 18 GC сборок. LSH: GC ≈ 0. Brute-force на corpus: ~4.2 минуты, 306 GB аллокаций, 3805 GC сборок.

### Качество (claims, 1 261 документов, порог Jaccard ≥ 0.5)

| | LSH | Brute-force |
|---|---:|---:|
| Пар найдено | 1 640 | 1 720 |
| Время | 1.1 ms | 721 ms |

**Recall** 95.06% — **Precision** 99.70%

### Графики

#### Throughput (avgt)

![addDocument](graphs/addDocument_avgt_vs_dataset.png)
![queryCandidates](graphs/queryCandidates_avgt_vs_dataset.png)
![querySimilar](graphs/querySimilar_avgt_vs_dataset.png)

#### SingleShot

![buildIndex](graphs/buildIndex_ss_vs_dataset.png)
![findAllDuplicates](graphs/findAllDuplicates_ss_vs_dataset.png)

#### Профилирование CPU (addDocument)

![add CPU](graphs/lsh_add_prof.png)

Время — на shingling (вычисление k-грамм) и MinHash сигнатуру (100 хеш-функций).

#### Профилирование Memory (addDocument)

![add Mem](graphs/lsh_add_mem.png)

Аллокации ~7.1K B/op (claims) / ~11.0K B/op (corpus) — создание шинглов и сигнатуры.

#### Профилирование CPU (buildIndex)

![build CPU](graphs/lsh_build_prof.png)

#### Профилирование Memory (buildIndex)

![build Mem](graphs/lsh_build_mem.png)

### Анализ

- B/op ~7.1K на add (claims) и ~11.0K (corpus) — shingling + сигнатура.
- Brute-force 1.3 GB на claims, 306 GB на corpus (~4.2 мин, N² сравнений).
- LSH на corpus (17k docs) ищет дубликаты за ~8.2 ms.

### Оптимизации

- Rolling hash для shingling. Для каждой позиции n-граммы хэш считается каждый раз заново - это несколько итераций (в зависимости от shingleSize).
Rolling hash позволит при сдвиге окна на 1 символ пересчитать хэш за О(1) без цикла - это ускорит shingling (который судя по профилю занимает основное время)
- Считать MinHash одновременно с генерацией шинглов. Сейчас отдельным проходом считаются шинглы Set<Long>, а потом по этому множеству считается minHash.
То есть избавимся и от лежащего в памяти множества, и от лишнего прохода.
