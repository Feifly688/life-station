package com.qixi.life;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.provider.MediaStore;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private final ActivityResultLauncher<Intent> fileChooserLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (filePathCallback == null) return;
                Uri[] results = null;
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) results = new Uri[]{uri};
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            });

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 沉浸式：状态栏透明，内容延伸至上，浅色背景用深色图标
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(
                    getWindow().getDecorView().getSystemUiVisibility()
                            | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // localStorage
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        // 备份导出：JS 调用原生写文件
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return openOutsideApp(request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return openOutsideApp(Uri.parse(url));
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view,
                                             ValueCallback<Uri[]> callback,
                                             FileChooserParams fileChooserParams) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                Intent intent = fileChooserParams.createIntent();
                try {
                    fileChooserLauncher.launch(intent);
                } catch (Exception e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "无法打开文件选择", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });

        // 兜底下载监听（blob: 在部分设备触发）
        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            if (url.startsWith("blob:")) {
                // blob 下载由 JS Interface 处理，这里忽略
                return;
            }
            try {
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                req.setMimeType(mimetype);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "QiFile/" + guessName(contentDisposition, url));
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                dm.enqueue(req);
            } catch (Exception e) {
                Toast.makeText(this, "下载失败", Toast.LENGTH_SHORT).show();
            }
        });

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl("file:///android_asset/index.html");
        }
    }

    private String guessName(String disposition, String url) {
        if (!TextUtils.isEmpty(disposition)) {
            int idx = disposition.indexOf("filename=");
            if (idx >= 0) {
                String name = disposition.substring(idx + 9).replace("\"", "").trim();
                if (name.length() > 0) return sanitizeFilename(name);
            }
        }
        return sanitizeFilename(url.substring(url.lastIndexOf('/') + 1));
    }

    private boolean openOutsideApp(Uri uri) {
        String scheme = uri.getScheme();
        if ("file".equalsIgnoreCase(scheme)
                && uri.toString().startsWith("file:///android_asset/")) {
            return false;
        }
        if ("about".equalsIgnoreCase(scheme)) return false;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开该链接", Toast.LENGTH_SHORT).show();
        }
        // 不允许外部页面留在带有 Android JS 接口的 WebView 中。
        return true;
    }

    private static String sanitizeFilename(String filename) {
        String sanitized = filename.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return sanitized.isEmpty() ? "download" : sanitized;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (webView == null) {
            super.onBackPressed();
            return;
        }

        // 这是一个单页应用，WebView 历史并不反映应用内页面。
        // 先让页面关闭弹层或返回上一级，只有首页才退出 Activity。
        webView.evaluateJavascript(
                "(window.handleAndroidBack && window.handleAndroidBack()) ? 'handled' : 'exit'",
                value -> {
                    if (!"\"handled\"".equals(value) && !isFinishing()) {
                        MainActivity.super.onBackPressed();
                    }
                });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            setContentView(new View(this));
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    /** JS 调用：导出文本文件到公共 Downloads/QiFile/ */
    public static class WebAppInterface {
        private final Context ctx;
        WebAppInterface(Context c) { this.ctx = c; }

        /** JS 调用：把数据写入 APP 私有目录（filesDir/data/<key>.json）——替代 localStorage，卸载前一直有效 */
        @JavascriptInterface
        public void saveData(String key, String value) {
            try {
                File dir = new File(ctx.getFilesDir(), "data");
                if (!dir.exists() && !dir.mkdirs()) return;
                File f = new File(dir, sanitize(key) + ".json");
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(value.getBytes(StandardCharsets.UTF_8));
                fos.close();
            } catch (Exception ignored) {}
        }

        /** JS 调用：读取私有目录数据，无则返回 null */
        @JavascriptInterface
        public String loadData(String key) {
            try {
                File f = new File(new File(ctx.getFilesDir(), "data"), sanitize(key) + ".json");
                if (!f.exists()) return null;
                FileInputStream fis = new FileInputStream(f);
                byte[] b = new byte[(int) f.length()];
                int off = 0;
                while (off < b.length) {
                    int n = fis.read(b, off, b.length - off);
                    if (n < 0) break;
                    off += n;
                }
                fis.close();
                return new String(b, 0, off, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return null;
            }
        }

        /** JS 调用：删除私有目录数据 */
        @JavascriptInterface
        public void removeData(String key) {
            try {
                File f = new File(new File(ctx.getFilesDir(), "data"), sanitize(key) + ".json");
                if (f.exists()) f.delete();
            } catch (Exception ignored) {}
        }

        private String sanitize(String key) {
            return key.replaceAll("[^A-Za-z0-9_.-]", "_");
        }

        @JavascriptInterface
        public void exportFile(String filename, String content, String mime) {
            filename = sanitizeFilename(filename);
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+：用 MediaStore 写公共下载目录（卸载后仍在）
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                    values.put(MediaStore.Downloads.MIME_TYPE, mime);
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/QiFile");
                    ContentResolver resolver = ctx.getContentResolver();
                    // 同名文件先删除避免重复
                    resolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            MediaStore.Downloads.DISPLAY_NAME + "=? AND "
                                    + MediaStore.Downloads.RELATIVE_PATH + "=?",
                            new String[]{filename, Environment.DIRECTORY_DOWNLOADS + "/QiFile"});
                    Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri != null) {
                        java.io.OutputStream os = resolver.openOutputStream(uri);
                        os.write(data);
                        os.close();
                        toast("已导出到 下载/QiFile/" + filename);
                        return;
                    }
                }
                // Android 9 及以下：直接写公共 Downloads
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "QiFile");
                if (!dir.exists() && !dir.mkdirs()) {
                    fallbackCache(filename, content);
                    return;
                }
                File file = new File(dir, filename);
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(data);
                fos.close();
                toast("已导出到 Downloads/QiFile/" + filename);
            } catch (Exception e) {
                fallbackCache(filename, content);
            }
        }

        private void fallbackCache(String filename, String content) {
            try {
                File file = new File(ctx.getExternalCacheDir(), filename);
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(content.getBytes(StandardCharsets.UTF_8));
                fos.close();
                toast("已导出到缓存/" + filename);
            } catch (Exception e) {
                toast("导出失败：" + e.getMessage());
            }
        }

        private void toast(final String msg) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show());
        }
    }
}
