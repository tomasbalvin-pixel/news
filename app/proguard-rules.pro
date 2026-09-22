# OkHttp platform helpers referenced reflectively.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Room generated implementations are looked up by name.
-keep class cz.balvin.news.data.local.** { *; }
