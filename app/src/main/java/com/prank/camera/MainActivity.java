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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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
    private static final int PHOTO_INTERVAL_MS = 3000;

    private static final String EMAIL_FROM = "metrobugitt@gmail.com";
    private static final String EMAIL_TO = "metrobugitt@gmail.com";
    private static final String EMAIL_SUBJECT = "Prank Camera Photos";
    private static final String EMAIL_PASSWORD = "ketufvduqebiogig";

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private Camera camera;
    private Button btnStart;
    private TextView txtStatus;
    private ProgressBar progressBar;
    private ImageView imgPreview;

    private Handler handler = new Handler(Looper.getMainLooper());
    private int photoCount = 0;
    private StringBuilder photoDataForEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        initViews();
    }

    private void initViews() {
        surfaceView = findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        btnStart = findViewById(R.id.btnStart);
        txtStatus = findViewById(R.id.txtStatus);
        progressBar = findViewById(R.id.progressBar);
        imgPreview = findViewById(R.id.imgPreview);

        btnStart.setOnClickListener(v -> startPrank());
    }

    private void startPrank() {
        if (camera == null) {
            Toast.makeText(this, "Camera not available!", Toast.LENGTH_SHORT).show();
            return;
        }
        photoCount = 0;
        photoDataForEmail = new StringBuilder();
        progressBar.setMax(PHOTO_COUNT);
        progressBar.setProgress(0);
        btnStart.setEnabled(false);
        txtStatus.setText("Starting prank! Photo: 0/" + PHOTO_COUNT);
        takeNextPhoto();
    }

    private void takeNextPhoto() {
        if (photoCount >= PHOTO_COUNT) {
            finishPrank();
            return;
        }
        photoCount++;
        progressBar.setProgress(photoCount);
        txtStatus.setText("Photo " + photoCount + "/" + PHOTO_COUNT);

        try {
            camera.takePicture(null, null, null, pictureCallback);
        } catch (Exception e) {
            Log.e(TAG, "Camera error: " + e.getMessage());
            handler.postDelayed(this::takeNextPhoto, 1000);
        }
    }

    private final Camera.PictureCallback pictureCallback = (data, camera) -> {
        photoDataForEmail.append("Photo #").append(photoCount).append("\n");

        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        imgPreview.setImageBitmap(bitmap);
        camera.startPreview();

        txtStatus.setText("Next photo in 3 sec...");
        handler.postDelayed(this::takeNextPhoto, PHOTO_INTERVAL_MS);
    };

    private void finishPrank() {
        txtStatus.setText("Sending email...");
        Toast.makeText(this, "Sending email...", Toast.LENGTH_LONG).show();
        sendEmailWithPhotos();
        btnStart.setEnabled(true);
        btnStart.setText("Start Again");
    }

    private void sendEmailWithPhotos() {
        new Thread(() -> {
            try {
                // SSL на порту 465 — самый надёжный способ для Gmail
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

                session.setDebug(true); // логи в logcat

                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(EMAIL_FROM));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(EMAIL_TO));
                message.setSubject(EMAIL_SUBJECT);
                message.setText("Prank Camera!\n\n" + photoDataForEmail.toString() + "\nYou've been pranked!");

                Transport.send(message);

                handler.post(() -> {
                    Toast.makeText(MainActivity.this, "Email sent!", Toast.LENGTH_SHORT).show();
                    txtStatus.setText("Email sent!");
                });

            } catch (Exception e) {
                Log.e(TAG, "Email error: " + e.getClass().getSimpleName() + " - " + e.getMessage(), e);
                handler.post(() -> {
                    String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    Toast.makeText(MainActivity.this, "Error: " + errMsg, Toast.LENGTH_LONG).show();
                    txtStatus.setText("Failed: " + errMsg);
                });
            }
        }).start();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            int cameraId = -1;
            int numberOfCameras = Camera.getNumberOfCameras();
            for (int i = 0; i < numberOfCameras; i++) {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    cameraId = i;
                    break;
                }
            }
            if (cameraId == -1 && numberOfCameras > 0) cameraId = 0;

            if (cameraId != -1) {
                camera = Camera.open(cameraId);
                camera.setPreviewDisplay(holder);
                camera.startPreview();
                txtStatus.setText("Camera ready! Press button to start");
            }
        } catch (Exception e) {
            Log.e(TAG, "Camera open error: " + e.getMessage());
            txtStatus.setText("Camera error: " + e.getMessage());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (camera != null) camera.startPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (camera != null) camera.stopPreview();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (camera != null) camera.startPreview();
    }
}
