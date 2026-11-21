# Light Exposure Tracker — Lab 3

Лабораторна №3: REST-синхронізація сенсорних даних. Окрема локальна гілка
`lab3-cloud-sync`, робоча тека `D:\PMSS\lab3`. `main` містить ЛР2.
Злиття та push для ЛР3 не виконувалися. Скріншоти й матеріали звіту не входять у Git.

## Запуск

1. В Android Studio відкрити корінь цієї робочої теки, виконати Gradle Sync та Run.
2. Вкладка **Хмара** відкривається першою. Сенсори і BLE попередніх робіт збережені.
3. Емулятор → Extended Controls → Virtual Sensors → Additional sensors → Light:
   задати 420 лк. Device Pose дозволяє змінити X/Y/Z акселерометра.
4. Через 5 секунд після відкриття екрана почнеться передача. Очікуваний результат:
   зелений **SYNCED**, HTTP 201, Pending 0; нижче — фактичні JSON запиту й відповіді.
5. Змінити Light на 700 та нахил. Наступний запит міститиме нові показники.
6. Вимкнути Wi-Fi і мобільні дані **в емуляторі**. За наступного циклу буде червоний
   **PENDING** та Toast. Pending зберігається локально і переживає перезапуск процесу.
7. Відновити мережу: старі записи надсилатимуться FIFO з незмінним sampleId.
8. Logcat: `tag:LightHttp` — повні HTTP у debug; `tag:LightCloud` — події outbox.

## API та межі демонстрації

POST `https://jsonplaceholder.typicode.com/posts`, Retrofit 2.11.0, Gson 2.11.0,
OkHttp 4.12.0, Coroutines. API публічний, ключі й google-services.json не потрібні.
JSONPlaceholder повертає HTTP 201 та echo з id, але **не зберігає записи на сервері**.
GET /posts/101 не є перевіркою збереження. Це REST + Mock API, дозволений варіант Б.

`SensorData`: deviceId (випадковий installation UUID, не Android ID), timestamp
(Unix milliseconds), value (lux), type (`light`), x/y/z (м/с², nullable), sampleId
(UUID вимірювання). Формування JSON виконує Gson; дробовий роздільник — крапка.

## Архітектура та поведінка

`SensorManager → MainActivity → CloudViewModel → CloudRepository → CloudApi`.
Activity передає snapshotFlow стану сенсорів у ViewModel. ViewModel утримується при
пересозданні Activity, repository виконує послідовні suspend POST, StateFlow оновлює UI.
INTERNET і ACCESS_NETWORK_STATE оголошені в маніфесті. Android package/applicationId
`com.example.lab3`; назва проєкту — lab3.

- Новий знімок створюється раз на 5 секунд **після завершення попереднього циклу**.
  Перший запит також через 5 с; це не передача на кожну подію сенсора.
- За цикл надсилається максимум 3 записи з черги, по одному запиту одночасно.
- Валідація мережі через ConnectivityManager; IOException все одно обробляється,
  оскільки мережа може зникнути після перевірки. Тайм-аути: connect/read 10 с, call 15 с.
- Pending записується до SharedPreferences як JSON на Dispatchers.IO перед POST.
  ACK має містити успішний HTTP, id і відповідний sampleId; після нього запис видаляється.
- Mutex серіалізує цикли. Збереження диска й оновлення пам’яті захищено NonCancellable,
  щоб onPause не порушив їх узгодженість. Скасування мережі не стирає Pending.
- Черга обмежена 120 записами. За переповнення новий знімок пропускається з повідомленням
  у журналі; раніше прийняті записи не витісняються.
- onPause припиняє цикл і сенсори; немає WorkManager чи фонового сервісу. Повернення
  в додаток відновлює цикл. Серверний timestamp походить від годинника пристрою.
- Лічильник «Надіслано», остання відповідь, HTTP-код і журнал — поточна сесія ViewModel;
  після process death зберігаються лише Pending і deviceId. HTTP-код у Pending може бути
  кодом попереднього успішного запиту.
- sampleId стабільний під час повторів, але JSONPlaceholder не забезпечує серверну
  ідемпотентність. Для production потрібен серверний unique key / idempotency contract.
- Full BODY HTTP-лог увімкнений тільки для debug-збірки. API-ключів у коді немає.

## Перевірки

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain --max-workers=2
```

20 локальних тестів, 0 помилок. Нові 9 тестів перевіряють JSON POST через MockWebServer,
FIFO і ліміт batch, offline/restart, HTTP 503, невідповідний ACK, IOException,
скасування під час POST та запису на диск, ліміт черги й некоректні числа.
11 попередніх тестів BLE/індикатора також пройдено; instrumented-тест шаблону не запускався.
Android Lint: 0 errors, залишилися попередження залежностей і стилю.
Емулятор Medium Phone API 37: HTTP 201, зміна lux і XYZ, Pending offline,
перезапуск процесу та повтор з тим самим sampleId перевірені фактично.

Для скриншота трафіку вручну: Android Studio → View → Tool Windows → App Inspection
→ процес `com.example.lab3` → Network Inspector → HTTPS POST /posts → Request / Response.
Додаток має залишатися активним. Скриншот саме Network Inspector ще потрібно додати
до звіту; HTTP-логи та екран відповіді API вже підготовлені.

## Джерела

- https://jsonplaceholder.typicode.com/guide/
- https://square.github.io/retrofit/
- https://developer.android.com/studio/inspect/network
