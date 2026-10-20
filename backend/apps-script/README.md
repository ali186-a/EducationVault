# EducationVault — Apps Script بیک اینڈ

یہ بیک اینڈ آپ کے (مالک کے) Google Drive میں فائلیں رکھتا ہے۔ ہر درخواست پر Firebase ٹوکن کی تصدیق، invitation list کی جانچ اور فولڈر کی ملکیت کی جانچ ہوتی ہے۔

## فائلیں

| فائل | کام |
|---|---|
| `appsscript.json` | ڈپلائمنٹ سیٹنگز (Execute as: Me، Access: Anyone) اور اجازتیں |
| `src/Code.gs` | `doGet` / `doPost` اور routing |
| `src/Auth.gs` | Firebase `accounts:lookup` سے تصدیق، allowlist، ایڈمن چیک |
| `src/Storage.gs` | Drive فولڈرز، `_metadata.json`، ownership چیک |
| `src/Handlers.gs` | upload / list / download / rename / delete / invite / revoke |
| `src/Util.gs`, `src/Config.gs` | ویلیڈیشن، JSON جوابات، مستقل اقدار |
| `tests/` | Node ٹیسٹ (جعلی Drive اور جعلی Firebase کے ساتھ) |

## ٹیسٹ چلانا (Node 18+)

```
cd backend/apps-script
npm test
```

## Script Properties (Apps Script → Project Settings → Script Properties)

| نام | لازمی؟ | قدر |
|---|---|---|
| `FIREBASE_API_KEY` | ہاں | Firebase Web API key (راز نہیں، مگر کمیٹ بھی نہ کریں) |
| `FIREBASE_PROJECT_ID` | ہاں | Firebase پروجیکٹ ID |
| `ADMIN_EMAIL` | ہاں | آپ کی ای میل (واحد ایڈمن) |
| `MAX_UPLOAD_BYTES` | نہیں | ڈیفالٹ `5242880` (5 MB)، زیادہ سے زیادہ `10485760` (10 MB) |
| `REQUIRE_EMAIL_VERIFIED` | نہیں | ڈیفالٹ `true`۔ ایڈمن کے لیے ہمیشہ لازمی ہے |
| `REQUIRE_INVITE` | نہیں | ڈیفالٹ `false`: ہر وہ شخص جس کا ای میل تصدیق شدہ ہو (یا Google اکاؤنٹ) ایپ استعمال کر سکتا ہے۔ `true` کرنے سے دعوت (allowlist) کا نظام واپس آ جاتا ہے |
| `ROOT_FOLDER_ID` | خودکار | پہلی درخواست پر خود بن جاتا ہے، ہاتھ سے نہ لکھیں |

## ڈپلائمنٹ (مختصر)

1. <https://script.google.com> پر نیا پروجیکٹ بنائیں۔
2. `src/*.gs` کی ہر فائل الگ فائل کے طور پر پیسٹ کریں، اور Project Settings میں "Show appsscript.json" آن کر کے `appsscript.json` کا مواد پیسٹ کریں۔
3. اوپر والی Script Properties سیٹ کریں۔
4. Deploy → New deployment → Web app: **Execute as: Me**، **Who has access: Anyone**۔
5. پہلی بار Drive کی اجازت (Authorize) دیں۔
6. جو URL ملے (`.../exec` پر ختم) وہ Android ایپ کی configuration میں لگائیں۔

کوڈ بدلنے پر Deploy → Manage deployments → Edit → New version دینا ضروری ہے، ورنہ پرانا کوڈ چلتا رہے گا۔

## API

ہر درخواست `POST`، body JSON، اور `Content-Type: text/plain` (preflight سے بچنے کے لیے):

```json
{ "action": "listDocs", "idToken": "<Firebase ID token>", "limit": 30, "cursor": "0" }
```

ٹوکن body میں اس لیے ہے کہ Apps Script ویب ایپ custom headers نہیں پڑھ سکتی۔
جواب ہمیشہ HTTP 200 ہوتا ہے (Apps Script status code نہیں بدل سکتا)؛ نتیجہ `ok` سے پہچانیں:

