package com.example.maat;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.os.Bundle;
import android.service.autofill.ImageTransformation;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import org.tensorflow.lite.Interpreter;

import androidx.appcompat.app.AppCompatActivity;

import com.example.maat.ui.signDetector;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class MainActivity extends AppCompatActivity {

    private Camera mCamera;
    private CameraPreview mPreview;
    private Camera.PictureCallback mPicture;
    private Button capture, switchCamera;
    private Context myContext;
    private LinearLayout cameraPreview;
    private boolean cameraFront = false;
    private signDetector signClassifier = null;


    public static Bitmap bitmap;
    private static Matrix rot_mat;


//    public static float[] modelInp;



    private boolean safeToTakePicture = false;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Rotation Matrix (90 degrees) so to rotate the bitmap after an image is taken.
        rot_mat = new Matrix();
        rot_mat.postRotate(90);


        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        myContext = this;

        mCamera =  Camera.open();
        mCamera.setDisplayOrientation(90);
        cameraPreview = findViewById(R.id.cPreview);
        mPreview = new CameraPreview(myContext, mCamera);
        cameraPreview.addView(mPreview);
        mPicture = getPictureCallback(); // ??

        capture = findViewById(R.id.btnCam);
        capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (safeToTakePicture) {
                    mCamera.takePicture(null, null, mPicture);
                    safeToTakePicture = false;
                }
            }
        });

        switchCamera = findViewById(R.id.btnSwitch);
        switchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //get the number of cameras
                int camerasNumber = Camera.getNumberOfCameras();
                if (camerasNumber > 1) {
                    //release the old camera instance
                    //switch camera, from the front and the back and vice versa

                    releaseCamera();
                    chooseCamera();
                } else {

                }
            }
        });

        mCamera.startPreview();
        safeToTakePicture = true;


    }

    private int findFrontFacingCamera() {

        int cameraId = -1;
        // Search for the front facing camera
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                cameraId = i;
                cameraFront = true;
                break;
            }
        }
        return cameraId;

    }

    private int findBackFacingCamera() {
        int cameraId = -1;
        //Search for the back facing camera
        //get the number of cameras
        int numberOfCameras = Camera.getNumberOfCameras();
        //for every camera check
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                cameraId = i;
                cameraFront = false;
                break;

            }

        }
        return cameraId;
    }

    public void onResume() {

        super.onResume();
        if(mCamera == null) {
            mCamera = Camera.open();
            mCamera.setDisplayOrientation(90);
            mPicture = getPictureCallback();
            mPreview.refreshCamera(mCamera);
            Log.d("nu", "null");
        }else {
            Log.d("nu","no null");
        }

    }

    public void chooseCamera() {
        //if the camera preview is the front
        if (cameraFront) {
            int cameraId = findBackFacingCamera();
            if (cameraId >= 0) {
                //open the backFacingCamera
                //set a picture callback
                //refresh the preview

                mCamera = Camera.open(cameraId);
                mCamera.setDisplayOrientation(90);
                mPicture = getPictureCallback();
                mPreview.refreshCamera(mCamera);
            }
        } else {
            int cameraId = findFrontFacingCamera();
            if (cameraId >= 0) {
                //open the backFacingCamera
                //set a picture callback
                //refresh the preview
                mCamera = Camera.open(cameraId);
                mCamera.setDisplayOrientation(90);
                mPicture = getPictureCallback();
                mPreview.refreshCamera(mCamera);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        //when on Pause, release camera in order to be used from other applications
        releaseCamera();
    }

    private void releaseCamera() {
        // stop and release camera
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }
    }

    private Camera.PictureCallback getPictureCallback() {
        Camera.PictureCallback picture = new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                //Rotate bitmap so it's properly oriented
                int crop_x, crop_y, crop_w, crop_h;
                int w = bitmap.getWidth();
                int h = bitmap.getHeight();
                if (w<h){
                    crop_h = crop_w = w;
                    crop_x = (int) ((h-w)/2.0);
                    crop_y = 0;
                }
                else{
                    crop_h = crop_w = h;
                    crop_x = 0;
                    crop_y = (int) ((w-h)/2.0);
                }

                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), rot_mat, false);
                // Crop bitmap
                bitmap = Bitmap.createBitmap(bitmap, crop_x, crop_y, crop_w, crop_h);
                // Resize bitmap to 244 by 244
                bitmap = Bitmap.createScaledBitmap(bitmap, 244, 244, false);

                Intent intent = new Intent(MainActivity.this,PictureActivity.class);
                startActivity(intent);


                String guess = signClassifier.classify(bitmap);


                Log.d("butts",  "Found Image = " + guess);


                safeToTakePicture = true;
            }
        };

        // Crop bitmap
//        bitmap = Bitmap.createBitmap(bitmap, )

        return picture;
    }
}