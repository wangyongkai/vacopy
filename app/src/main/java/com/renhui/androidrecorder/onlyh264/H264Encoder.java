package com.renhui.androidrecorder.onlyh264;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Environment;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * H264 编码类
 */
public class H264Encoder {

    private final static int TIMEOUT_USEC = 12000;

    private MediaCodec mediaCodec;

    public boolean isRuning = false;
    private int width, height, framerate;
    public byte[] configbyte;

    private BufferedOutputStream outputStream;

    public ArrayBlockingQueue<byte[]> yuv420Queue = new ArrayBlockingQueue<>(10);

    /***
     * 构造函数
     * @param width
     * @param height
     * @param framerate
     */
    public H264Encoder(int width, int height, int framerate) {
        this.width = width;
        this.height = height;
        this.framerate = framerate;

        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", width, height);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, width * height * 5);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        try {
            mediaCodec = MediaCodec.createEncoderByType("video/avc");
            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mediaCodec.start();
            createfile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createfile() {
        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/test.mp4";
        File file = new File(path);
        if (file.exists()) {
            file.delete();
        }
        try {
            outputStream = new BufferedOutputStream(new FileOutputStream(file));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void putData(byte[] buffer) {
        if (yuv420Queue.size() >= 10) {//如果不这样 add方法一旦超过容量 会报异常
            yuv420Queue.poll();
        }
        yuv420Queue.add(buffer);
    }

    /***
     * 开始编码
     */
    public void startEncoder() {
        new Thread(new Runnable() {

            @Override
            public void run() {
                isRuning = true;
                byte[] input = null;
                long pts = 0;
                long generateIndex = 0;

                while (isRuning) {
                    if (yuv420Queue.size() > 0) {
                        input = yuv420Queue.poll();
                        byte[] yuv420sp = new byte[width * height * 3 / 2];
                        // 必须要转格式，否则录制的内容播放出来为绿屏
                        NV21ToNV12(input, yuv420sp, width, height);
                        input = yuv420sp;
                    }
                    if (input != null) {
                        try {
                            //输入输出缓冲器
                            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
                            ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();

                            //首先，这一对函数的应用场合是对输入的数据流进行编码或者解码处理的时候，
                            //你会通过各种方法获得一个ByteBuffer的数组，这些数据就是准备处理的数据。
                            //你要通过自己的方法找到你要处理的部分，然后调用dequeueInputBuffer方法
                            //提取出要处理的部分（也就是一个ByteBuffer数据流），把这一部分放到缓存区。

                            //要处理数据的部分的索引
                            int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);

                            //INFO_TRY_AGAIN_LATER=-1 等待超时
                            //INFO_OUTPUT_FORMAT_CHANGED=-2 媒体格式更改
                            //INFO_OUTPUT_BUFFERS_CHANGED=-3 缓冲区已更改（过时）
                            //大于等于0的为缓冲区数据下标
                            if (inputBufferIndex >= 0) {

                                //根据帧数生成时间戳
                                pts = computePresentationTime(generateIndex);

                                //找到要处理的数据ByteBuffer 意思是找到要把要解析的数据放到这个ByteBuffer
                                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                                inputBuffer.clear();

                                //放入要解析的数据
                                inputBuffer.put(input);

                                //告诉编码器数据已经放入指定的ByteBuffer    找到--放入--告诉codec放入
                                mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, System.currentTimeMillis(), 0);
                                generateIndex += 1;
                            }

                            //存放输出buffer信息的对象 主要包括输出buffer的offset size
                            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

                            //要输出数据的部分的索引
                            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
                            while (outputBufferIndex >= 0) {
                                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];

                                //构造跟输出buffer的长度一致的数组
                                byte[] outData = new byte[bufferInfo.size];
                                //把输入的数据写入outData
                                outputBuffer.get(outData);
                                if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                                    //不是media数据    /**
                                    //     * This indicated that the buffer marked as such contains codec
                                    //     * initialization / codec specific data instead of media data.
                                    //     */
                                    configbyte = new byte[bufferInfo.size];
                                    configbyte = outData;
                                } else if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_SYNC_FRAME) {
                                    //关键帧
                                    byte[] keyframe = new byte[bufferInfo.size + configbyte.length];
                                    //把不是media的configbyte复制到关键帧的开始
                                    System.arraycopy(configbyte, 0, keyframe, 0, configbyte.length);

                                    //把关键帧数据复制到configbyte后面
                                    System.arraycopy(outData, 0, keyframe, configbyte.length, outData.length);
                                    outputStream.write(keyframe, 0, keyframe.length);
                                } else {
                                    outputStream.write(outData, 0, outData.length);
                                }

                                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
                            }

                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    } else {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                // 停止编解码器并释放资源
                try {
                    mediaCodec.stop();
                    mediaCodec.release();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // 关闭数据流
                try {
                    outputStream.flush();
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * 停止编码数据
     */
    public void stopEncoder() {
        isRuning = false;
    }

    private void NV21ToNV12(byte[] nv21, byte[] nv12, int width, int height) {
        if (nv21 == null || nv12 == null) return;
        int framesize = width * height;
        int i = 0, j = 0;
        System.arraycopy(nv21, 0, nv12, 0, framesize);
        for (i = 0; i < framesize; i++) {
            nv12[i] = nv21[i];
        }
        for (j = 0; j < framesize / 2; j += 2) {
            nv12[framesize + j - 1] = nv21[j + framesize];
        }
        for (j = 0; j < framesize / 2; j += 2) {
            nv12[framesize + j] = nv21[j + framesize - 1];
        }
    }

    /**
     * 根据帧数生成时间戳
     */
    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / framerate;
    }
}
