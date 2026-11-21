# Light Exposure Tracker — лабораторна №4

Гілка `lab4-actuator-control` від `lab3-cloud-sync`. Відкрити цю теку в Android
Studio, запустити app. Package/applicationId `com.example.lab4`, minSdk 31,
compile/target API 37. Вкладки сенсорів, BLE і хмарної синхронізації ЛР3 збережено.

## Керування

`SensorManager → MainActivity → ControlViewModel → DecisionEngine → SmartActuator`.

- AUTO: lux < 300 вмикає лампу, lux ≥ 350 вимикає; 300–350 утримує стан.
  Це навчальні пороги, не норматив освітлення. null, NaN, від’ємні lux — OFF.
- MANUAL: кнопки «Увімкнути» / «Вимкнути»; інші джерела не змінюють стан.
- REMOTE: реальні Retrofit GET, наступний poll через 3 с після завершення запиту.

Режими взаємовиключні. Зміна режиму скидає OFF; повернення AUTO одразу перевіряє lux.
Вібрація 180 мс лише при переході OFF → ON. Повторні вимірювання чи GET не повторюють її.
onPause: polling cancelled, власний torch OFF, vibration cancelled, callback unregistered.
Після повернення REMOTE чекає новий GET; завершення процесу скидає режим у AUTO.

Жовте коло означає **віртуальну** лампу, не факт фізичного спалаху. SmartActuator
використовує VibratorManager.defaultVibrator, VibrationEffect.createOneShot,
CameraManager.setTorchMode та TorchCallback. Апаратний ліхтарик вмикається окремим
перемикачем із runtime-дозволом CAMERA; відсутність спалаху/дозволу або помилка
камери показуються явно з віртуальним fallback. VIBRATE — звичайний дозвіл.

## Cloud-to-Device

Public API: HTTPS GET `https://jsonplaceholder.typicode.com/todos/1`, сталий OFF.
JSONPlaceholder не зберігає зміни. Змінну зовнішню команду демонструє локальний
**Mock REST-хаб**, дозволена імітація з методички; це не Firebase і не публічна хмара.

1. ПК: `python demo-hub/server.py` (Python 3, без сторонніх пакетів).
2. Для емулятора: `adb reverse tcp:8787 tcp:8787`.
3. Браузер ПК: [консоль хаба](http://127.0.0.1:8787/).
4. Android: REMOTE → Demo hub. На ПК натиснути «Лампа ON» / «Лампа OFF».

GET `/command` повертає `{"id":2,"completed":true}`. id — додатне ціле число ревізії,
completed — суворий boolean (true=ON). Хаб збільшує id при команді; зберігає стан
у пам’яті до перезапуску. POST `/command` приймає `{"completed":false}`.

HTTP-помилка, невалідний JSON, timeout чи відсутність мережі → безпечний OFF;
наступний poll повторює спробу. Тайм-аут повного запиту 6 с. Затримка реакції —
до наступного poll + мережевий час. Це звичайне **polling**, не long polling або push.

HTTP тільки loopback (127.0.0.1/localhost) у debug. Release не показує Demo hub,
використовує HTTPS. Хаб слухає лише loopback ПК; порт не відкривається в LAN.
Фізичний смартфон може працювати через USB adb reverse. API-ключів немає.

## Перевірка

`gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

REST-хаб: `python -m unittest discover -s demo-hub`.

Тести: границі/гістерезис, невалідні lux, один імпульс на активацію, незалежність
режимів, пізні команди після onPause, помилка/відновлення зв’язку, строгий JSON,
GET через MockWebServer. Попередні тести ЛР1–3 збережено.

Емулятор → Virtual Sensors: 50 лк ON → 320 утримання → 350 OFF. Перевірити MANUAL,
потім REMOTE через зовнішню консоль. Logcat: `tag:LightControl`.

[CameraManager](https://developer.android.com/reference/android/hardware/camera2/CameraManager),
[VibratorManager](https://developer.android.com/reference/android/os/VibratorManager),
[JSONPlaceholder](https://jsonplaceholder.typicode.com/guide/).
