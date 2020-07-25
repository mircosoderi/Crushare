package it.mircosoderi.crushare;

import android.app.ActivityManager;
import android.arch.persistence.room.Room;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Surface;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageListener;
import com.google.android.gms.nearby.messages.PublishCallback;
import com.google.android.gms.nearby.messages.PublishOptions;
import com.google.android.gms.nearby.messages.Strategy;
import com.google.android.gms.nearby.messages.SubscribeCallback;
import com.google.android.gms.nearby.messages.SubscribeOptions;
import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;

import static android.support.v4.content.FileProvider.getUriForFile;

public class SlideshowActivity extends AppCompatActivity {

    HashMap<String,SparseArray<byte[]>> bigMessages = new HashMap<>();
    MessageListener mMessageListener;
    ArrayList<String> mIncoming;
    SparseIntArray mIncomingHashCodes;
    int mCountdown = 0;
    ArrayList<String> displayed = new ArrayList<>();
    SubscribeOptions mOptions = new SubscribeOptions.Builder()
            .setStrategy(Strategy.DEFAULT)
            .setCallback(new SubscribeCallback() {
                @Override
                public void onExpired() {
                    super.onExpired();
                    connect();
                }
            })
            .build();

    long mSessionId;
    ArrayList<ImgSign> imgSigns = new ArrayList<>();

