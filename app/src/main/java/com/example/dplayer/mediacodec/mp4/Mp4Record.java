package com.example.dplayer.mediacodec.mp4;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceView;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import com.example.dplayer.MyApp;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.example.dplayer.mediacodec.mp4.AacEncoder.AAC_ENCODER;
import static com.example.dplayer.mediacodec.mp4.H264Encoder.H264_ENCODER;

public class Mp4Record implements H264VideoRecord.Callback, AacAudioRecord.Callback {
    private static int index = 0;
    private H264VideoRecord mH264VideoRecord;
    private AacAudioRecord mAacAudioRecord;
    private MediaMuxer mMediaMuxer;

    private boolean mHasStartMuxer;
    private boolean mHasStopAudio;
    private boolean mHasStopVideo;
    private int mVideoTrackIndex = -1;
    private int mAudioTrackIndex = -1;
    private final Object mLock;
    private BlockingQueue<AVData> mDataBlockingQueue;
    private volatile boolean mIsRecoding;
    private long mLastStartSeconds = -1;
    private long mLogSeconds = -1;
    private MediaFormat mVideoTrackFormat = null;
    private MediaFormat mAudioTrackFormat = null;
    private volatile boolean mIsSwitchMuxer = false;
    private Activity mContext;
    private String mSDcard;
    private boolean mIgnoreAudioTrack = false;
    public Mp4Record(Activity activity, SurfaceView surfaceView, int audioSource, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes, String path) {
        mH264VideoRecord = new H264VideoRecord(activity, surfaceView);
        mAacAudioRecord = new AacAudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes);
        mH264VideoRecord.setCallback(this);
        mAacAudioRecord.setCallback(this);
        mContext = activity;
        //mSDcard = getStoragePath(mContext,true);
        try {
            mMediaMuxer = new MediaMuxer(generateFilePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            //FileDescriptor fd = generateFileDescripter();
            //mMediaMuxer = new MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        } catch (IOException e) {
            e.printStackTrace();
        }
        mHasStartMuxer = false;
        mLock = new Object();
        mDataBlockingQueue = new LinkedBlockingQueue<>();


    }

    public void start() {
        mIsRecoding = true;
        mAacAudioRecord.start();
        mH264VideoRecord.start();
    }

    public void stop() {
        mAacAudioRecord.stop();
        mH264VideoRecord.stop();
        mIsRecoding = false;
    }


    @Override
    public void outputAudio(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
        if(!mIsSwitchMuxer) {
            if(mIgnoreAudioTrack == false)
                writeMediaData(mAudioTrackIndex, byteBuffer, bufferInfo);
        }
    }

    @Override
    public void outputMediaFormat(int type, MediaFormat mediaFormat) {
        checkMediaFormat(type, mediaFormat);
    }

    @Override
    public void outputVideo(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
        if(!mIsSwitchMuxer)
            writeMediaData(mVideoTrackIndex, byteBuffer, bufferInfo);
    }

    private void writeMediaData(int trackIndex, ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
        //Log.d("dataflow","add encoded data to queue");
        mDataBlockingQueue.add(new AVData(index++, trackIndex, byteBuffer, bufferInfo));
    }

    private void checkMediaFormat(int type, MediaFormat mediaFormat) {
        synchronized (mLock) {
            //Log.e("ethan","mLock synchronized type="+type);
            if (type == AAC_ENCODER) {
                if(mIgnoreAudioTrack) {
                    mAudioTrackIndex = 33;
                    mAudioTrackFormat = null;
                } else {
                    mAudioTrackIndex = mMediaMuxer.addTrack(mediaFormat);
                    mAudioTrackFormat = mediaFormat;
                }
            }
            if (type == H264_ENCODER) {
                mVideoTrackIndex = mMediaMuxer.addTrack(mediaFormat);
                mVideoTrackFormat = mediaFormat;
            }
            Log.e("ethan","mLock synchronized type="+type+",mAudioTrackIndex="+mAudioTrackIndex+",mVideoTrackIndex="+mVideoTrackIndex);
            startMediaMuxer();
        }
    }

