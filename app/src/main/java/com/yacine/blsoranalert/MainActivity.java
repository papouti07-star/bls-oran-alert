package com.yacine.blsoranalert;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.content.Context;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;

public class MainActivity extends Activity {
    private static final String BOOKING_URL = "https://algeria.blsinternational.com/book-appointment";
    private static final String CHANNEL_ID = "bls_oran_alerts";
    private static final long CHECK_INTERVAL_MS = 5 * 60 * 1000L;

    private WebView webView;
    private TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean wasNoSlots = false;
    private boolean hasFirstResult = false;
    private boolean pageLoaded = false;

    private final Runnable periodicCheck = new Runnable() {
        @Override public void run() {
            if (pageLoaded && webView != null) {
                status.setText("آخر فحص: الآن — نعاود تحديث صفحة BLS بعد 5 دقائق ما دام التطبيق مفتوحاً.");
                webView.reload();
            }
            handler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel();
        requestNotificationPermission();
        buildScreen();
        webView.loadUrl(BOOKING_URL);
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 12, 16, 8);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("تنبيه مواعيد BLS وهران");
        title.setTextSize(22);
        title.setTextColor(Color.rgb(25, 45, 70));
        title.setPadding(0, 4, 0, 8);
        root.addView(title);

        status = new TextView(this);
        status.setText("افتح صفحة الحجز وسجّل الدخول بنفسك. المراقبة تعمل فقط والتطبيق مفتوح.");
        status.setTextSize(15);
        status.setTextColor(Color.DKGRAY);
        status.setPadding(0, 0, 0, 10);
        root.addView(status);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        Button refresh = new Button(this);
        refresh.setText("تحديث الآن");
        refresh.setOnClickListener(v -> webView.reload());
        buttons.addView(refresh, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button restart = new Button(this);
        restart.setText("فتح صفحة الحجز");
        restart.setOnClickListener(v -> webView.loadUrl(BOOKING_URL));
        buttons.addView(restart, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(buttons);

        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setLoadsImagesAutomatically(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                pageLoaded = true;
                status.setText("تم فتح الصفحة. إذا طلب الموقع تسجيل الدخول أو رمز تحقق، أدخله بنفسك.");
                inspectPage();
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private void inspectPage() {
        if (webView == null) return;
        String js = "(function(){var t=(document.body&&document.body.innerText||'').toLowerCase();" +
                "var no=t.indexOf('no slots available right now')>=0;" +
                "var booking=t.indexOf('select appointment slot')>=0 || t.indexOf('appointment')>=0;" +
                "return (no?'NO_SLOTS':'')+'|'+(booking?'BOOKING_PAGE':'OTHER');})()";
        webView.evaluateJavascript(js, value -> {
            if (value == null) return;
            boolean noSlots = value.contains("NO_SLOTS");
            boolean bookingPage = value.contains("BOOKING_PAGE");
            if (noSlots) {
                wasNoSlots = true;
                hasFirstResult = true;
                status.setText("حالة الموقع: لا توجد مواعيد حالياً. إعادة الفحص بعد 5 دقائق ما دام التطبيق مفتوحاً.");
            } else if (bookingPage) {
                status.setText("تغيّرت رسالة الصفحة أو لم تعد ظاهرة. افتح الصفحة وتأكد بنفسك؛ هذا ليس تأكيداً بوجود موعد.");
                if (hasFirstResult && wasNoSlots) {
                    sendNotification("تغيّرت صفحة BLS", "رسالة عدم توفر المواعيد لم تعد ظاهرة. افتح التطبيق وتحقق يدوياً؛ لم يتم تأكيد وجود موعد.");
                    wasNoSlots = false;
                }
                hasFirstResult = true;
            } else {
                status.setText("لم أستطع قراءة حالة المواعيد. قد تكون الصفحة تطلب تسجيل الدخول أو تحققاً إضافياً.");
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(periodicCheck);
        handler.postDelayed(periodicCheck, CHECK_INTERVAL_MS);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(periodicCheck);
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "تنبيهات مواعيد BLS", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("تنبيه عند تغيّر صفحة توفر المواعيد");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    private void sendNotification(String title, String message) {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Notification notification = builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title).setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(pending).setAutoCancel(true).build();
        ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(1, notification);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(periodicCheck);
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}