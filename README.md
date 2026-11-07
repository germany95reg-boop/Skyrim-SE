# Skyrim SE Mobile — v2.23 (геометрическое авто-вписывание камеры)

**На основании реального Android-отчёта v2.22:** 21 PASS · Adreno 840 · 3349 NIF треугольников · normal-safe · 29.9/59.7/119.9 FPS · PixelCopy **565 из 49152 пикселей (1.15%)**, область модели 43×49 пикселей из 192×256. Простое приближение камеры могло обрезать модель при вращении, поэтому **v2.23** вычисляет безопасное расстояние по **реальным вершинам геометрии и перспективе Vulkan** с проверкой 24 углов авто-вращения. На стройных/плоских моделях это уменьшает излишние пустые поля относительно прежней большой сферы. В копируемом отчёте теперь отдельный пункт **«Авто-вписывание камеры NIF»** с дистанцией; PixelCopy позволит измерить результат.

Сохранены BSA/NIF, NiNode/BSFadeNode, diffuse/normal DDS, alpha-cutout, Adreno normal-safe, FPS 30/60/∞, ручной масштаб и исходные файлы Skyrim. Android версия 2.23.0, versionCode 46, прежняя стабильная подпись.

[Подробная инструкция для проверки v2.23](DEVICE_TEST_V223_RU.md).

---

## История: v2.22


**Реальный отчёт v2.21:** Samsung SM-S948B, Adreno 840, **21 PASS / 0 WARN / 0 FAIL**, normal-safe и PixelCopy работали; 175 из 49152 пикселей отличались от фона, что может означать маленькую модель **или** тонкую геометрию. v2.22 измеряет ещё и **ширину и высоту области модели**. При маленьком размере по обеим осям отчёт честно покажет WARN о возможной проблеме масштабирования, но длинные сетки и верёвки не будут ошибочно считаться слишком мелкими.

В диагностике также остаются DDS alpha-cutout, две карты diffuse/normal, NiNode/BSFadeNode и три FPS-режима. Версия Android 2.22.0 (versionCode 45), прежний package ID и подпись для обновления на месте.

[Как проверить v2.22 на телефоне](DEVICE_TEST_V222_RU.md).

---

## История: v2.21


**Новое:** в родных Android Vulkan DDS-шейдерах (full, Adreno normal-safe, atlas-safe, basic) почти полностью прозрачные пиксели diffuse DDS (alpha < 5%) теперь **отсекаются** вместо рисования непрозрачных прямоугольников. В автотесте и просмотрщике BSA добавлена отдельная диагностика **«Diffuse DDS альфа-маска»** с подсчётом непрозрачности только цветных карт; normal-map alpha/gloss не путаются с прозрачностью.

Сохранены иерархия NiNode, парные материалы diffuse/normal, normal-safe на Adreno, PixelCopy-проверка реального GPU SurfaceView и переключение 30/60/∞ FPS. Код версии 44, версия 2.21.0, прежний сертификат обновления.

Это **бинарная маска**, не смешивание полупрозрачных поверхностей, не полный SkyrimSE.exe.

[Инструкция к аппаратному тесту v2.21](DEVICE_TEST_V221_RU.md).

---

## История: v2.20


**Новое:** проверка того, что Vulkan действительно выводит изображение модели, а не только увеличивает счётчик кадров. Android **PixelCopy** однократно копирует уменьшенный кадр из `SurfaceView` в оперативную память и считает признаки видимой геометрии. Кадр не сохраняется и не передаётся; в отчёте появляется отдельный PASS/WARN «Изображение Vulkan PixelCopy».

Сохранены NiNode/BSFadeNode, точные diffuse+normal пары v2.19, шейдер `normal-safe` для Adreno, 30/60/∞ FPS, пакет и сертификат подписи. Важно: PixelCopy — лишь проверка неоднотонного GPU-изображения, **не доказательство попиксельной точности и не запуск SkyrimSE.exe**.

[Инструкция по физическому тесту v2.20](DEVICE_TEST_V220_RU.md).

---

## История: v2.19


**По результатам реального автотеста v2.18: 19 PASS · Adreno 840 · Vulkan normal-safe**. В v2.19 две части NIF могут использовать один diffuse DDS и разные normal DDS без неверного общего номера материала. Графический атлас учитывает **пару diffuse/normal**, сохраняет нейтральные normal DDS и проверенный atlas-safe fallback. Пользовательская фраза об автоматической отправке убрана из конца текста автотеста по запросу пользователя; автоматическая отправка данных не добавлялась.

Android versionCode 42, versionName 2.19.0, прежняя подпись и package ID. Устанавливается поверх v2.18.

[Инструкция по проверке на телефоне](DEVICE_TEST_V219_RU.md).

---

## История: v2.18

**Новое:** составной NIF-просмотр теперь может собирать diffuse DDS и normal DDS по реальным связям BSTriShape → shader → texture set в один ограниченный 2048×2048 Vulkan атлас. Добавлен облегчённый normal-safe шейдер для Adreno, сохранился проверенный diffuse-only atlas-safe fallback. В автотесте отдельно указано, были ли normal DDS только декодированы или **действительно применены GPU**. Результаты 30/60/∞ FPS и NiNode/BSFadeNode остаются в полном отчёте.

