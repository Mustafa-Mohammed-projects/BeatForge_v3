# BeatForge Studio 🎛️

استوديو متكامل لصنع بيتات الراب والأغاني على أندرويد — مكتوب بالكامل بلغة **Kotlin** و **Jetpack Compose**.

## المميزات
- **صانع البيتات**: سيكونسر 16 خطوة، 4 أنماط (A–D)، رسم الخطوات بالسحب، Swing، تغيير BPM.
- **أصوات مولَّدة برمجياً**: Kick / Snare / Clap / Hi-Hat / Open Hat / Tom / 808 / Perc / Rim بدون أي ملفات صوت.
- **استيراد عينات** (mp3, wav, m4a, ogg, flac) كأصوات إضافية.
- **الميكسر**: Fader وPan وPitch وMute وSolo لكل مسار مع عدّاد مستوى مباشر + Master.
- **التسجيل**: سجّل صوتك فوق البيت، أو استورد أغنية/أكابيلا.
- **المونتاج (Song)**: ترتيب الأنماط على Bars، وضع الكلمات والمقاطع على الخط الزمني، قص البداية والنهاية، التحكم بالصوت.
- **التصدير**: ملف WAV ستيريو (Loop أو الأغنية كاملة) ومشاركته مباشرة.
- **حفظ تلقائي** للمشاريع + تراجع (Undo).
- **شاشة بداية** تعرض `presplash.png` لمدة 3 ثوانٍ.

## استبدال صورة الـ Splash
ضع صورتك هنا (استبدل الملف الموجود):

```
app/src/main/res/drawable-nodpi/presplash.png
```

الملف الحالي صورة مؤقتة. تُعرض بملء الشاشة 3 ثوانٍ مع تكبير بطيء وشريط تقدّم.

## البناء عبر GitHub
1. أنشئ مستودعاً جديداً وارفع محتوى هذا المجلد كما هو (مع مجلد `.github`).
2. افتح تبويب **Actions** ← سيعمل **Build BeatForge APK** تلقائياً عند كل push (أو شغّله يدوياً بـ *Run workflow*).
3. بعد انتهائه حمّل الـ APK من **Artifacts** (`BeatForge-debug` و`BeatForge-release`).
4. لنشر نسخة في Releases: `git tag v1.0.0 && git push --tags`.

> نسخة release موقَّعة بمفتاح debug للتجربة. للنشر على Google Play أنشئ مفتاحك الخاص وبدّل `signingConfig` في `app/build.gradle.kts`.

## البناء محلياً
Android Studio (Koala أو أحدث) ← Open ← Run. المتطلبات: JDK 17، minSdk 26.

## هيكل المشروع
```
app/src/main/java/com/beatforge/studio/
 ├─ MainActivity.kt        نقطة الدخول + الـ Splash
 ├─ StudioViewModel.kt     الحالة، الحفظ، التسجيل، التصدير
 ├─ audio/                 المحرك: الأصوات، السيكونسر، الميكسر، التسجيل، WAV
 └─ ui/                    الواجهات: Home, Beat, Mixer, Song, Vocals
```
