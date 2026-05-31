Запуск:

```bash
./run.sh balanced
```

После появления приглашения `>` можно вставлять запросы:

```text
climate AND change
new ADJ york
new NEAR/5 york
"new york" AND NOT police
(obama OR biden) AND election
(science NEAR/8 research) AND NOT fiction
```

Для BM25/WAND-запросов переключите режим:

```text
:mode wand
climate change policy
machine learning neural network
space exploration nasa
```

Для точного BM25 без WAND:

```text
:mode exhaustive
economic growth inflation
```

Изменить число результатов и выгрузить последний результат:

```text
:k 20
world war history

```

# Boolean / positional
climate AND change
climate OR election
new ADJ york
new NEAR/5 york
"new york" AND NOT police
(obama OR biden) AND election
(science NEAR/8 research) AND NOT fiction
history AND NOT fiction
computer NEAR/6 science

# BM25 / WAND mode
climate change policy
machine learning neural network
world war history
space exploration nasa
economic growth inflation
