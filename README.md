# Light Exposure Tracker — лабораторна №2

Це один Android-проєкт у корені репозиторію. Лабораторні розділені історією:

- Перший коміт / тег `lab1` — сенсори та індикатор освітленості.
- Другий коміт / тег `lab2` — додано Mock BLE, Read/Write/Notify та керування хабом.

Поточна гілка `main` містить другу лабораторну. Для перегляду першої:
`git switch --detach lab1`; повернення до другої: `git switch main`.
В Android Studio відкривайте корінь репозиторію. Перед перемиканням збережіть
власні зміни; після перемикання виконайте Gradle Sync.

Package / applicationId другої лабораторної:
`com.example.lab2`, тому обидві лабораторні встановлюються одночасно.
Kotlin, Jetpack Compose, Android 12+ (minSdk 31), compile/target API 37.

## Запуск і демонстрація

1. Відкрити цю теку в Android Studio, дочекатися Gradle Sync та натиснути Run.
2. На вкладці **IoT-хаб · BLE** натиснути **Пошук пристроїв**.
   Через 2 секунди з’явиться Light Smart Hub (RSSI та адреса — mock).
3. Натиснути **Підключити**. Після 800 мс стан стане Connected.
4. В Extended Controls емулятора → Virtual Sensors → Additional sensors
   задати Light = 420. Або виконати `adb emu sensor set light 420`.
5. Увімкнути **Notify: OFF**, натиснути **Надіслати lux**, потім **Read статус**.
   Очікувані Read / Notify: `L:420.0;T:500`, підтвердження `ACK:LUX`.
6. **Ціль 500 лк** записує `SET:500`. Ціль є параметром хаба, а не медичною
   чи нормативною рекомендацією щодо освітленості.
7. **Імітувати втрату зв’язку** переводить у Disconnected; для відновлення
   натиснути **Підключити**, а потім знову увімкнути Notify.
8. Вкладка **Сенсори** зберігає функції лабораторної №1: lux, X/Y/Z та Canvas.

Термінал розташований нижче карток; він прокручується разом з екраном,
нові повідомлення показуються першими. Logcat: фільтр `tag:LightBle`.

## Архітектура

- `MainActivity` — створення монітора та MockBleConnector у lifecycleScope,
  запуск сенсорів в onResume, зупинка сенсорів і BLE в onPause.
- `sensors/WorkplaceSensorMonitor` — сенсори з лабораторної №1.
- `ble/IBleConnector` — контракт connect/disconnect/sendData та scan/read/notify.
- `ble/MockBleScanner` — suspend scan, delay(2000), один віртуальний пристрій.
- `ble/MockBleConnector` — MutableStateFlow / StateFlow, стани, журнал,
  перевірка пакетів, ACK та Notify, скасування Job і тайм-аут 3 секунди.
- `ble/BleProfile` — UUID, моделі стану та пакети.
- `ui/Lab2App` — Compose, collectAsStateWithLifecycle, керування хабом.
- `ui/LightExposureScreen`, `TiltPosition` — показники й індикатор з lab1.

Контролер та його CoroutineScope використовуються на Main. Немає блокувального
Thread.sleep у додатку; delay віддає потік. UI блокує паралельні GATT-операції,
контролер додатково відхиляє їх. disconnect скасовує незавершену операцію без ACK.
При повороті зі створенням нової Activity mock-стан і журнал починаються заново;
історія на диску та фоновий сервіс не реалізовані.

## Профіль і протокол

| Роль | UUID | Операції |
|---|---|---|
| Сервіс Light Smart Hub | `8f4b1000-6a6b-4d91-a922-42d0a62f0100` | Виявлення |
| STATUS | `8f4b1001-6a6b-4d91-a922-42d0a62f0100` | Read, Notify |
| COMMAND | `8f4b1002-6a6b-4d91-a922-42d0a62f0100` | Write |

Користувацькі UUID; це не стандартний профіль Bluetooth SIG. UTF-8, ASCII-команди,
без роздільника наприкінці, одна команда на один віртуальний запис, до 20 байтів.
Цей розмір відповідає ATT payload для базового MTU 23. Реального GATT/радіопакета,
переговорів MTU або запису CCCD у mock немає.

- `LUX:420.0` — передати 0..200000 лк, не більше одного десяткового знака;
  `ACK:LUX` — підтвердження застосування.
- `SET:500` — встановити ціле значення цілі 10..10000 лк; `ACK:SET`.
- `NOTIFY:ON` / `NOTIFY:OFF` — перемикання підписки; `ACK:NOTIFY`.
- Read STATUS → `L:420.0;T:500` (останнє значення хаба і ціль).
- Notify STATUS → такий самий формат після підписки та успішного запису.
  Без нової команди періодичних повідомлень немає.
- Неприпустимий формат, діапазон чи понад 20 байтів → ERROR у журналі без TX.

Стан: Disconnected → Scanning → Disconnected (список знайдено) → Connecting →
Connected → Data Transfer → Connected. Скасування, onPause, перешкода або
тайм-аут підключення повертають у Disconnected. Повторне підключення ручне;
автоматичний backoff не реалізований. Отримані раніше Read/Notify залишаються
останнім результатом; актуальний стан зв’язку показується окремо.

## Дозволи

Маніфест містить BLUETOOTH_SCAN (neverForLocation), BLUETOOTH_CONNECT,
ACCESS_FINE_LOCATION / BLUETOOTH / BLUETOOTH_ADMIN з maxSdkVersion=30 для
опису сумісного реального шару. Поточний minSdk=31. Mock не звертається до
Bluetooth API та не запитує runtime-дозволи. Для заміни на реальний BluetoothGatt
потрібно додати runtime-запит Nearby devices і обробку відмови користувача.

## Перевірка

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain --max-workers=2 --offline
```

Успішна збірка; шість тестів MockBleConnector + чотири TiltPosition + один
початковий тест шаблону. Сценарії BLE: затримка 2 с, Read/Write/Notify,
скасування сканування/підключення, перешкода під час запису, некоректні та
паралельні пакети, тайм-аут і можливість повторного підключення.
Матеріали перевірки емулятора збережено окремо для звіту.

Документація Android:
- https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
- https://developer.android.com/topic/libraries/architecture/coroutines
- https://developer.android.com/develop/connectivity/bluetooth/ble/transfer-ble-data