```json
{ "ok": true,  "data": { } }
{ "ok": false, "error": { "code": "NOT_INVITED", "message": "..." } }
```

Actions: `ping`, `me`, `listCategories`, `addCategory`, `listDocs`, `getDoc`, `upload`, `download`, `updateDoc`, `deleteDoc`, `inviteUser`, `revokeUser`, `listInvites`

Error codes: `BAD_REQUEST`, `UNAUTHENTICATED`, `TOKEN_EXPIRED`, `ACCOUNT_DISABLED`, `EMAIL_NOT_VERIFIED`, `NOT_INVITED`, `FORBIDDEN`, `NOT_FOUND`, `FILE_TOO_LARGE`, `UNSUPPORTED_TYPE`, `LIMIT_REACHED`, `METADATA_CORRUPT`, `STORAGE_UNAVAILABLE`, `STORAGE_ERROR`, `NOT_CONFIGURED`, `UPSTREAM_ERROR`, `BUSY`, `REQUEST_TOO_LARGE`, `INTERNAL`

`upload` کے فیلڈز: `title`، `category`، `mimeType`، `contentBase64`، اختیاری `fileName`، `institution`، `passingYear`، `issueDate` (`YYYY-MM-DD`)، `notes`۔

## سیکیورٹی کے اہم فیصلے اور حدود

- **ٹوکن کی تصدیق:** ہر درخواست پر `accounts:lookup` کال ہوتی ہے۔ صرف JWT ڈی کوڈ ہو جانا کافی نہیں مانا جاتا۔ `aud`/`iss`/uid کی اضافی جانچ بھی ہوتی ہے۔ Firebase بند، حذف یا disabled اکاؤنٹ کو رد کرتا ہے۔ ٹوکن revoke ہونے پر `accounts:lookup` کا رویہ اصل Firebase پروجیکٹ کے ساتھ ٹیسٹ کر کے دیکھنا چاہیے؛ یہ ٹیسٹ جعلی Firebase پر ممکن نہیں۔
- **ای میل ویری فکیشن لازمی:** Firebase کی ای میل/پاس ورڈ سائن اپ ای میل کی ملکیت ثابت نہیں کرتی۔ بغیر ویری فکیشن کے کوئی بھی آپ کی ای میل سے پہلے رجسٹر ہو کر ایڈمن بن سکتا تھا۔ اس لیے ایڈمن کے لیے ویری فکیشن ہمیشہ لازمی ہے، اور Android ایپ میں verification-email کا فلو بنانا ہوگا۔
- **Drive اجازت:** `DriveApp` کو مکمل `drive` scope چاہیے (`drive.file` صرف Advanced Drive Service کے ساتھ چلتا ہے)۔ یہ اجازت صرف آپ، یعنی اسکرپٹ کے مالک، دیتے ہیں۔ صارفین کو کبھی نہیں دکھائی جاتی۔
- **Drive ID کبھی کلائنٹ کو نہیں دی جاتی:** کلائنٹ صرف سرور کی بنائی ہوئی `docId` استعمال کرتا ہے، جو صرف اس کی اپنی metadata میں ڈھونڈی جاتی ہے۔
- **Delete:** فائل Drive کے Trash میں جاتی ہے (مالک تقریباً 30 دن میں بحال کر سکتا ہے)۔
- **اپ لوڈ:** فائل base64 میں JSON کے اندر جاتی ہے (سائز ~33٪ بڑھتا ہے)۔ 5 MB ڈیفالٹ محفوظ ہے۔ 10 MB تک پہنچنے سے پہلے اصل ڈپلائمنٹ پر موبائل نیٹ ورک پر ٹیسٹ کریں۔
- **کوٹے:** Apps Script کی روزانہ حدیں اور `UrlFetchApp` کی حدیں ہیں (ہر درخواست پر ایک Firebase کال ہوتی ہے)۔ 3–4 صارفین کے لیے یہ عام طور پر کافی ہیں۔ تازہ حدیں Google کی دستاویز میں دیکھیں۔
