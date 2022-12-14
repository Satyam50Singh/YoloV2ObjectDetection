package satya.app.objectdetectionyolov2;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import satya.app.objectdetectionyolov2.ml.Yolov2;
import satya.app.objectdetectionyolov2.utils.Utils;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ID_MULTIPLE_PERMISSIONS = 100101;
    int imageSize = 416;

    // UI Controls
    ImageView imageView, ivSaveOutputImage;
    TextView tvNoOfObjectDetected, tvPriority, tvClassName, tvPValue, tvNmsValue;
    RelativeLayout rlProgressBar;
    AppCompatButton btnCaptureImage;
    SeekBar sbPThreshold, sbNmsThreshold;
    private static final String TAG = "MainActivity";
    float pThreshold = 0.5f, nmsThreshold = 0.5f;
    Uri imageCapturedUri;

    // You can do the assignment inside onAttach or onCreate, i.e, before the activity is displayed
    ActivityResultLauncher<Intent> cameraActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // There are no request codes
                        classifyImage(imageCapturedUri);
                    }
                }
            });

    ActivityResultLauncher<String> galleryActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            result -> {
                // There are no request codes
                classifyImage(result);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initUI();

        setListeners();
    }

    // method to initialise controls
    private void initUI() {
        imageView = findViewById(R.id.imageView);
        tvNoOfObjectDetected = findViewById(R.id.tvNoOfObjectDetected);
        tvPriority = findViewById(R.id.tvPriority);
        tvClassName = findViewById(R.id.tvClassName);
        rlProgressBar = findViewById(R.id.common_progress_bar);
        btnCaptureImage = findViewById(R.id.btn_capture_image);
        sbPThreshold = findViewById(R.id.sb_p_threshold);
        sbNmsThreshold = findViewById(R.id.sb_nms_threshold);
        tvPValue = findViewById(R.id.tv_p_value);
        tvNmsValue = findViewById(R.id.tv_nms_value);
        ivSaveOutputImage = findViewById(R.id.iv_save_output_image);
        ivSaveOutputImage.setVisibility(View.GONE);
    }

    private void setListeners() {
        sbPThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int val, boolean b) {
                pThreshold = (float) val / 100;
                Log.e(TAG, "sbPThreshold: " + val + "  " + pThreshold);
                tvPValue.setText(String.valueOf(pThreshold));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        sbNmsThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int val, boolean b) {
                nmsThreshold = (float) val / 100;
                Log.e(TAG, "sbNmsThreshold: " + val + "  " + nmsThreshold);
                tvNmsValue.setText(String.valueOf(nmsThreshold));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        btnCaptureImage.setOnClickListener(view -> {
            try {
                if (checkAndRequestPermissions()) {
                    // if camera and gallery permissions granted
                    chooseImage();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        ivSaveOutputImage.setOnClickListener(view -> downloadImageInGallery());
    }

    // check camera and gallery permissions.
    private boolean checkAndRequestPermissions() {
        try {
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    // Handled permission Result
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        try {
            if (requestCode == REQUEST_ID_MULTIPLE_PERMISSIONS) {
                if (ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), "FlagUp Requires Access to Camara.", Toast.LENGTH_SHORT).show();
                    checkAndRequestPermissions();
                } else if (ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), "FlagUp Requires Access to Your Storage.", Toast.LENGTH_SHORT).show();
                    checkAndRequestPermissions();
                } else {
                    // if camera and gallery permissions granted
                    chooseImage();
                }
            } else {
                throw new IllegalStateException("Unexpected value: " + requestCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // method to show alert dialog for different options
    private void chooseImage() {
        try {
            final CharSequence[] optionsMenu = {getString(R.string.take_photo), getString(R.string.choose_from_gallery), getString(R.string.exit)};
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setItems(optionsMenu, (dialogInterface, i) -> {
                if (optionsMenu[i].equals(getString(R.string.take_photo))) {
                    // Open the camera and get the photo
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.TITLE, "Object " + new Date().getTime());
                    values.put(MediaStore.Images.Media.DESCRIPTION, "From Camera");
                    imageCapturedUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

                    Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageCapturedUri);

                    cameraActivityResultLauncher.launch(cameraIntent);
                } else if (optionsMenu[i].equals(getString(R.string.choose_from_gallery))) {
                    // choose from  external storage
                    galleryActivityResultLauncher.launch("image/*");
                } else if (optionsMenu[i].equals(getString(R.string.exit))) {
                    dialogInterface.dismiss();
                }
            });
            builder.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // method to classify Image Objects
    private void classifyImage(Uri imageUri) {
        if (imageUri != null) {
            // converting uri to bitmap
            Bitmap image = Utils.getBitmapFromUri(imageUri, MainActivity.this);
            int dimension = Math.min(image.getWidth(), image.getHeight());
            image = ThumbnailUtils.extractThumbnail(image, dimension, dimension);

            image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false);

            rlProgressBar.setVisibility(View.VISIBLE);

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

                // p - threshold :: if confidence value is greater than p-threshold then only show the bounding boxes.
                TensorBuffer inputFeature1 = TensorBuffer.createFixedSize(new int[]{1, 1}, DataType.FLOAT32);
                ByteBuffer byteBuffer1 = ByteBuffer.allocateDirect(4);
                inputFeature1.loadBuffer(byteBuffer1);
                byteBuffer1.order(ByteOrder.nativeOrder());
                byteBuffer1.putFloat(pThreshold);

                // nms - threshold :: this remove the overlapped bounding boxes.
                TensorBuffer inputFeature2 = TensorBuffer.createFixedSize(new int[]{1, 1}, DataType.FLOAT32);
                ByteBuffer byteBuffer2 = ByteBuffer.allocateDirect(4);
                inputFeature2.loadBuffer(byteBuffer2);
                byteBuffer2.order(ByteOrder.nativeOrder());
                byteBuffer2.putFloat(nmsThreshold);

                // Runs model inference and gets result.
                Yolov2.Outputs outputs = model.process(inputFeature0, inputFeature2, inputFeature1);

                TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();
                float[] noOfDetection = outputFeature0.getFloatArray();
                int totalObjects = (int) noOfDetection[0];
                tvNoOfObjectDetected.setText(getString(R.string.no_of_object_detected) + noOfDetection[0]);

                TensorBuffer outputFeature1 = outputs.getOutputFeature1AsTensorBuffer();
                float[] probability = outputFeature1.getFloatArray();
                tvPriority.setText(R.string.probability);
                for (int i = 0; i < totalObjects; i++) {
                    tvPriority.append(probability[i] * 100 + "%, ");
                }

                TensorBuffer outputFeature2 = outputs.getOutputFeature2AsTensorBuffer();
                float[] coordinates = outputFeature2.getFloatArray();

                TensorBuffer outputFeature3 = outputs.getOutputFeature3AsTensorBuffer();
                float[] classIndexes = outputFeature3.getFloatArray();
                int i = (int) classIndexes[0];

                String[] class_names = new String[]{"aeroplane", "bicycle", "bird", "boat", "bottle", "bus", "car", "cat", "chair", "cow",
                        "dining table", "dog", "horse", "motorbike", "person", "potted plant", "sheep", "sofa", "train", "tv monitor"};

                tvClassName.setText(R.string.class_name);
                for (int j = 0; j < totalObjects; j++) {
                    tvClassName.append(class_names[(int) classIndexes[j]].toUpperCase() + ", ");
                }

                // Code to draw bounding boxes  ----------------------------------------------------------------------------------------------
                Canvas canvas = new Canvas(image);
                Paint paint = new Paint();
                paint.setColor(Color.BLACK);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(6);

                Paint textStyle = new Paint(Paint.ANTI_ALIAS_FLAG);
                textStyle.setColor(Color.BLACK);
                Resources resources = getResources();
                float scale = resources.getDisplayMetrics().density;
                textStyle.setTextSize((int) (15 * scale));
                textStyle.setColor(getColor(R.color.black));

                int indexVal = 0;
                for (int k = 0; k < totalObjects * 4; k += 4) {
                    float leftx = (float) Math.ceil(coordinates[k]);
                    float topy = (float) Math.ceil(coordinates[k + 1]);
                    float rightx = (float) Math.ceil(coordinates[k + 2]) + (float) Math.ceil(coordinates[k]);
                    float bottomy = (float) Math.ceil(coordinates[k + 1]) + (float) Math.ceil(coordinates[k + 3]);
                    canvas.drawRect(leftx, topy, rightx, bottomy, paint);
                    canvas.drawText(class_names[(int) classIndexes[indexVal++]].toUpperCase(), leftx, topy, textStyle);
                }
                if (image != null) {
                    imageView.setImageBitmap(image);
                    ivSaveOutputImage.setVisibility(View.VISIBLE);
                }

                //-------------------------------------------------------------------------------------------------------------------------------------


                // Releases model resources if no longer used.
                model.close();
                rlProgressBar.setVisibility(View.GONE);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void downloadImageInGallery() {
        rlProgressBar.setVisibility(View.VISIBLE);
        Bitmap bitmap = ((BitmapDrawable) imageView.getDrawable()).getBitmap();

        FileOutputStream fileOutputStream = null;
        File file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File dir = new File(file + "/objectDetectionOutputs");
        if (!dir.exists())
            dir.mkdirs();
        String filename = String.format("Output %d.png", System.currentTimeMillis());
        File outFile = new File(dir, filename);
        try {
            fileOutputStream = new FileOutputStream(outFile);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (bitmap != null) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream);
        }
        try {
            fileOutputStream.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            fileOutputStream.close();
            rlProgressBar.setVisibility(View.GONE);
            Toast.makeText(this, R.string.download_successfully, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


}