package com.example.dplayer;

import android.app.Application;
import android.net.Uri;

public class MyApp extends Application {
    private Uri mRootUri;
    public void setRootUri(Uri rootUri) {
        mRootUri = rootUri;
    }
    public Uri getRootUri() {
        return  mRootUri;
    }
}
