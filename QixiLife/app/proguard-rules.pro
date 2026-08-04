# WebView 应用无需额外混淆规则
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