    private void startMediaMuxer() {
        if (mHasStartMuxer) {
            return;
        }
        if (mAudioTrackIndex != -1 && mVideoTrackIndex != -1) {
            Log.e("ethan", "video track index:" + mVideoTrackIndex + "audio track index:" + mAudioTrackIndex);
            mMediaMuxer.start();
            mHasStartMuxer = true;
            mLastStartSeconds = System.currentTimeMillis()/1000;
            mLogSeconds = -1;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (mIsRecoding || !mDataBlockingQueue.isEmpty()) {
                        long currStartSenconds = System.currentTimeMillis()/1000;

                        if(currStartSenconds - mLastStartSeconds  >= 20) {
                            Log.d("ethan","laststart="+mLastStartSeconds+",curr="+currStartSenconds);
                            mIsSwitchMuxer = true;
                            mLastStartSeconds = System.currentTimeMillis()/1000;
                            switchMuxer();
                            mIsSwitchMuxer = false;
                        }
                        //Log.d("dataflow","poll encoded data from queue");
                        AVData avData = mDataBlockingQueue.poll();
                        if (avData == null) {
                            continue;
                        }
                        boolean keyFrame = (avData.bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
                        //Log.d("ethan","currStartSenconds%3="+currStartSenconds%3+",currStartSenconds="+currStartSenconds+",mLogSeconds="+mLogSeconds);
                        if(((currStartSenconds%3  == 0)&&(currStartSenconds != mLogSeconds)) || (keyFrame == true)) {
                            mLogSeconds = currStartSenconds;
                            Log.e("ethan", avData.index + "trackIndex:" + avData.trackIndex + ",writeSampleData:" + keyFrame);
                        }
                        //Log.d("dataflow","write encoded data to file");


                        mMediaMuxer.writeSampleData(avData.trackIndex, avData.byteBuffer, avData.bufferInfo);
                    }
                }
            }).start();
            Log.e("ethan","mLock notifyAll() begin");
            mLock.notifyAll();
            Log.e("ethan","mLock notifyAll() end");
        } else {
            try {
                Log.e("ethan","mLock wait() begin");
                mLock.wait();
                Log.e("ethan","mLock wait() end");
            } catch (InterruptedException e) {

            }
        }
    }

    @Override
    public void onStop(int type) {
        synchronized (mLock) {
            if (type == H264_ENCODER) {
                mHasStopVideo = true;
            }
            if (type == AAC_ENCODER) {
                mHasStopAudio = true;
            }
            if (mHasStopAudio && mHasStopVideo && mHasStartMuxer) {
                mHasStartMuxer = false;
                mMediaMuxer.stop();
            }
        }
    }

    private class AVData {
        int index = 0;
        int trackIndex;
        ByteBuffer byteBuffer;
        MediaCodec.BufferInfo bufferInfo;

        public AVData(int index, int trackIndex, ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
            this.index = index;
            this.trackIndex = trackIndex;
            this.byteBuffer = byteBuffer;
            this.bufferInfo = bufferInfo;
            boolean keyFrame = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
            //Log.e("ethan", index + "trackIndex:" + trackIndex + ",AVData:" + keyFrame);
        }
    }
    private FileDescriptor generateFileDescripter(){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        Date date = new Date(System.currentTimeMillis());
        String dateSting = sdf.format(date);
        FileDescriptor df;
        MyApp myApp = (MyApp)mContext.getApplication();
        Uri rootUri = myApp.getRootUri();
        rootUri = getTreeUri();
        DocumentFile documentDir = DocumentFile.fromTreeUri(mContext, rootUri);
        DocumentFile documentFile = documentDir.createFile("video/mp4",dateSting+".mp4");
        Uri getUri = documentFile.getUri();
        try {
            ParcelFileDescriptor pfd = mContext.getContentResolver().openFileDescriptor(getUri, "rwt");

            FileDescriptor fileDescriptor = pfd.getFileDescriptor();

            Log.e("ethan","path="+getUri);
            return fileDescriptor;
        }catch (Exception e) {
            e.printStackTrace();
        }
        return new FileDescriptor();

    }
    private Uri getTreeUri(){
        SharedPreferences sf = mContext.getSharedPreferences("treeUri",Context.MODE_PRIVATE);
        String uriString = sf.getString("treeUri","");
        if(TextUtils.isEmpty(uriString)){
            Toast.makeText(mContext,"no treeUri",Toast.LENGTH_SHORT).show();
        }else {
            Uri uri = Uri.parse(uriString);
            int takeflags = sf.getInt("treeUriFlags", Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            mContext.getContentResolver().takePersistableUriPermission(uri,takeflags);
            return uri;
        }
        return null;
    }
    private String generateFilePath() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        String sdcard = getStoragePath(mContext,true);
        Date date = new Date(System.currentTimeMillis());
        String dateSting = sdf.format(date);
        String path;
        if(sdcard == null) {
            File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/DPlayer");
            if(!file.exists())
                file.mkdir();
            path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/DPlayer/" + "media_muxer-" + dateSting + ".mp4";
        }else{
            File file = new File(sdcard+"/DPlayer");
            if(!file.exists())
                file.mkdir();
            path = sdcard+"/DPlayer/" + "media_muxer-" + dateSting + ".mp4";
        }
        Log.d("ethan","path="+path);
        return path;
    }
    private static String getStoragePath(Context mContext, boolean is_removale) {

        StorageManager mStorageManager = (StorageManager) mContext.getSystemService(Context.STORAGE_SERVICE);
        Class<?> storageVolumeClazz = null;
        is_removale = false;
        try {
            storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            Method getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
            Method getPath = storageVolumeClazz.getMethod("getPath");
            Method isRemovable = storageVolumeClazz.getMethod("isRemovable");
            Object result = getVolumeList.invoke(mStorageManager);
            final int length = Array.getLength(result);
            for (int i = 0; i < length; i++) {
                Object storageVolumeElement = Array.get(result, i);
                String path = (String) getPath.invoke(storageVolumeElement);
                boolean removable = (Boolean) isRemovable.invoke(storageVolumeElement);
                if (is_removale == removable) {
                    return path;
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }
    private MediaMuxer generateMuxer() {
        MediaMuxer mediaMuxer = null;
        try {
            mMediaMuxer = new MediaMuxer(generateFilePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return mediaMuxer;
    }
    private void switchMuxer() {
        mMediaMuxer.stop();
        mMediaMuxer = null;
        try {
            //mMediaMuxer = new MediaMuxer(generateFileDescripter(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mMediaMuxer = new MediaMuxer(generateFilePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(mIgnoreAudioTrack) {
            mAudioTrackIndex = 33;
            mAudioTrackFormat = null;
        } else {
            mAudioTrackIndex = mMediaMuxer.addTrack(mAudioTrackFormat);
        }

        mVideoTrackIndex = mMediaMuxer.addTrack(mVideoTrackFormat);
        mMediaMuxer.start();
    }
}