    public void connect() {

        Toast.makeText(this, getString(R.string.connecting), Toast.LENGTH_SHORT).show();

        Nearby.getMessagesClient(this).subscribe(mMessageListener, mOptions).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(SlideshowActivity.this, getString(R.string.connection_error),
                        Toast.LENGTH_SHORT).show();
            }
        }).addOnCanceledListener(new OnCanceledListener() {
            @Override
            public void onCanceled() {
                Toast.makeText(SlideshowActivity.this, getString(R.string.canceled),
                        Toast.LENGTH_SHORT).show();
            }
        }).addOnSuccessListener(new OnSuccessListener<Object>() {
            @Override
            public void onSuccess(Object result) {
                Toast.makeText(SlideshowActivity.this, getString(R.string.connected),
                        Toast.LENGTH_SHORT).show();
            }
        });

    }

    public void disconnect() {

        Toast.makeText(SlideshowActivity.this, getString(R.string.disconnecting), Toast.LENGTH_SHORT).show();

        Nearby.getMessagesClient(this).unsubscribe(mMessageListener).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {

            }
        }).addOnSuccessListener(new OnSuccessListener<Object>() {
            @Override
            public void onSuccess(Object result) {

            }
        });

    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        setContentView(R.layout.activity_slideshow);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        boolean allowContentAccess = sharedPref.getBoolean(SettingsFragment.INLINE_IMAGES, true);
        boolean executeJavascript = sharedPref.getBoolean(SettingsFragment.EXECUTE_JAVASCRIPT, false);
        boolean safeBrowsing = sharedPref.getBoolean(SettingsFragment.SAFE_BROWSING, true);
        WebView slide = findViewById(R.id.slide);
        slide.setBackgroundColor(Color.TRANSPARENT);
        WebSettings settings = slide.getSettings();
        settings.setAllowContentAccess(allowContentAccess);
        settings.setJavaScriptEnabled(executeJavascript);
        settings.setUseWideViewPort(false);
        settings.setLoadWithOverviewMode(false);
        if (Build.VERSION.SDK_INT >= 26) {
            settings.setSafeBrowsingEnabled(safeBrowsing);
        }
        slide.setWebViewClient(new WebViewClient() {
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && url.startsWith("display://")) {
                    view.getContext().startActivity(new Intent().setData(Uri.parse(url)));
                    return true;
                }
                else {
                    return false;
                }
            }
        });
        String welcome;
        int rotation = Surface.ROTATION_0;
        WindowManager wm = ((WindowManager) getSystemService(Context.WINDOW_SERVICE));
        if(wm != null) {
            rotation = wm.getDefaultDisplay().getRotation();
        }
        if( rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            welcome = "<html><head><title>Nothing to display</title></head><body><div style=\"font-size: 4vh; line-height:1.6; color: #303F9F; text-align: justify; padding: 10vw; \">" + getString(R.string.slideshow_intro) + "</div></body></html>";
        }
        else {
            welcome = "<html><head><title>Nothing to display</title></head><body><div style=\"font-size: 4vw; line-height:1.5; color: #303F9F; text-align: justify; padding: 10vh; \">" + getString(R.string.slideshow_intro) + "</div></body></html>";
        }
        slide.loadDataWithBaseURL("file:///android_asset/", welcome, "text/html", "utf-8", null);

    }

    @Override
    public void onStart() {

        super.onStart();

        mSessionId = new Date().getTime();

        mIncoming = new ArrayList<>();
        mIncomingHashCodes = new SparseIntArray();

        mMessageListener = new SlideshowActivity.NearbyMessageListener(this);

        connect();

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        mCountdown = Integer.parseInt(sharedPref.getString(SettingsFragment.MIN_FRAME_DURATION, null));

        new CountDownTimer(1000*mCountdown, 1000) {

            public void onTick(long millisUntilFinished) {

            }

            public void onFinish() {
                if(!mIncoming.isEmpty()) {
                    mIncoming.add(mIncoming.get(0));
                    mIncomingHashCodes.put(mIncoming.size()-1,mIncomingHashCodes.get(0));
                    mIncoming.remove(0);
                    mIncomingHashCodes.delete(0);
                    ((WebView) findViewById(R.id.slide)).loadDataWithBaseURL("file:///android_asset/", mIncoming.get(0), "text/html", "utf-8", null);
                }
                start();
            }

        }.start();

    }

    @Override
    public void onStop() {
        disconnect();
        super.onStop();
    }

    private LayerDrawable getShape(String message) {

        LayerDrawable layerDrawable = null;
        int authorStart = message.indexOf("<meta name=\"author\" content=\"");
        if(authorStart > -1) {
            int authorEnd = message.indexOf("\">",authorStart);
            String author = message.substring(authorStart, authorEnd).replace("<meta name=\"author\" content=\"","");
            String colorString = "#"+author.substring(0,8);
            int color = adjustAlpha(Color.parseColor(colorString));
            layerDrawable = (LayerDrawable) ResourcesCompat.getDrawable(getResources(), R.drawable.webview_textview_bg, null);
            if(layerDrawable != null) {
                layerDrawable.mutate();
                ((GradientDrawable)layerDrawable.getDrawable(0)).setStroke((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics()), color);
                ((GradientDrawable)layerDrawable.getDrawable(0)).setColors(new int[]{Color.WHITE, Color.WHITE, color});
            }

        }
        return layerDrawable;
    }

    @ColorInt
    private static int adjustAlpha(@ColorInt int color) {
        int alpha = Math.round(205+Color.alpha(color)/255*(255-205));
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }

    class NearbyMessageListener extends MessageListener {

        AppCompatActivity mainActivity;

        NearbyMessageListener(AppCompatActivity mainActivity) {
            this.mainActivity = mainActivity;
        }

        private boolean isLowMemory() {
            try {
                ActivityManager activityManager = (ActivityManager) mainActivity.getSystemService(ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
                if(activityManager != null) activityManager.getMemoryInfo(memoryInfo);
                return memoryInfo.lowMemory;
            }
            catch(Exception e) {
                return false;
            }
        }

        @Override
        public void onFound(Message message) {

            if( (!(message instanceof BigMessage)) && message.getType().startsWith("#")) {
                int pos = Integer.parseInt(message.getType().split(" ")[0].replace("#",""));
                String type = message.getType().substring(message.getType().indexOf(" ")).trim();
                if(bigMessages.containsKey(type) && bigMessages.get(type).indexOfKey(pos) >= 0) return;
                if (isLowMemory()) return;
                if(!bigMessages.containsKey(type)) {
                    bigMessages.put(type,new SparseArray<byte[]>());
                }
                bigMessages.get(type).put(pos,message.getContent());
                if(message.getType().startsWith("##")) {
                    bigMessages.get(type).put(pos+1,new byte[1]);
                }
                int i = 0;
                int bigMessageSize = 0;
                while(bigMessages.get(type).get(i) != null) {
                    if(Arrays.equals(bigMessages.get(type).get(i), new byte[1])) {
                        byte[] bigContent = new byte[bigMessageSize];
                        int written = 0;
                        for(int j = 0; j < i; j++) {
                            System.arraycopy(bigMessages.get(type).get(j),0,bigContent,written,bigMessages.get(type).get(j).length);
                            written+=bigMessages.get(type).get(j).length;
                        }
                        onFound(new BigMessage(bigContent, type));
                    }
                    else {
                        bigMessageSize+=bigMessages.get(type).get(i).length;
                    }
                    i++;
                }
                return;
            }

            if( (!(message instanceof BigMessage)) && message.getType().equals("text/uuid")) return;

            if( (!(message instanceof BigMessage)) && displayed.contains(String.valueOf(Arrays.hashCode(message.getContent())))) return;

            if( (!(message instanceof BigMessage)) && new String(message.getContent()).startsWith("DESTROY")) {
                try {
                    String[] cmd = new String(message.getContent()).split(" ");
                    String author = cmd[1];
                    int mexHash = Integer.parseInt(cmd[2]);
                    AppDatabase db = Room.databaseBuilder(mainActivity, AppDatabase.class, "crushare").allowMainThreadQueries().fallbackToDestructiveMigration().build();
                    String content = new String(db.archivedMessageDao().getRawContent(mexHash));
                    String mediaAuthor = db.archivedMessageDao().getMediaAuthor(mexHash);
                    if (content.contains(author) || author.equals(mediaAuthor)) {
                        db.archivedMessageDao().destroy(mexHash, "%" + author + "%");
                        mIncoming.remove(mIncomingHashCodes.keyAt(mIncomingHashCodes.indexOfValue(mexHash)));
                        SparseIntArray newIncomingHashCodes = new SparseIntArray();
                        for (int i = 0; i < mIncomingHashCodes.size(); i++) {
                            if (mIncomingHashCodes.keyAt(i) < mIncomingHashCodes.indexOfValue(mexHash)) {
                                newIncomingHashCodes.put(mIncomingHashCodes.keyAt(i), mIncomingHashCodes.valueAt(i));
                            } else if (mIncomingHashCodes.keyAt(i) > mIncomingHashCodes.indexOfValue(mexHash)) {
                                newIncomingHashCodes.put(mIncomingHashCodes.keyAt(i) - 1, mIncomingHashCodes.valueAt(i));
                            }
                        }
                        mIncomingHashCodes = newIncomingHashCodes;
                        displayed.add(String.valueOf(Arrays.hashCode(message.getContent())));
                        displayed.add(String.valueOf(Arrays.hashCode(db.archivedMessageDao().getRawContent(mexHash))));
                    }
                }
                catch(Exception e) {
                    return;
                }
                return;
            }

            if((!(message instanceof BigMessage)) && message.getType().startsWith("text/sign")) {
                String[] type = message.getType().split(" ");
                String author = type[1];
                String hash = type[2];
                String sign =  new String(message.getContent());
                for(ImgSign imgSign: imgSigns) {
                    if(imgSign.author.equals(author) && imgSign.hash.equals(hash)) {
                        if(imgSign.sign == null && imgSign.mex != null) {
                            imgSign.sign = sign;
                            onFound(imgSign.mex);
                        }
                        return;
                    }
                }
                imgSigns.add(new ImgSign(author, hash, null, sign));
                return;
            }

            if(!("text/plain".equals(message.getType()))) {
                String author = message.getType().substring(0,message.getType().indexOf("."));
                String hash = String.valueOf(Arrays.hashCode(message.getContent()));
                boolean found = false;
                for(ImgSign imgSign: imgSigns) {
                    if(imgSign.author.equals(author) && imgSign.hash.equals(hash)) {
                        if(imgSign.sign != null) {
                            found = true;
                        }
                        else {
                            return;
                        }
                    }
                }
                if(!found) {
                    imgSigns.add(new ImgSign(author, hash, message, null));
                    return;
                }
            }

            String signatureToBeArchived = null;

            displayed.add(String.valueOf(Arrays.hashCode(message.getContent())));

            String mimeType;
            try {
                InputStream is = new BufferedInputStream(new ByteArrayInputStream(message.getContent()));
                mimeType = URLConnection.guessContentTypeFromStream(is);
                if(mimeType == null) mimeType = getMimeType(message.getType());
            }
            catch(Exception emt) {
                mimeType = "";
            }

            boolean isAllRight = true;

            if("text/plain".equals(message.getType())) {
                String txt = new String(message.getContent()).trim();
                if(!DetectHtml.isHtml(txt)) {
                    txt = txt.replace("\n","<br>");
                }
                if(txt.contains("<html>") && txt.contains("<head>") && txt.contains("</head>") && txt.contains("</html>")) {
                    txt = txt.substring(0, txt.indexOf("</head>")) + "<style>p.signature, div.datetime { display: none; }</style><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/><script src=\"jquery-3.3.1.min.js\"></script><script>$(document).ready( function () { $('body,html').animate( {scrollTop: 0 }, 1 ); setTimeout( function() { $('body,html').animate( {scrollTop: $(document).height() }, "+String.valueOf(mCountdown*2*800)+"); }, "+String.valueOf(mCountdown*2*100)+" ); } );</script>" + txt.substring(txt.indexOf("</head>"));
                }
                else {
                    txt = "<!DOCTYPE html>\n<html><head><style>p.signature, div.datetime { display: none; }</style><title>Slide</title><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/><script src=\"jquery-3.3.1.min.js\"></script><script>$(document).ready( function () { $('body,html').animate( {scrollTop: 0 }, 1 ); setTimeout( function() { $('body,html').animate( {scrollTop: $(document).height() }, "+String.valueOf(mCountdown*2*800)+"); }, "+String.valueOf(mCountdown*2*100)+" ); } );</script></head><body>"+txt+"</body></html>";
                }
                if(!mIncoming.contains(txt)) {
                    mIncoming.add(txt);
                    mIncomingHashCodes.put(mIncoming.size()-1,Arrays.hashCode(message.getContent()));
                    findViewById(R.id.slide).setBackground(getShape(txt));

                }

            }
            /*else if (mimeType != null && mimeType.startsWith("video/")) {
                try {

                    File tmp = File.createTempFile(getString(R.string.incoming_file_prefix), message.getType().substring(message.getType().lastIndexOf(".")), getCacheDir());
                    FileOutputStream tmpos = new FileOutputStream(tmp);
                    tmpos.write(message.getContent());
                    tmpos.flush();
                    tmpos.close();

                    String uuid = message.getType().substring(0,message.getType().indexOf("."));

                    Date mexDateTimeObj = new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf("."))));
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(SlideshowActivity.this);
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>";
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(SlideshowActivity.this);
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(SlideshowActivity.this);
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj)+"</div>";
                    }

                    signatureToBeArchived = new ImgSign().getSign(uuid,String.valueOf(Arrays.hashCode(message.getContent())));
                    new ImgSign().delSign(uuid,String.valueOf(Arrays.hashCode(message.getContent())));

                    Uri tmpUri = getUriForFile(SlideshowActivity.this, "it.mircosoderi.fileprovider", tmp);
                    grantUriPermission(getPackageName(), tmpUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mainActivity);
                    if (sharedPref.getBoolean(SettingsFragment.INLINE_VIDEOS, true)) {
                        String newMex = "<html><head><meta name=\"author\" content=\""+(signatureToBeArchived+" ").substring(0, 36)+"\"><title>"+tmp.getName()+"</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+getString(R.string.media_warning)+"</p>"+"<p>Put a look at this "+"<a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + getMimeType(tmpUri.toString()).substring(1 + getMimeType(tmpUri.toString()).indexOf("/")) + " "+getMimeType(tmpUri.toString()).substring(0,getMimeType(tmpUri.toString()).indexOf("/"))+"</b></a>!"+"</p>"+(signatureToBeArchived.length() > 36?"<p>"+getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signatureToBeArchived.substring(36)+"</span></p>":"")+"<p>"+"<object style=\"max-width:100%; height:auto; border: thin dotted #3F51B5;\" type=\""+mimeType+"\" data=\"" + tmpUri.toString() + "\"><param name=\"autoplay\" value=\"false\" ><param name=\"loop\" value=\"false\" ></object>"+"</p></body></html>";
                        if (!mIncoming.contains(newMex)) {
                            mIncoming.add(newMex);
                            mIncomingHashCodes.put(-1+mIncoming.size(),Arrays.hashCode(message.getContent()));
                        }
                    } else {
                        String newMex = "<html><head><meta name=\"author\" content=\""+(signatureToBeArchived+" ").substring(0, 36)+"\"><title>" + tmp.getName() + "</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+getString(R.string.media_warning)+"</p>"+"<p>Put a look at this <a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + getMimeType(tmpUri.toString()).substring(1 + getMimeType(tmpUri.toString()).indexOf("/")) + " "+getMimeType(tmpUri.toString()).substring(0,getMimeType(tmpUri.toString()).indexOf("/"))+"</b></a>!</p>"+(signatureToBeArchived.length() > 36?"<p>"+getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signatureToBeArchived.substring(36)+"</span></p>":"")+"</body></html>";
                        if (!mIncoming.contains(newMex)) {
                            mIncoming.add(newMex);
                            mIncomingHashCodes.put(-1+mIncoming.size(),Arrays.hashCode(message.getContent()));
                        }
                    }
                } catch (IOException e) {
                    isAllRight = false;
                    Date mexDateTimeObj = new Date();
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(SlideshowActivity.this);
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(SlideshowActivity.this);
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(SlideshowActivity.this);
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                    }
                    mIncoming.add(String.format(getString(R.string.file_missed),datetime));
                    new AsyncDoArchiveMessage(SlideshowActivity.this, new Message(String.format(getString(R.string.file_missed),datetime).getBytes(),"text/plain"), SlideshowActivity.this.mSessionId, false, null, null).execute();
                    mIncomingHashCodes.put(mIncoming.size()-1,-1);
                }


            }
            else if (mimeType != null && "application/pdf".equals(mimeType)) {
                try {

                    File tmp = File.createTempFile(getString(R.string.incoming_file_prefix), message.getType().substring(message.getType().lastIndexOf(".")), getCacheDir());
                    FileOutputStream tmpos = new FileOutputStream(tmp);
                    tmpos.write(message.getContent());
                    tmpos.flush();
                    tmpos.close();

                    String uuid = message.getType().substring(0,message.getType().indexOf("."));

                    Date mexDateTimeObj = new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf("."))));


                    signatureToBeArchived = new ImgSign().getSign(uuid,String.valueOf(Arrays.hashCode(message.getContent())));
                    new ImgSign().delSign(uuid,String.valueOf(Arrays.hashCode(message.getContent())));

                    Uri tmpUri = getUriForFile(SlideshowActivity.this, "it.mircosoderi.fileprovider", tmp);
                    grantUriPermission(getPackageName(), tmpUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    String newMex = "<html><head><meta name=\"author\" content=\""+(signatureToBeArchived+" ").substring(0, 36)+"\"><title>"+tmp.getName()+"</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body></body></html>";
                    if (!mIncoming.contains(newMex)) {
                         mIncoming.add(newMex);
                         mIncomingHashCodes.put(-1+mIncoming.size(),Arrays.hashCode(message.getContent()));
                    }
                } catch (IOException e) {
                    isAllRight = false;
                    Date mexDateTimeObj = new Date();
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(SlideshowActivity.this);
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(SlideshowActivity.this);
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(SlideshowActivity.this);
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                    }
                    mIncoming.add(String.format(getString(R.string.file_missed),datetime));
                    new AsyncDoArchiveMessage(SlideshowActivity.this, new Message(String.format(getString(R.string.file_missed),datetime).getBytes(),"text/plain"), SlideshowActivity.this.mSessionId, false, null, null).execute();
                    mIncomingHashCodes.put(mIncoming.size()-1,-1);
                }

            }*/
            else if (mimeType != null && mimeType.startsWith("image/")) {
                try {

                    File tmp = File.createTempFile(getString(R.string.incoming_file_prefix), message.getType().substring(message.getType().lastIndexOf(".")), getCacheDir());
                    FileOutputStream tmpos = new FileOutputStream(tmp);
                    tmpos.write(message.getContent());
                    tmpos.flush();
                    tmpos.close();

                    String uuid = message.getType().substring(0,message.getType().indexOf("."));
                    signatureToBeArchived = new ImgSign().getSign(uuid,String.valueOf(Arrays.hashCode(message.getContent())));
                    new ImgSign().delSign(uuid,String.valueOf(Arrays.hashCode(message.getContent())));

                    Uri tmpUri = getUriForFile(SlideshowActivity.this, "it.mircosoderi.fileprovider", tmp);
                    grantUriPermission(getPackageName(), tmpUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mainActivity);
                    if (getMimeType(tmpUri.toString()).startsWith("image/") && sharedPref.getBoolean(SettingsFragment.INLINE_IMAGES, true)) {
                        String newMex = "<html><head><meta name=\"author\" content=\""+(signatureToBeArchived+" ").substring(0,36)+"\"><title>Slide</title><script src=\"jquery-3.3.1.min.js\"></script><script>$(document).ready( function () { $('body,html').animate( {scrollTop: 0 }, 1 ); setTimeout( function() { $('body,html').animate( {scrollTop: $(document).height() }, "+String.valueOf(mCountdown*900)+"); }, "+String.valueOf(mCountdown*50)+" ); } );</script><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+"<p><img style=\"max-width:100%; height:auto;\" src=\"" + tmpUri.toString() + "\" title=\"" + tmp.getName() + "\"/></p></body></html>";
                        if (!mIncoming.contains(newMex)) {
                            mIncoming.add(newMex);
                            mIncomingHashCodes.put(mIncoming.size()-1,Arrays.hashCode(message.getContent()));
                            findViewById(R.id.slide).setBackground(getShape(newMex));
                        }
                    }

                } catch (IOException e) {
                    isAllRight = false;
                    /*isAllRight = false;
                    Date mexDateTimeObj = new Date();
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mainActivity);
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mainActivity);
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mainActivity);
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                    }
                    mIncoming.add(String.format(getString(R.string.file_missed),datetime));
                    new AsyncDoArchiveMessage(SlideshowActivity.this, new Message(String.format(getString(R.string.file_missed),datetime).getBytes(),"text/plain"), SlideshowActivity.this.mSessionId, false, null, null).execute();
                    mIncomingHashCodes.put(mIncoming.size()-1,-1);*/
                }
            }
            /*else {
                try {

                    File tmptmp = File.createTempFile(getString(R.string.incoming_file_prefix), message.getType().substring(message.getType().lastIndexOf(".")), getCacheDir());
                    FileOutputStream tmpos = new FileOutputStream(tmptmp);
                    tmpos.write(message.getContent());
                    tmpos.flush();
                    tmpos.close();
                    String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                    if(ext == null) ext = message.getType().substring(1+message.getType().lastIndexOf("."));
                    if( (!ext.isEmpty()) && (!ext.startsWith("."))) ext = "."+ext;
                    File tmp;
                    if(!"zip".equals(ext)) {
                        tmp = File.createTempFile(tmptmp.getName(), ".zip", getCacheDir());
                        FileOutputStream dest = new FileOutputStream(tmp);
                        ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));
                        String entryName = tmptmp.getName();
                        ZipEntry entry = new ZipEntry(entryName);
                        out.putNextEntry(entry);
                        FileInputStream fi = new FileInputStream(tmptmp);
                        BufferedInputStream origin = new BufferedInputStream(fi, 1024);
                        int count;
                        byte data[] = new byte[1024];
                        while ((count = origin.read(data, 0, 1024)) != -1) {
                            out.write(data, 0, count);
                        }
                        origin.close();
                        fi.close();
                        out.close();
                        dest.close();
                    }
                    else {
                        tmp = tmptmp;
                    }

                    String uuid = message.getType().substring(0,message.getType().indexOf("."));

                    Date mexDateTimeObj = new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf("."))));
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(SlideshowActivity.this);
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>";
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(SlideshowActivity.this);
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(SlideshowActivity.this);
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj)+"</div>";
                    }

                    signatureToBeArchived = new ImgSign().getSign(uuid,String.valueOf(Arrays.hashCode(message.getContent())));
                    new ImgSign().delSign(uuid,String.valueOf(Arrays.hashCode(message.getContent())));

                    Uri tmpUri = getUriForFile(SlideshowActivity.this, "it.mircosoderi.fileprovider", tmp);
                    grantUriPermission(getPackageName(), tmpUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    String newMex = "<html><head><meta name=\"author\" content=\""+(signatureToBeArchived+" ").substring(0, 36)+"\"><title>"+tmp.getName()+"</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+getString(R.string.media_warning)+"</p>"+"<p>Put a look at files in this "+"<a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>archive</b></a>!"+"</p>"+(signatureToBeArchived.length() > 36?"<p>"+getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signatureToBeArchived.substring(36)+"</span></p>":"")+"</body></html>";
                    if (!mIncoming.contains(newMex)) {
                        AppDatabase db = Room.databaseBuilder(mainActivity, AppDatabase.class, "crushare").allowMainThreadQueries().fallbackToDestructiveMigration().build();
                        String lastMessageContent;
                        String lastMessageAuthor;
                        String lastMessageSession;
                        try {
                            lastMessageContent = new String(db.archivedMessageDao().getLastMessage().get(0).getMessageContent());
                            lastMessageAuthor = db.archivedMessageDao().getLastMessage().get(0).getAuthor();
                            lastMessageSession = String.valueOf(db.archivedMessageDao().getLastMessage().get(0).getSessionId());
                        }
                        catch(Exception e) {
                            lastMessageContent = "";
                            lastMessageAuthor = "";
                            lastMessageSession = "";
                        }
                        if(lastMessageContent.isEmpty()) lastMessageContent = "empty";
                        if(lastMessageAuthor == null || lastMessageAuthor.isEmpty()) lastMessageAuthor = "nobody";
                        if(lastMessageSession.isEmpty()) lastMessageAuthor = "boh";
                        mIncoming.add(newMex);
                        mIncomingHashCodes.put(-1+mIncoming.size(),Arrays.hashCode(message.getContent()));
                    }
                } catch (IOException e) {
                    isAllRight = false;
                    Date mexDateTimeObj = new Date();
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(SlideshowActivity.this);
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(SlideshowActivity.this);
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(SlideshowActivity.this);
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                    }
                    mIncoming.add(String.format(getString(R.string.file_missed),datetime));
                    new AsyncDoArchiveMessage(SlideshowActivity.this, new Message(String.format(getString(R.string.file_missed),datetime).getBytes(),"text/plain"), SlideshowActivity.this.mSessionId, false, null, null).execute();
                    mIncomingHashCodes.put(mIncoming.size()-1,-1);
                }
            }
*/
            if(isAllRight) {
                new AsyncDoArchiveMessage(mainActivity, message, mSessionId, false, signatureToBeArchived != null ? (signatureToBeArchived + " ").substring(36).trim() : null, signatureToBeArchived != null ? (signatureToBeArchived + " ").substring(0, 36) : null).execute();
                deliver(new BigMessage(message.getContent(), message.getType()));
            }
        }

        @Override
        public void onLost(Message message) {}

        private String getMimeType(String url) {
            String type = null;
            String extension = MimeTypeMap.getFileExtensionFromUrl(url);
            if (extension != null) {
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            }
            return type;
        }

    }

    private void deliver(final Message message) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        final int delay = Integer.parseInt(sharedPref.getString(SettingsFragment.DELIVERY_INTERVAL, "10"));
        final int attempts = Integer.parseInt(sharedPref.getString(SettingsFragment.DELIVERY_ATTEMPTS, "2"));
        new Postman(this, message, delay, attempts-1).run();
    }

    class Postman implements Runnable {

        Context context;
        Message message;
        int delay;
        int attempts;
        Handler handler = new Handler();
        boolean init = false;

        Postman(Context context, Message message, int delay, int attempts) {
            this.context = context;
            this.message = message;
            this.delay = 1000*delay;
            this.attempts = attempts;
        }

        public void run(){

            if(0 == attempts) {
                return;
            }

            if(!init) {
                init = true;
                handler.postDelayed(this, Double.valueOf(Math.abs(new Random().nextGaussian())*delay).longValue());
                return;
            }

            attempts--;
            if(message.getContent().length < 80000) {
                Nearby.getMessagesClient(context)
                        .publish(new Message(message.getContent(), message.getType()), new PublishOptions.Builder().setStrategy(Strategy.DEFAULT).build());
            }
            else {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            int totalSize = message.getContent().length;
                            int deliveredBytes = 0;
                            int deliveredMessages = 0;
                            Random r = new Random();
                            while (totalSize > deliveredBytes) {
                                int chunkSize = 50000+(deliveredMessages*10000%50000);
                                String chunkMime;
                                if(deliveredBytes+chunkSize>totalSize) {
                                    chunkSize=totalSize-deliveredBytes;
                                    chunkMime = "##"+String.valueOf(deliveredMessages)+" "+message.getType();
                                }
                                else {
                                    chunkMime = "#"+String.valueOf(deliveredMessages)+" "+message.getType();
                                }
                                byte[] chunk = Arrays.copyOfRange(message.getContent(),deliveredBytes,deliveredBytes+chunkSize);
                                PublishOptions options = new PublishOptions.Builder()
                                        .setStrategy(Strategy.DEFAULT)
                                        .setCallback(new PublishCallback() {
                                            @Override
                                            public void onExpired() {
                                                super.onExpired();
                                                finish();
                                            }
                                        }).build();
                                Nearby.getMessagesClient(SlideshowActivity.this)
                                        .publish(new Message(chunk,chunkMime), options)
                                        .addOnFailureListener(new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception e) {
                                            }
                                        });
                                deliveredBytes+=chunkSize;
                                deliveredMessages++;
                                int sleepTime = r.nextInt(3000)+1000;
                                Thread.sleep(sleepTime);
                            }
                        }
                        catch(Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
            handler.postDelayed(this, Double.valueOf(Math.abs(new Random().nextGaussian()) * delay).longValue());

        }

    }

    class ImgSign {
        String author;
        String hash;
        Message mex;
        String sign;

        ImgSign(String author, String hash, Message mex, String sign) {
            this.author = author;
            this.hash = hash;
            this.mex = mex;
            this.sign = sign;
        }

        ImgSign() {}

        String getSign(String author, String hash) {
            for(ImgSign imgSign: imgSigns) {
                if(imgSign.author.equals(author) && imgSign.hash.equals(hash)) {
                    return imgSign.sign;
                }
            }
            return getString(R.string.signature_default);
        }

        void delSign(String author, String hash) {
            for(ImgSign imgSign: imgSigns) {
                if(imgSign.author.equals(author) && imgSign.hash.equals(hash)) {
                    imgSigns.remove(imgSign);
                }
            }
        }
    }

}
