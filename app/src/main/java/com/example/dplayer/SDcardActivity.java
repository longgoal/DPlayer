package com.example.dplayer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
//import androidx.documentfile.provider.DocumentFile;
//import android.support.v4.provider.DocumentFile;
import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

public class SDcardActivity extends AppCompatActivity {
    public static void startActivity(Context context) {
        Intent intent = new Intent(context, SDcardActivity.class);
        context.startActivity(intent);
    }    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ...
        //if (DocumentsUtils.checkWritableRootPath(getActivity(), rootPath)) {
            //showOpenDocumentTree();
        //}
        // ...
        //openDocument();
        sdcardAuth();
    }
    private final int OPEN_DOCUMENT = 101;
    private void openDocument(){
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent,OPEN_DOCUMENT);
    }
    private void showOpenDocumentTree() {
        Intent intent = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            StorageManager sm = getSystemService(StorageManager.class);

            StorageVolume volume = sm.getStorageVolume(new File("storage/sdcard1"));

            if (volume != null) {
                intent = volume.createAccessIntent(null);
            }
        }

        //if (intent == null) {
          //  intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        //}
        startActivityForResult(intent, 100);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case 100:
                if (data != null && data.getData() != null) {
                    Uri uri = data.getData();
                    //File file = new File(uri);
                    Log.d("ethan","Uri="+uri+",path="+uri.getPath());
                    //getContentResolver().openFileDescriptor(uri,"rw");

                    //DocumentsUtils.saveTreeUri(getActivity(), rootPath, uri);
                }
                break;
            case OPEN_DOCUMENT:
                if (data != null && data.getData() != null) {
                    Uri uri = data.getData();
                    //File file = new File(uri);
                    Log.d("ethan","Uri="+uri+",path="+uri.getPath());
                    //getContentResolver().openFileDescriptor(uri,"rw");

                    //DocumentsUtils.saveTreeUri(getActivity(), rootPath, uri);
                    handleOpenDocumentAction(data);
                }
                break;
            case SDCARD_AUTH_CODE:
                if (data != null && data.getData() != null) {
                    Uri uri = data.getData();
                    //File file = new File(uri);
                    Log.d("ethan","Uri="+uri+",path="+uri.getPath());
                    //getContentResolver().openFileDescriptor(uri,"rw");

                    //DocumentsUtils.saveTreeUri(getActivity(), rootPath, uri);
                    handleSdCardAuth(data);
                }
                break;
            default:
                break;
        }
    }
    private void handleOpenDocumentAction(Intent data){
        if (data == null) {
            return;
        }
        //获取文档指向的uri,注意这里是指单个文件。
        Uri uri = data.getData();
        //根据该Uri可以获取该Document的信息，其数据列的名称和解释可以在DocumentsContact类的内部类Document中找到
        //我们在此查询的信息仅仅只是演示作用
        Cursor cursor = getContentResolver().query(uri,null,
                null,null,null,null);
        StringBuilder sb = new StringBuilder(" open document Uri ");
        sb.append(uri.toString());
        if(cursor!=null && cursor.moveToFirst()){
            String documentId = cursor.getString(cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID));
            String name = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
            int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
            String size = null;
            if (!cursor.isNull(sizeIndex)) {
                // Technically the column stores an int, but cursor.getString()
                // will do the conversion automatically.
                size = cursor.getString(sizeIndex);
            } else {
                size = "Unknown";
            }
            sb.append(" name ").append(name).append(" size ").append(size);
        }
        //以下为直接从该uri中获取InputSteam，并读取出文本的内容的操作，这个是纯粹的java流操作，大家应该已经很熟悉了
        //我就不多解释了。另外这里也可以直接使用OutputSteam，向文档中写入数据。
        BufferedReader br = null;
        try {
            InputStream is = getContentResolver().openInputStream(uri);

            br = new BufferedReader(new InputStreamReader(is));
            String line;
            sb.append("\r\n content : ");
            while((line = br.readLine())!=null){
                sb.append(line);
            }
            showToast(sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            closeSafe(br);
        }
        try {
            ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(uri, "w");
            FileDescriptor fileDescriptor = fd.getFileDescriptor();
            FileOutputStream fileOutputStream =
                    new FileOutputStream(fileDescriptor);
            fileOutputStream.write(60);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    private void showToast(String st){
        Toast.makeText(this,st,Toast.LENGTH_SHORT).show();
        Log.d("ethen",st);
    }
    private void closeSafe(BufferedReader br){
        if(br != null) {
            try {
                br.close();
            }
            catch (Exception e){
                e.printStackTrace();
            }
        }
    }
    private final int SDCARD_AUTH_CODE = 102;
    private void sdcardAuth(){
        //获取存储管理服务
        StorageManager sm = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
        //获取存储器
        List<StorageVolume> list = sm.getStorageVolumes();
        for(StorageVolume sv : list){
            //遍历所有存储器，当它是Removable(包含外置sd卡，usb等)且已经装载时
            if(sv.isRemovable() && TextUtils.equals(sv.getState(), Environment.MEDIA_MOUNTED)) {
                //调用StorageVolume的createAccessIntent方法
                Intent i = sv.createAccessIntent(null);
                startActivityForResult(i, SDCARD_AUTH_CODE);
                return;
            }
        }
        showToast(" can not find sdcard ");
    }
    private void handleSdCardAuth(Intent data){
        if (data == null) {
            return;
        }
        //这里获取外置sd卡根目录的Uri,我们可以将它保存下来，方便以后使用
        Uri treeUri = data.getData();
        //赋予它永久性的读写权限
        final int takeFlags = data.getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
        //保存treeUri
        SharedPreferences sf = getSharedPreferences("treeUri",Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sf.edit();
        editor.putString("treeUri",treeUri.toString());
        editor.putInt("treeUriFlags",takeFlags);
        editor.apply();

        DocumentFile df;

        Uri uri = treeUri.buildUpon().appendPath("test.mp4").build();
        DocumentFile documentDir = DocumentFile.fromTreeUri(this, treeUri);
        DocumentFile documentFile = documentDir.createFile("video/mp4","test.mp4");
        Uri getUri = documentFile.getUri();
        MyApp myApp = (MyApp)getApplication();
        myApp.setRootUri(treeUri);
        showToast(" sdcard auth succeed,uri "+treeUri+",new uri"+uri+",documentFile"+documentFile+",getUri"+getUri);

        try {
            ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(getUri, "rwt");
            FileDescriptor fileDescriptor = fd.getFileDescriptor();

            FileOutputStream fileOutputStream =
                    new FileOutputStream(fileDescriptor);
            fileOutputStream.write(58);
            fileOutputStream.close();
            fd.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
