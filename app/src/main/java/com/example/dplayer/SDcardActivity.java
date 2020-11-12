package com.example.dplayer;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;


import java.io.File;

public class SDcardActivity extends AppCompatActivity {
    public static void startActivity(Context context) {
        Intent intent = new Intent(context, SDcardActivity.class);
        context.startActivity(intent);
    }    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ...
        //if (DocumentsUtils.checkWritableRootPath(getActivity(), rootPath)) {
            showOpenDocumentTree();
        //}
        // ...
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
            default:
                break;
        }
    }

}
