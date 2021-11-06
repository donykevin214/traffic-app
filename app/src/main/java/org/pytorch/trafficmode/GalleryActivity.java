package org.pytorch.trafficmode;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;
import org.pytorch.MemoryFormat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class GalleryActivity extends AppCompatActivity {
    // Added
    private static int RESULT_LOAD_IMAGE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        // Ask for permission of gallery upon the first launch of application
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }

        ImageView imageView = findViewById(R.id.image);
        Button buttonClass = findViewById(R.id.classify);
        buttonClass.setVisibility(View.INVISIBLE);

        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            TextView textView = findViewById(R.id.text);
            textView.setText("");
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, RESULT_LOAD_IMAGE);
            }
        });

        buttonClass.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            Bitmap bitmap = null;
            Module module = null;

            // Get the image from the image view
            ImageView imageView = findViewById(R.id.image);
            imageView.setClipToOutline(true);
            try {
              // Read the image as Bitmap
              bitmap = ((BitmapDrawable)imageView.getDrawable()).getBitmap();
              // Reshape the image into 400*400
              bitmap = Bitmap.createScaledBitmap(bitmap, 400, 400, true);
              // Load the model file
              module = LiteModuleLoader.load(assetFilePath(GalleryActivity.this, "model.ptl"));
            } catch (IOException e) {
              Log.e("PytorchTrafficMode", "Error reading assets!", e);
              finish();
              overridePendingTransition(0, 0);
              startActivity(getIntent());
              overridePendingTransition(0, 0);
            }

            // Prepare the input tensor
            final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(bitmap,
                    TensorImageUtils.TORCHVISION_NORM_MEAN_RGB, TensorImageUtils.TORCHVISION_NORM_STD_RGB, MemoryFormat.CHANNELS_LAST);

            // Run the model
              assert module != null;
              final Tensor outputTensor = module.forward(IValue.from(inputTensor)).toTensor();

            // Get the tensor content as java array of floats
            final float[] scores = outputTensor.getDataAsFloatArray();

            // Search for the index with the maximum score
            float maxScore = -Float.MAX_VALUE;
            int maxScoreIdx = -1;
            for (int i = 0; i < scores.length; i++) {
              if (scores[i] > maxScore) {
                maxScore = scores[i];
                maxScoreIdx = i;
              }
            }

            String className = ModelClasses.MODEL_CLASSES[maxScoreIdx];

            // Show the text related to the output class name
            TextView textView = findViewById(R.id.text);
            textView.setText(className);
          }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && null != data) {
            // Get the uri of the picked image
            Uri selectedImage = data.getData();
            String picturePath = createCopyAndReturnRealPath(GalleryActivity.this, selectedImage);
            ImageView imageView = (ImageView) findViewById(R.id.image);
            imageView.setImageBitmap(BitmapFactory.decodeFile(picturePath));

            // Sey the URI to read the Bitmap from the image
            imageView.setImageURI(null);
            imageView.setImageURI(selectedImage);
            Button buttonClass = findViewById(R.id.classify);
            buttonClass.setVisibility(View.VISIBLE);
        } else {
            finish();
            overridePendingTransition(0, 0);
            startActivity(getIntent());
            overridePendingTransition(0, 0);
        }
    }

    public static String createCopyAndReturnRealPath(
            @NonNull Context context, @NonNull Uri uri) {
        final ContentResolver contentResolver = context.getContentResolver();
        if (contentResolver == null)
            return null;

        // Create the file path inside app's data dir
        String filePath = context.getApplicationInfo().dataDir + File.separator
                + System.currentTimeMillis();

        File file = new File(filePath);
        try {
            InputStream inputStream = contentResolver.openInputStream(uri);
            if (inputStream == null)
                return null;

            OutputStream outputStream = new FileOutputStream(file);
            byte[] buf = new byte[1024];
            int len;
            while ((len = inputStream.read(buf)) > 0)
                outputStream.write(buf, 0, len);

            outputStream.close();
            inputStream.close();
        } catch (IOException ignore) {
            return null;
        }

        return file.getAbsolutePath();
    }

    // Copy specified asset to the file in /files app directory and returns this file absolute path
    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }
}
