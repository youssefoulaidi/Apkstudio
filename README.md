# ApkStudio 📱⬆️🐙⬇️📦

**ApkStudio** هو تطبيق أندرويد يحوّل أي مشروع أندرويد بصيغة **ZIP** إلى نسخة **Debug APK** عبر البناء السحابي على **GitHub Actions** — كل ذلك من هاتفك، بدون كمبيوتر وبدون Android Studio.

## ⬇️ تنزيل التطبيق

- **صفحة الإصدارات:** https://github.com/youssefoulaidi/Apkstudio/releases
- **آخر نسخة (v1.0.0):** https://github.com/youssefoulaidi/Apkstudio/releases/download/v1.0.0/ApkStudio-debug.apk

## كيف يعمل؟

```
1. اختر ملف ZIP لمشروع أندرويد
2. التطبيق يحلّل المشروع ويفحصه على هاتفك
3. اربط حساب GitHub (رمز وصول شخصي) واختر مستودعاً من حسابك
4. التطبيق يرفع المشروع + ملف البناء السحابي إلى المستودع
5. GitHub Actions يبني المشروع (3-8 دقائق عادة)
6. التطبيق يراقب البناء، ثم ينزّل الـ APK ويثبّته
```

## المزايا

- ✅ استيراد مشروع ZIP وفكّه وتحليله (الحزمة، الموديول، إصدارات SDK/AGP/Gradle/Kotlin)
- ✅ كشف تلقائي لنوع المستودع (**عام/خاص**) مع تنبيه حول دقائق البناء
- ✅ إنشاء مستودع جديد من داخل التطبيق
- ✅ توليد ملف GitHub Actions متوافق تلقائياً (اختيار JDK المناسب)
- ✅ رفع المشروع عبر Git Data API (يدعم المستودعات الفارغة أيضاً)
- ✅ تشغيل البناء ومراقبته حيّاً (الحالة + الخطوات + السجل الكامل)
- ✅ تنزيل الـ APK + حفظ نسخة في التنزيلات + التثبيت بضغطة زر
- ✅ **عرض دقيق للأخطاء**: كل شاشة توضح المشكلة بالضبط وماذا تفعل لحلها
- ✅ إشعار عند انتهاء البناء + إمكانية مغادرة شاشة البناء ومتابعته لاحقاً
- ✅ واجهة عربية وإنجليزية (RTL مدعوم)
- ✅ الرمز يُحفظ مشفّراً على الهاتف فقط

## المتطلبات

- Android 8.0 (API 26) فما فوق
- حساب GitHub + رمز وصول شخصي (PAT) بصلاحيتي `repo` و `workflow`
- التطبيق يرشدك لإنشاء الرمز خطوة بخطوة من داخله

## بناء المشروع (للمطورين)

```bash
./gradlew assembleDebug
# الناتج: app/build/outputs/apk/debug/app-debug.apk
```

أو تلقائياً عبر GitHub Actions عند الدفع — انظر `.github/workflows/build-apk.yml`.
آخر نسخة مبنية تجدها في مجلد `release/` (يُحدَّث تلقائياً).

## البنية التقنية

- **اللغة**: Kotlin — **الواجهة**: Jetpack Compose + Material3
- **الشبكة**: Retrofit + OkHttp + Moshi على GitHub REST API
- **الحفظ الآمن**: EncryptedSharedPreferences
- **التنقل**: Navigation Compose
- **البناء السحابي**: GitHub Actions (JDK Temurin + Gradle + upload-artifact)

```
app/src/main/java/com/apkstudio/app/
├── MainActivity.kt / ApkStudioApp.kt / AppGraph.kt
├── data/
│   ├── github/      # GitHubApi + Models + GitHubClient + GitHubRepository
│   ├── project/     # ProjectAnalyzer + PushCollector + WorkflowGenerator
│   └── prefs/       # SessionManager
├── ui/
│   ├── screens/     # Welcome/Login/Repos/Import/Analysis/Upload/Build/Result/Settings
│   ├── components/  # Common.kt
│   └── theme/       # Theme.kt
└── util/            # AppError + ZipUtils + ApkInstaller + BuildNotifications
```

## ملاحظات مهمة

- البناء يستهلك من دقائق GitHub Actions **الخاصة بحساب المستخدم**: المستودعات العامة = بناء غير محدود، الخاصة = 2000 دقيقة/شهر مجاناً.
- يجب تفعيل GitHub Actions في المستودع (مفعّلة افتراضياً).
- النسخة الحالية تنتج **Debug APK** (موقّعة تلقائياً بمفتاح التطوير).
