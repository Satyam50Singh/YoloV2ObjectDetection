package satya.app.objectdetectionyolov2;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import satya.app.objectdetectionyolov2.ml.Yolov2;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_ID_MULTIPLE_PERMISSIONS = 100101;
    ImageView imageView;
    int imageSize = 416;
    TextView tvNoOfObjectDetected, tvPriority, tvObjectCoordinates, tvClassName;
    RelativeLayout rlProgressBar;
    Button btnCaptureImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageView = findViewById(R.id.imageView);
        tvNoOfObjectDetected = findViewById(R.id.tvNoOfObjectDetected);
        tvPriority = findViewById(R.id.tvPriority);
        tvClassName = findViewById(R.id.tvClassName);
        rlProgressBar = findViewById(R.id.common_progress_bar);
        btnCaptureImage = findViewById(R.id.btn_capture_image);

        btnCaptureImage.setOnClickListener(view -> {
            if (checkAndRequestPermissions()) {
                chooseImage(this);
            } else {

            }
        });


//        tvObjectCoordinates = findViewById(R.id.tvObjectCoordinates);

//        try {
//            @SuppressLint("UseCompatLoadingForDrawables")
//            Drawable d = getDrawable(R.drawable.img2); // the drawable (Captain Obvious, to the rescue!!!)
//            Bitmap bitmap = ((BitmapDrawable) d).getBitmap();
//            int dimension = Math.min(bitmap.getWidth(), bitmap.getHeight());
//            bitmap = ThumbnailUtils.extractThumbnail(bitmap, dimension, dimension);
//            imageView.setImageBitmap(bitmap);
//
//            bitmap = Bitmap.createScaledBitmap(bitmap, imageSize, imageSize, false);
//            classifyImage(bitmap);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

    public boolean checkAndRequestPermissions() {
        int storagePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int cameraPermission = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA);

        List<String> listPermissionsNeeded = new ArrayList<>();
        if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.CAMERA);
        }
        if (storagePermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), REQUEST_ID_MULTIPLE_PERMISSIONS);
            return false;
        }
        return true;
    }


    // Handled permission Result
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_ID_MULTIPLE_PERMISSIONS:
                if (ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), "FlagUp Requires Access to Camara.", Toast.LENGTH_SHORT).show();
                    checkAndRequestPermissions();
                } else if (ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), "FlagUp Requires Access to Your Storage.", Toast.LENGTH_SHORT).show();
                    checkAndRequestPermissions();
                } else {
                    chooseImage(MainActivity.this);
                }
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + requestCode);
        }
    }

    private void chooseImage(MainActivity mainActivity) {
        // create a menuOption Array
        final CharSequence[] optionsMenu = {"Take Photo", "Choose from Gallery", "Exit"};
        // create a dialog for showing the optionsMenu
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // set the items in builder
        builder.setItems(optionsMenu, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (optionsMenu[i].equals("Take Photo")) {
                    // Open the camera and get the photo
                    Intent takePicture = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                    startActivityForResult(takePicture, 0);
                } else if (optionsMenu[i].equals("Choose from Gallery")) {
                    // choose from  external storage
                    Intent pickPhoto = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    startActivityForResult(pickPhoto, 1);
                } else if (optionsMenu[i].equals("Exit")) {
                    dialogInterface.dismiss();
                }
            }
        });
        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_CANCELED) {
            switch (requestCode) {
                case 0:
                    if (resultCode == RESULT_OK && data != null) {
                        Bitmap selectedImage = (Bitmap) data.getExtras().get("data");
                        int dimension = Math.min(selectedImage.getWidth(), selectedImage.getHeight());
                        selectedImage = ThumbnailUtils.extractThumbnail(selectedImage, dimension, dimension);
                        imageView.setImageBitmap(selectedImage);

                        selectedImage = Bitmap.createScaledBitmap(selectedImage, imageSize, imageSize, false);
                        classifyImage(selectedImage);
                    }
                    break;
                case 1:
                    if (resultCode == RESULT_OK && data != null) {
                        Uri selectedImage = data.getData();
                        String[] filePathColumn = {MediaStore.Images.Media.DATA};
                        if (selectedImage != null) {
                            Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
                            if (cursor != null) {
                                cursor.moveToFirst();
                                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                                String picturePath = cursor.getString(columnIndex);
                                Bitmap imageBitmap = BitmapFactory.decodeFile(picturePath);
                                int dimension = Math.min(imageBitmap.getWidth(), imageBitmap.getHeight());
                                imageBitmap = ThumbnailUtils.extractThumbnail(imageBitmap, dimension, dimension);
                                imageView.setImageBitmap(imageBitmap);

                                imageBitmap = Bitmap.createScaledBitmap(imageBitmap, imageSize, imageSize, false);
                                classifyImage(imageBitmap);
                                cursor.close();
                            }
                        }
                    }
                    break;
            }
        }
    }

    private void classifyImage(Bitmap image) {
        try {
            Yolov2 model = Yolov2.newInstance(this);

            // Creates inputs for reference.
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 416, 416, 3}, DataType.FLOAT32);
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3);
            byteBuffer.order(ByteOrder.nativeOrder());
            inputFeature0.loadBuffer(byteBuffer);


            int[] intValues = new int[imageSize * imageSize];
            image.getPixels(intValues, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());

            int pixel = 0;

            for (int i = 0; i < imageSize; i++) {
                for (int j = 0; j < imageSize; j++) {
                    int val = intValues[pixel++];
                    byteBuffer.putFloat(((val >> 16) & 0xFF) * (1.f / 1));
                    byteBuffer.putFloat(((val >> 8) & 0xFF) * (1.f / 1));
                    byteBuffer.putFloat((val & 0xFF) * (1.f / 1));
                }
            }

            TensorBuffer inputFeature1 = TensorBuffer.createFixedSize(new int[]{1, 1}, DataType.FLOAT32);
            ByteBuffer byteBuffer2 = ByteBuffer.allocateDirect(4);
            inputFeature1.loadBuffer(byteBuffer2);
            byteBuffer2.order(ByteOrder.nativeOrder());
            byteBuffer2.putFloat((float) 0.5);


            // Runs model inference and gets result.
            Yolov2.Outputs outputs = model.process(inputFeature0, inputFeature1, inputFeature1);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();
            float[] noOfDetection = outputFeature0.getFloatArray();
            int totalObjects = (int) noOfDetection[0];
            tvNoOfObjectDetected.setText("No of Objects Detected : " + String.valueOf(noOfDetection[0]));

            TensorBuffer outputFeature1 = outputs.getOutputFeature1AsTensorBuffer();
            float[] probability = outputFeature1.getFloatArray();
            tvPriority.setText("Probability : ");
            for (int i = 0; i < totalObjects; i++) {
                tvPriority.append(probability[i] * 100 + "%  , ");
            }

            TensorBuffer outputFeature2 = outputs.getOutputFeature2AsTensorBuffer();
            float[] coordinates = outputFeature2.getFloatArray();
            double[] x_min = new double[0], y_min = new double[0], width = new double[0], height = new double[0];

            // Code to draw bounding boxes  ----------------------------------------------------------------------------------------------
            Canvas canvas = new Canvas(image);
            Paint paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(10);

            for (int k = 0; k < totalObjects * 4; k += 4) {
                float leftx = (float) Math.ceil(coordinates[k]);
                float topy = (float) Math.ceil(coordinates[k + 1]);
                float rightx = (float) Math.ceil(coordinates[k + 2]) + (float) Math.ceil(coordinates[k]);
                float bottomy = (float) Math.ceil(coordinates[k + 1]) + (float) Math.ceil(coordinates[k + 3]);
                canvas.drawRect(leftx, topy, rightx, bottomy, paint);
            }
            imageView.setImageBitmap(image);

            //-------------------------------------------------------------------------------------------------------------------------------------


            Log.e(TAG, "classifyImage: " + x_min + "  " + y_min + "  " + width + "  " + height);

            TensorBuffer outputFeature3 = outputs.getOutputFeature3AsTensorBuffer();
            float[] index = outputFeature3.getFloatArray();
            int i = (int) index[0];

            String[] class_names = new String[]{"aeroplane", "bicycle", "bird", "boat", "bottle", "bus", "car", "cat", "chair", "cow",
                    "dining table", "dog", "horse", "motorbike", "person", "potted plant", "sheep", "sofa", "train", "tv monitor"};

            tvClassName.setText("Class Name : ");
            for (int j = 0; j < totalObjects; j++) {
                tvClassName.append(class_names[(int) index[j]].toUpperCase() + "  , ");
            }
            tvClassName.setTextColor(getColor(R.color.purple_500));

//            Canvas canvas = new Canvas(image);
//
//            Paint paint = new Paint();
//            paint.setColor(Color.BLACK);
//            paint.setStyle(Paint.Style.STROKE);
//            paint.setStrokeWidth(10);
//            float leftx = (float) x_min;
//            float topy = (float) y_min;
//            float rightx = (float) (x_min + width);
//            float bottomy = (float) (y_min + height);
//            canvas.drawRect(leftx, topy, rightx, bottomy, paint);

            imageView.setImageBitmap(image);


            Log.e(TAG, "classifyImage: ");
            // Releases model resources if no longer used.
            model.close();
        } catch (IOException e) {
            // TODO Handle the exception
            e.printStackTrace();
        }
    }
}