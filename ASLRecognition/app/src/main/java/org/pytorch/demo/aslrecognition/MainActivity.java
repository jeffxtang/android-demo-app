package org.pytorch.demo.aslrecognition;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.FloatBuffer;

public class MainActivity extends AppCompatActivity implements Runnable {
    private ImageView mImageView;
    private Button mButtonRecognize;
    private ProgressBar mProgressBar;
    private Bitmap mBitmap = null;
    private Module mModule = null;
    private String mImagename = "A1.jpg";

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            mBitmap = BitmapFactory.decodeStream(getAssets().open(mImagename));
        } catch (IOException e) {
            Log.e("ASLRecognition", "Error reading assets", e);
            finish();
        }

        mImageView = findViewById(R.id.imageView);
        mImageView.setImageBitmap(mBitmap);

        final Button buttonRestart = findViewById(R.id.restartButton);
        buttonRestart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mImagename == "A1.jpg")
                    mImagename = "B1.jpg";
                else
                    mImagename = "A1.jpg";
                try {
                    mBitmap = BitmapFactory.decodeStream(getAssets().open(mImagename));
                    mImageView.setImageBitmap(mBitmap);
                } catch (IOException e) {
                    Log.e("ASLRecognition", "Error reading assets", e);
                    finish();
                }
            }
        });


        mButtonRecognize = findViewById(R.id.segmentButton);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mButtonRecognize.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mButtonRecognize.setEnabled(false);
                mProgressBar.setVisibility(ProgressBar.VISIBLE);
                mButtonRecognize.setText(getString(R.string.run_model));

                Thread thread = new Thread(MainActivity.this);
                thread.start();
            }
        });

        try {
            mModule = LiteModuleLoader.load(MainActivity.assetFilePath(getApplicationContext(), "asl.ptl"));
        } catch (IOException e) {
            Log.e("ASLRecognition", "Error reading assets", e);
            finish();
        }
    }

    @Override
    public void run() {
        FloatBuffer inTensorBuffer = Tensor.allocateFloatBuffer(3*200*200);
        for (int x = 0; x < 200; x++) {
            for (int y = 0; y < 200; y++) {
                int colour = mBitmap.getPixel(x, y);

                int red = Color.red(colour);
                int blue = Color.blue(colour);
                int green = Color.green(colour);
                inTensorBuffer.put(x + 200*y, (float) blue);

                inTensorBuffer.put(200*200 + x + 200*y, (float) green);

                inTensorBuffer.put(2*200*200 + x + 200*y, (float) red);
            }
        }

        Tensor inputTensor = Tensor.fromBlob(inTensorBuffer, new long[]{1, 3, 200, 200});
        final float[] inputs = inputTensor.getDataAsFloatArray();


        final long startTime = SystemClock.elapsedRealtime();
        Tensor outTensor = mModule.forward(IValue.from(inputTensor)).toTensor();
        final long inferenceTime = SystemClock.elapsedRealtime() - startTime;
        Log.d("ASLRecognition",  "inference time (ms): " + inferenceTime);

        final float[] scores = outTensor.getDataAsFloatArray();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mButtonRecognize.setEnabled(true);
                mButtonRecognize.setText(getString(R.string.recognize));
                mProgressBar.setVisibility(ProgressBar.INVISIBLE);

            }
        });
    }
}
