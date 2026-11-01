# WEYKY — النواة الذكية والواجهة التكتيكية للأندرويد

مشروع أندرويد متكامل مبني باستخدام **Kotlin** و **Jetpack Compose** مع نظام تكيّف ذكي للهواتف والأجهزة اللوحية، ومحرك أوامر صوتية ونصية ثنائي اللغة (العربية والإنجليزية).

---

## خطوات تشغيل المشروع على Android Studio

### 1. تحميل ملفات المشروع
- من القائمة العلوية في المنصة، اختر **Download ZIP** (تحميل كملف مضغوط) أو **Export to GitHub**.
- إذا قمت بتحميل ملف `ZIP`، قم بفك الضغط عنه في مجلد على جهازك.

### 2. فتح المشروع في Android Studio
1. افتح برنامج **Android Studio** (يُفضل أحدث إصدار مثل Ladybug أو Meerkat أو أحدث).
2. اختر **Open** وحدد المجلد الرئيسي للمشروع (`WEYKY`).
3. انتظر حتى يقوم **Gradle Sync** بمزامنة الحزم وتجهيز ملفات البناء تلقائياً:
   - يتم توليد ملف التوقيع `debug.keystore` تلقائياً من `debug.keystore.base64` عند أول مزامنة.
   - تأكد من توفر **Android SDK 36** من خلال `SDK Manager` داخل Android Studio.

### 3. إعداد المفاتيح السرية (اختياري)
- يعتمد التطبيق على ملف `.env.example` كإعداد افتراضي لبناء المشروع بدون أخطاء.
- إذا أردت إضافة مفاتيح خاصة بك محلياً، يمكنك نسخ ملف `.env.example` إلى ملف جديد باسم `.env` في المجلد الرئيسي للمشروع.

### 4. دعم النماذج الصوتية المحلية (Offline Voice Models - اختياري)
لتشغيل التعرف الصوتي المحلي بدون إنترنت أو بدون خدمات جوجل على الأجهزة الحقيقية:
- **محرك Vosk:** ضع ملفات النموذج داخل المسار:
  `app/src/main/assets/model-ar/` أو `app/src/main/assets/vosk-model-ar/`
- **محرك Sherpa-ONNX:** ضع ملفات النموذج (`tokens.txt` وملفات `.onnx`) داخل المسار:
  `app/src/main/assets/sherpa-onnx-ar/`

### 5. البناء والتشغيل
- اختر جهازك المتصل عبر USB (أو المحاكي Emulator) من شريط الأدوات العلوي في Android Studio.
- اضغط على زر **Run (`▶`)** أو الاختصار `Shift + F10` لتثبيت وتشغيل تطبيق **WEYKY**.

---

## حل مشكلة `debug.keystore not found` عند التشغيل المحلي

إذا ظهر لك الخطأ التالي في Android Studio:
`Keystore file '.../debug.keystore' not found for signing config 'debugConfig'`
(بسبب تجاهل ملف `debug.keystore` في `.gitignore` عند التصدير أو الاستنساخ)، افتح نافذة **Terminal** داخل Android Studio (في مجلد المشروع `/home/wertyoupia/StudioProjects/weyky-ai`) ونفّذ هذا الأمر الواحد لفك تشفير المفتاح المرفق فوراً:

```bash
base64 -d debug.keystore.base64 > debug.keystore
```

أو في حال عدم وجود ملف `debug.keystore.base64`، يمكنك توليد مفتاح جديد مباشرة بالأمر:

```bash
keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
```

