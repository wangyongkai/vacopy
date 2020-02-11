package com.renhui.androidrecorder.onlyh264;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.renhui.androidrecorder.muxer.MediaMuxerActivity;
import com.renhui.androidrecorder.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    Camera camera;
    SurfaceView surfaceView;
    SurfaceHolder surfaceHolder;
    Button muxerButton;

    int width = 1280;
    int height = 720;
    int framerate = 30;
    H264Encoder encoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "申请权限", Toast.LENGTH_SHORT).show();
            // 申请 相机 麦克风权限
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
        }

        surfaceView = (SurfaceView) findViewById(R.id.surface_view);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        muxerButton = (Button) findViewById(R.id.go_muxer);
        muxerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, MediaMuxerActivity.class);
                startActivity(intent);
                finish();
            }
        });

        if (supportH264Codec()) {
            Log.e("MainActivity", "support H264 hard codec");
        } else {
            Log.e("MainActivity", "not support H264 hard codec");
        }
    }

    private boolean supportH264Codec() {
        // 遍历支持的编码格式信息
        if (Build.VERSION.SDK_INT >= 18) {
            for (int j = MediaCodecList.getCodecCount() - 1; j >= 0; j--) {

                MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(j);

                String[] types = codecInfo.getSupportedTypes();
                Log.e("MainActivity", "codecInfo.getName()=" + codecInfo.getName() + " types=" + Arrays.toString(types));
                for (int i = 0; i < types.length; i++) {
                    if (types[i].equalsIgnoreCase("video/avc")) {

                        //找到能硬编码h264的MediaCodecInfo对象
                        //下面是根据MediaCodecInfo找到合适的COLOR_FORMAT

                        //疑问：找到颜色格式，是要在如下地方使用吗  设置正确的MediaFormat.KEY_COLOR_FORMAT
                       // MediaFormat videoFormat = MediaFormat.createVideoFormat(VCODEC, vOutWidth, vOutHeight);
                       // videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);


//                        MediaFormat.KEY_COLOR_FORMAT配置属性，该属性用于指明video编码器的颜色格式，
//                        具体选择哪种颜色格式与输入的视频数据源颜色格式有关。比如，我们都知道Camera预览
//                    采集的图像流通常为NV21或YV12，那么编码器需要指定相应的颜色格式，
//                        否则编码得到的数据可能会出现花屏、叠影、颜色失真等现象。
//                        MediaCodecInfo.CodecCapabilities.存储了编码器所有支持的颜色格式，常见颜色格式映射如下：
////                        原始数据 编码器
//                        NV12(YUV420sp) ———> COLOR_FormatYUV420PackedSemiPlanar
//                        NV21 ———-> COLOR_FormatYUV420SemiPlanar
//                        YV12(I420) ———-> COLOR_FormatYUV420Planar




                        //int matchedColorFormat = 0;
                        //        MediaCodecInfo.CodecCapabilities cc = vmci.getCapabilitiesForType(VCODEC);
                        //        for (int i = 0; i < cc.colorFormats.length; i++) {
                        //            int cf = cc.colorFormats[i];
                        //            Log.i(TAG, String.format("vencoder %s supports color fomart 0x%x(%d)", vmci.getName(), cf, cf));
                        //
                        //            // choose YUV for h.264, prefer the bigger one.
                        //            // corresponding to the color space transform in onPreviewFrame
                        //            if (cf >= cc.COLOR_FormatYUV420Planar && cf <= cc.COLOR_FormatYUV420SemiPlanar) {
                        //                if (cf > matchedColorFormat) {
                        //                    matchedColorFormat = cf;
                        //                }
                        //            }
                        //        }

                        return true;
                    }
                }
            }
        }
        return false;
    }


    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.w("MainActivity", "enter surfaceCreated method");
        // 目前设定的是，当surface创建后，就打开摄像头开始预览
        camera = Camera.open();
        camera.setDisplayOrientation(90);
        Camera.Parameters parameters = camera.getParameters();
        parameters.setPreviewFormat(ImageFormat.NV21);
        parameters.setPreviewSize(1280, 720);

        //得到设置的预览图片格式
        parameters.getPreviewFormat();


//        case ImageFormat.NV16:      return PIXEL_FORMAT_YUV422SP;
//        case ImageFormat.NV21:      return PIXEL_FORMAT_YUV420SP;
//        case ImageFormat.YUY2:      return PIXEL_FORMAT_YUV422I;
//        case ImageFormat.YV12:      return PIXEL_FORMAT_YUV420P;
//        case ImageFormat.RGB_565:   return PIXEL_FORMAT_RGB565;
//        case ImageFormat.JPEG:      return PIXEL_FORMAT_JPEG;

//查找相机支持的分辨率
        List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();

//        <p>Gets the supported video frame sizes that can be used by
//         * MediaRecorder.</p>
        List<Camera.Size> videoSizes = parameters.getSupportedVideoSizes();



        List<String> list1 = new ArrayList<>();
        List<String> list2 = new ArrayList<>();
        for (int i = 0; i < previewSizes.size(); i++) {
            Pair pair = new Pair(previewSizes.get(i).width, previewSizes.get(i).height);
            list1.add(pair.toString());
        }

        for (int i = 0; i < videoSizes.size(); i++) {
            Pair pair = new Pair(videoSizes.get(i).width, videoSizes.get(i).height);
            list2.add(pair.toString());
        }

        Log.e("MainActivity", "getSupportedPreviewSizes--------" + list1.toString());
        Log.e("MainActivity", "getSupportedVideoSizes--------" + list2.toString());


        try {
            camera.setParameters(parameters);
            camera.setPreviewDisplay(surfaceHolder);
            camera.setPreviewCallback(this);
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }

        encoder = new H264Encoder(width, height, framerate);
        encoder.startEncoder();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        Log.w("MainActivity", "enter surfaceChanged method");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        Log.w("MainActivity", "enter surfaceDestroyed method");

        // 停止预览并释放资源
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera = null;
        }

        if (encoder != null) {
            encoder.stopEncoder();
        }
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        if (encoder != null) {
            encoder.putData(bytes);
        }
    }
}
