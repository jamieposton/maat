package com.example.maat;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import com.example.maat.MainActivity;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class signDetector {

        private final String TAG = this.getClass().getSimpleName();
        private Interpreter tflite;
        private List<String> labelList;
        private ByteBuffer inputBuffer = null;
        private float[][] mnistOutput = null;
        private static final String MODEL_PATH = "converted_model2.lite";
        private static final String LABEL_PATH = "label_map.pbtxt";
        private static final int RESULTS_TO_SHOW = 5;
        private static final int NUMBER_LENGTH = 100;
        private static final int DIM_BATCH_SIZE = 1;
        private static final int DIM_IMG_SIZE_X = 244;
        private static final int DIM_IMG_SIZE_Y = 244;
        private static final int DIM_PIXEL_SIZE = 3;
        private static final int BYTE_SIZE_OF_FLOAT = 4;

        private PriorityQueue<Map.Entry<String, Float>> sortedLabels =
                new PriorityQueue<>(
                        RESULTS_TO_SHOW,
                        new Comparator<Map.Entry<String, Float>>() {
                            @Override
                            public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float> o2) {
                                return (o1.getValue()).compareTo(o2.getValue());
                            }
                        });


        public signDetector(Activity activity) {
            try {
                tflite = new Interpreter(loadModelFile(activity));
                labelList = loadLabelList(activity);
                inputBuffer =
                        ByteBuffer.allocateDirect(
                                BYTE_SIZE_OF_FLOAT * DIM_BATCH_SIZE * DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE);
                inputBuffer.order(ByteOrder.nativeOrder());
                mnistOutput = new float[DIM_BATCH_SIZE][NUMBER_LENGTH];
                Log.d(TAG, "Created a Tensorflow Lite MNIST Classifier.");
            } catch (IOException e) {
                Log.e(TAG, "IOException loading the tflite file");
            }
        }

        protected void runInference() {
            tflite.run(inputBuffer, mnistOutput);
        }

        public String classify(Bitmap bitmap) {
            if (tflite == null) {
                Log.e(TAG, "Image classifier has not been initialized; Skipped.");
            }
            preprocess(bitmap);
            runInference();
            return printTopKLabels();
        }

        private String printTopKLabels() {
            for (int i = 0; i < labelList.size(); ++i) {
                sortedLabels.add(
                        new AbstractMap.SimpleEntry<>(labelList.get(i), mnistOutput[0][i]));
                if (sortedLabels.size() > RESULTS_TO_SHOW) {
                    sortedLabels.poll();
                }
            }
            String textToShow = "";
            final int size = sortedLabels.size();
            for (int i = 0; i < size; ++i) {
                Map.Entry<String, Float> label = sortedLabels.poll();
                textToShow = label.getKey();
            }
            return textToShow;
        }

        /** Reads label list from Assets. */
        private List<String> loadLabelList(Activity activity) throws IOException {
            List<String> labelList = new ArrayList<String>();
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(activity.getAssets().open(LABEL_PATH)));
            String line;
            while ((line = reader.readLine()) != null) {
                labelList.add(line);
            }
            reader.close();
            return labelList;
        }

        private MappedByteBuffer loadModelFile(Activity activity) throws IOException {
            String tag = "loading???????????";
            Log.d(tag, "beforeasset");
            final AssetManager assets = activity.getAssets();
            final String[] names = assets.list( "" );
            AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_PATH);
            Log.d(tag, "afterasset");

            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        }

        private void preprocess(Bitmap bitmap) {
            if (bitmap == null || inputBuffer == null) {
                return;
            }
            // Reset the image data
            inputBuffer.rewind();

            int width = bitmap.getWidth();
            int height = bitmap.getHeight();

            long startTime = SystemClock.uptimeMillis();

            // The bitmap shape should be 28 x 28
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            for (int i = 0; i < pixels.length; ++i) {
                // Set 0 for white and 255 for black pixels
                int pixel = pixels[i];
                // The color of the input is black so the blue channel will be 0xFF.
                int channel = pixel & 0xff;

                inputBuffer.putFloat(pixel & 0xFFFF0000); // R
                inputBuffer.putFloat(pixel & 0xFF00FF00); // G
                inputBuffer.putFloat(pixel & 0xFF0000FF); // B

            }
            long endTime = SystemClock.uptimeMillis();
            Log.d(TAG, "Time cost to put values into ByteBuffer: " + Long.toString(endTime - startTime));

    }
}