Режим normal-safe — диагностическое приближение, не полное Skyrim освещение. По-прежнему не реализован SkyrimSE.exe и игровой движок.

[Проверка v2.18 на телефоне и расшифровка Normal DDS GPU](DEVICE_TEST_V218_RU.md).

---

## История разработки: v2.17

**Новое v2.17 (Android versionCode 40):** составная статическая NIF теперь учитывает подтверждённые родительские преобразования **NiNode/BSFadeNode** (смещения, повороты, масштаб) до нормализации геометрии. Пер-материальные diffuse DDS, автоматический 30/60/∞ FPS тест и прежняя нативная Vulkan-архитектура сохранены.

**На телефоне:** нажми «ЗАПУСТИТЬ ПОЛНЫЙ АВТОТЕСТ НА ТЕЛЕФОНЕ» и пришли строку **NiNode/BSFadeNode** вместе с общим отчётом. При неподдерживаемом графе локальная геометрия сохранится, но появится предупреждение.

[Инструкция автотеста v2.17](DEVICE_TEST_V217_RU.md).

---

## История разработки: v2.16

**Новое в v2.16 (versionCode 39):** автотест прямо на телефоне теперь сам переключает настоящую Vulkan-сцену через **30 FPS → 60 FPS → без программного ограничения**, измеряя фактические кадры отдельно на каждом этапе, без ручных нажатий. В отчёт выводятся заданный лимит, действительный FPS, длительность, число кадров и подтверждение лимита через native Vulkan. Результаты не подменяются расчётными значениями, тест имеет ограничение времени.

[Инструкция автотеста v2.16 на русском](DEVICE_TEST_V216_RU.md).

Прежний пакет и тестовый сертификат подписи сохранены для установки APK поверх v2.15. Приложение остаётся диагностическим BSA/NIF/DDS просмотрщиком, а не полноценным игровым движком Skyrim.

---

## История: v2.15

**Новое: автоматический тест прямо на телефоне.** В лаунчере нажми «ЗАПУСТИТЬ ПОЛНЫЙ АВТОТЕСТ НА ТЕЛЕФОНЕ». После этого приложение само проверит реальный ARM64/Vulkan, сохранённый BSA, составной NIF и DDS, откроет и закроет реальную Vulkan-сцену и предложит **скопировать / поделиться итоговым отчётом**. Эмулятор не нужен.

Автотест использует только ранее сохранённые Android SAF-разрешения на архив и папку Data. Если они не сохранены, выбираешь их **один раз** в просмотрщике BSA; в дальнейшем достаточно одной кнопки. Игровые файлы не изменяются, отчёт никуда не отправляется без согласия пользователя.

**v2.15.0 (versionCode 38)** сохраняет прежний package name и сертификат для установки поверх v2.14. GitHub CI продолжает проверять JVM-парсеры, Vulkan GLSL, компиляцию и подпись ARM64 APK, но обязательный эмуляторный этап заменён настоящими тестами внутри приложения на устройстве.

[Руководство по одной кнопке и отчёту на русском](DEVICE_TEST_V215_RU.md).

---

## История разработки: v2.13

Android ARM64 **read-only** Skyrim SE BSA/NIF/DDS diagnostic Vulkan viewer, not a Skyrim game engine.

## New in v2.13: composite NIF with separate DDS for each supported part
- After «Найти DDS каждой части NIF», tap **«Показать ВСЕ части NIF с DDS · v2.13»**.
- Reuses the existing globally normalized composite geometry and the validated BSTriShape → BSLightingShaderProperty → BSShaderTextureSet references.
- Packs up to **15 distinct diffuse DDS** into a bounded **2048×2048** CPU atlas. A per-triangle flat shader attribute selects the correct tile; each shape repeats its original UV within that tile. Several shapes may reuse the same decoded DDS.
- Shapes with missing, malformed or excess diffuse DDS remain as neutral gray geometry (not silently assigned another material). A summary reports how many parts have fallback material.
- Source DDS decode cache capped at **8 million pixels**; GPU atlas and geometry remain bounded. No archive writing or game assets redistributed.
- Single-shape DDS and normal-map Vulkan preview are retained. **New:** a bounded bilinear resize can match different normal/diffuse first-mip sizes (when the native normal atlas supports the target), correcting the v2.12 “отличается размер DDS” report.
- Original composite geometry-only preview, individual shape selection, camera, FPS, persistent SAF Data permission, user icon and APK in-place upgrades are preserved.
- Android **versionCode 36 / versionName 2.13.0**, same `applicationId`, same development-only signing certificate.
- Synthetic Java tests and source contracts cover atlas material mapping, fallback, normal resampling and JNI/GLSL integration.

**Scope and limits:** This is now a **textured composite static geometry preview**; normals are *not* yet shown for multiple materials simultaneously. Parent NiNode/BSFadeNode transforms, Skyrim shading, animation, game scripts and SkyrimSE.exe porting are unsupported. A partially read BSA index may conceal other DDS files.

See [DEVICE_TEST_V213_RU.md](DEVICE_TEST_V213_RU.md) for the real-device checklist.
