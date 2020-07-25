package it.mircosoderi.crushare;

import android.arch.persistence.room.Room;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.SparseIntArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.webkit.MimeTypeMap;
import android.widget.AbsListView;
import android.widget.ListView;
import com.google.android.gms.nearby.messages.Message;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URLConnection;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static android.support.v4.content.FileProvider.getUriForFile;

public class ArchiveActivity extends AppCompatActivity implements AbsListView.OnScrollListener {

    static ArrayList<String> mArchive = new ArrayList<>();
    WebViewArrayAdapter mAdapter;
    static long mMinSessionId;
    static long mMaxSessionId;
    SparseIntArray mHashCodes = new SparseIntArray();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        setContentView(R.layout.activity_archive);

        mAdapter = new WebViewArrayAdapter(this, mArchive);
        ListView listView = findViewById(R.id.archive);
        listView.setAdapter(mAdapter);
        listView.setOnScrollListener(this);

    }

    private static class Init {

        WeakReference<Context> mContext;

        Init(Context context) {
            mContext = new WeakReference<>(context);
        }

        void execute() {
            int displayed;
            AppDatabase db = Room.databaseBuilder(mContext.get(),
                    AppDatabase.class, "crushare").allowMainThreadQueries().fallbackToDestructiveMigration().build();
            mMinSessionId = db.archivedMessageDao().maxSessionId();
            mMaxSessionId = mMinSessionId;
            List<ArchivedMessage> archivedMessages = db.archivedMessageDao().getArchivedMessagesDesc(mMinSessionId);
            for(ArchivedMessage archivedMessage: archivedMessages) {
                Message message = new BigMessage(archivedMessage.getMessageContent(), archivedMessage.getMessageType());
                add(message, archivedMessage.isOutgoing(), archivedMessage.getSignature(), archivedMessage.getAuthor(), archivedMessage.getMessageId());
            }
            if(archivedMessages.size() == 0) {
                Date mexDateTimeObj = new Date();
                String datetime;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                    String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                    datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                }
                else {
                    DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                    DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                    datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                }
                add(new BigMessage(String.format(mContext.get().getString(R.string.archive_is_empty),datetime).getBytes(),"text/plain"), false, null, null, -1);
            }
            else {
                displayed = archivedMessages.size();
                while(displayed < 10 && archivedMessages.size() > 0) {
                    mMinSessionId = db.archivedMessageDao().prevSessionId(mMinSessionId);
                    archivedMessages = db.archivedMessageDao().getArchivedMessagesDesc(mMinSessionId);
                    for(ArchivedMessage archivedMessage: archivedMessages) {
                        Message message = new BigMessage(archivedMessage.getMessageContent(), archivedMessage.getMessageType());
                        add(message, archivedMessage.isOutgoing(), archivedMessage.getSignature(), archivedMessage.getAuthor(), archivedMessage.getMessageId());
                    }
                    displayed+=archivedMessages.size();
                }
            }
            db.close();
            ((ArchiveActivity)mContext.get()).mAdapter.notifyDataSetChanged();
        }

        private void add(Message message, boolean outgoing, String signature, String uuid, long messageId) {

            String mimeType;
            try {
                InputStream is = new BufferedInputStream(new ByteArrayInputStream(message.getContent()));
                mimeType = URLConnection.guessContentTypeFromStream(is);
            }
            catch(Exception emt) {
                mimeType = "";
            }

            if("text/plain".equals(message.getType())) {
                String txt = new String(message.getContent()).trim();
                if(!DetectHtml.isHtml(txt)) txt = txt.replace("\n","<br>");
                if(txt.contains("<html>") && txt.contains("<head>") && txt.contains("</head>") && txt.contains("</html>")) {
                    txt = txt.substring(0, txt.indexOf("</head>")) + "<link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/>" + txt.substring(txt.indexOf("</head>"));
                }
                else {
                    txt = "<!DOCTYPE html>\n<html><head><title>Message</title><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+txt+"</body></html>";
                }
                if(outgoing) txt = "#outgoing "+txt;
                txt = txt.replace("[/body]","["+String.valueOf(messageId)+"][/body]"); //
                if(!mArchive.contains(txt)) {
                    mArchive.add(txt);
                    ((ArchiveActivity)mContext.get()).mHashCodes.put(mArchive.size()-1, Arrays.hashCode(message.getContent()));
                }
            }
            else if (mimeType != null && mimeType.startsWith("video/")) {
                try {

                    File tmp = File.createTempFile(mContext.get().getString(R.string.incoming_file_prefix), message.getType().substring(message.getType().lastIndexOf(".")), mContext.get().getCacheDir());
                    FileOutputStream tmpos = new FileOutputStream(tmp);
                    tmpos.write(message.getContent());
                    tmpos.flush();
                    tmpos.close();

                    // String uuid = message.getType().substring(0,message.getType().indexOf("."));

                    /*DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                    DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                    String datetime = "<div class=\"datetime\">"+dateFormat.format(new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf(".")))))+" "+timeFormat.format(new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf(".")))))+"</div>";
*/
                    Date mexDateTimeObj = new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf("."))));
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>";
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj)+"</div>";
                    }

                    Uri tmpUri = getUriForFile(mContext.get(), "it.mircosoderi.fileprovider", tmp);
                    mContext.get().grantUriPermission(mContext.get().getPackageName(), tmpUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext.get());
                    if (sharedPref.getBoolean(SettingsFragment.INLINE_VIDEOS, true)) {
                        String newMex = "<html><head><meta name=\"author\" content=\""+uuid+"\"><title>"+tmp.getName()+"</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+mContext.get().getString(R.string.media_warning)+"</p>"+"<p>Put a look at this "+"<a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + getMimeType(tmpUri.toString()).substring(1 + getMimeType(tmpUri.toString()).indexOf("/")) + " "+getMimeType(tmpUri.toString()).substring(0,getMimeType(tmpUri.toString()).indexOf("/"))+"</b></a>!"+"</p>"+(!signature.isEmpty()?"<p>"+mContext.get().getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"")+"<p><object style=\"width:100%;\" data=\""+tmpUri.toString()+"\" type=\""+getMimeType(tmpUri.toString())+"\" /></p></body></html>";
                        if(outgoing) newMex = "#outgoing "+newMex;
                        if(!mArchive.contains(newMex)) {
                            mArchive.add(newMex);
                            ((ArchiveActivity)mContext.get()).mHashCodes.put(mArchive.size()-1, Arrays.hashCode(message.getContent()));
                        }
                    } else {
                        String newMex = "<html><head><meta name=\"author\" content=\""+uuid+"\"><title>" + tmp.getName() + "</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+mContext.get().getString(R.string.media_warning)+"</p><p>Put a look at this <a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + getMimeType(tmpUri.toString()).substring(1 + getMimeType(tmpUri.toString()).indexOf("/")) + " "+getMimeType(tmpUri.toString()).substring(0,getMimeType(tmpUri.toString()).indexOf("/"))+"</b></a>!</p>"+(!signature.isEmpty()?"<p>"+mContext.get().getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"")+"</body></html>";
                        if(outgoing) newMex = "#outgoing "+newMex;
                        if(!mArchive.contains(newMex)) {
                            mArchive.add(newMex);
                            ((ArchiveActivity)mContext.get()).mHashCodes.put(mArchive.size()-1, Arrays.hashCode(message.getContent()));
                        }
                    }

                } catch (IOException e) {
                    Date mexDateTimeObj = new Date();
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                    }
                    mArchive.add(String.format(mContext.get().getString(R.string.file_missed),datetime));
                }
            }
            else if (mimeType != null && mimeType.startsWith("application/pdf")) {
                try {

                    File tmp = File.createTempFile(mContext.get().getString(R.string.incoming_file_prefix), message.getType().substring(message.getType().lastIndexOf(".")), mContext.get().getCacheDir());
                    FileOutputStream tmpos = new FileOutputStream(tmp);
                    tmpos.write(message.getContent());
                    tmpos.flush();
                    tmpos.close();

                    // String uuid = message.getType().substring(0,message.getType().indexOf("."));

                    /*DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                    DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                    String datetime = "<div class=\"datetime\">"+dateFormat.format(new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf(".")))))+" "+timeFormat.format(new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf(".")))))+"</div>";
*/
                    Date mexDateTimeObj = new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf("."))));
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>";
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj)+"</div>";
                    }

                    Uri tmpUri = getUriForFile(mContext.get(), "it.mircosoderi.fileprovider", tmp);
                    mContext.get().grantUriPermission(mContext.get().getPackageName(), tmpUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    String newMex = "<html><head><meta name=\"author\" content=\""+uuid+"\"><title>" + tmp.getName() + "</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+mContext.get().getString(R.string.media_warning)+"</p><p>Put a look at this <a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>PDF document</b></a>!</p>"+(!signature.isEmpty()?"<p>"+mContext.get().getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"")+"</body></html>";
                    if(outgoing) newMex = "#outgoing "+newMex;
                    if(!mArchive.contains(newMex)) {
                        mArchive.add(newMex);
                        ((ArchiveActivity)mContext.get()).mHashCodes.put(mArchive.size()-1, Arrays.hashCode(message.getContent()));
                    }


                } catch (IOException e) {
                    Date mexDateTimeObj = new Date();
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                    }
                    mArchive.add(String.format(mContext.get().getString(R.string.file_missed),datetime));
                }
            }
            else if (mimeType != null && mimeType.startsWith("image/")) {
                try {

                    File tmp = File.createTempFile(mContext.get().getString(R.string.incoming_file_prefix), message.getType().substring(message.getType().lastIndexOf(".")), mContext.get().getCacheDir());
                    FileOutputStream tmpos = new FileOutputStream(tmp);
                    tmpos.write(message.getContent());
                    tmpos.flush();
                    tmpos.close();

                    // String uuid = message.getType().substring(0,message.getType().indexOf("."));

                    /*DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                    DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                    String datetime = "<div class=\"datetime\">"+dateFormat.format(new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf(".")))))+" "+timeFormat.format(new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf(".")))))+"</div>";
*/
                    Date mexDateTimeObj = new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf("."))));
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>";
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj)+"</div>";
                    }

                    Uri tmpUri = getUriForFile(mContext.get(), "it.mircosoderi.fileprovider", tmp);
                    mContext.get().grantUriPermission(mContext.get().getPackageName(), tmpUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext.get());
                    if (getMimeType(tmpUri.toString()).startsWith("image/") && sharedPref.getBoolean(SettingsFragment.INLINE_IMAGES, true)) {
                        String newMex = "<html><head><meta name=\"author\" content=\""+uuid+"\"><title>"+tmp.getName()+"</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+mContext.get().getString(R.string.media_warning)+"</p>"+"<p>Put a look at this "+"<a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + getMimeType(tmpUri.toString()).substring(1 + getMimeType(tmpUri.toString()).indexOf("/")) + " "+getMimeType(tmpUri.toString()).substring(0,getMimeType(tmpUri.toString()).indexOf("/"))+"</b></a>!"+"</p>"+(!signature.isEmpty()?"<p>"+mContext.get().getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"")+"<p><object style=\"width:100%;\" data=\""+tmpUri.toString()+"\" type=\""+getMimeType(tmpUri.toString())+"\" /></p></body></html>";
                        if(outgoing) newMex = "#outgoing "+newMex;
                        if(!mArchive.contains(newMex)) {
                            mArchive.add(newMex);
                            ((ArchiveActivity)mContext.get()).mHashCodes.put(mArchive.size()-1, Arrays.hashCode(message.getContent()));
                        }
                    } else {
                        String newMex = "<html><head><meta name=\"author\" content=\""+uuid+"\"><title>" + tmp.getName() + "</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+mContext.get().getString(R.string.media_warning)+"</p><p>Put a look at this <a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + getMimeType(tmpUri.toString()).substring(1 + getMimeType(tmpUri.toString()).indexOf("/")) + " "+getMimeType(tmpUri.toString()).substring(0,getMimeType(tmpUri.toString()).indexOf("/"))+"</b></a>!</p>"+(!signature.isEmpty()?"<p>"+mContext.get().getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"")+"</body></html>";
                        if(outgoing) newMex = "#outgoing "+newMex;
                        if(!mArchive.contains(newMex)) {
                            mArchive.add(newMex);
                            ((ArchiveActivity)mContext.get()).mHashCodes.put(mArchive.size()-1, Arrays.hashCode(message.getContent()));
                        }
                    }

                } catch (IOException e) {
                    Date mexDateTimeObj = new Date();
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                    }
                    mArchive.add(String.format(mContext.get().getString(R.string.file_missed),datetime));
                }
            }
            else {
                try {

                    File tmptmp = File.createTempFile(mContext.get().getString(R.string.incoming_file_prefix), message.getType().substring(message.getType().lastIndexOf(".")), mContext.get().getCacheDir());
                    FileOutputStream tmpos = new FileOutputStream(tmptmp);
                    tmpos.write(message.getContent());
                    tmpos.flush();
                    tmpos.close();
                    String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                    if(ext == null) ext = message.getType().substring(1+message.getType().lastIndexOf("."));
                    if( (!ext.isEmpty()) && (!ext.startsWith("."))) ext = "."+ext;
                    File tmp;
                    if(!"zip".equals(ext)) {
                        tmp = File.createTempFile(tmptmp.getName(), ".zip", mContext.get().getCacheDir());
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

                    // String uuid = message.getType().substring(0,message.getType().indexOf("."));

                /*DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                String datetime = "<div class=\"datetime\">"+dateFormat.format(new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf(".")))))+" "+timeFormat.format(new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf(".")))))+"</div>";
*/
                    Date mexDateTimeObj = new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf("."))));
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>";
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj)+"</div>";
                    }

                    Uri tmpUri = getUriForFile(mContext.get(), "it.mircosoderi.fileprovider", tmp);
                    mContext.get().grantUriPermission(mContext.get().getPackageName(), tmpUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    String newMex = "<html><head><meta name=\"author\" content=\""+uuid+"\"><title>" + tmp.getName() + "</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+mContext.get().getString(R.string.media_warning)+"</p><p>Put a look at files in this <a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>archive</b></a>!</p>"+(!signature.isEmpty()?"<p>"+mContext.get().getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"")+"</body></html>";
                    if(outgoing) newMex = "#outgoing "+newMex;
                    if(!mArchive.contains(newMex)) {
                        mArchive.add(newMex);
                        ((ArchiveActivity)mContext.get()).mHashCodes.put(mArchive.size()-1, Arrays.hashCode(message.getContent()));
                    }


                } catch (IOException e) {
                    Date mexDateTimeObj = new Date();
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                    }
                    mArchive.add(String.format(mContext.get().getString(R.string.file_missed),datetime));
                }

            }

        }

        private String getMimeType(String url) {
            String type = null;
            String extension = MimeTypeMap.getFileExtensionFromUrl(url);
            if (extension != null) {
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            }
            return type;
        }

    }

    @Override
    public void onStart() {
        super.onStart();
        mArchive.clear();
        mHashCodes.clear();
        mMaxSessionId = 0;
        mMinSessionId = 0;
        new Init(this).execute();
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {

    }

    private static class Update {

        WeakReference<Context> mContext;


        Update(Context context) {
            mContext = new WeakReference<>(context);
        }

        void execute() {
            List<ArchivedMessage> archivedMessages = null;
            AppDatabase db = Room.databaseBuilder(mContext.get(),
                    AppDatabase.class, "crushare").allowMainThreadQueries().fallbackToDestructiveMigration().build();
            ListView listView = ((ArchiveActivity)mContext.get()).findViewById(R.id.archive);
            if(listView.getLastVisiblePosition() == mArchive.size()-1) {
                mMinSessionId = db.archivedMessageDao().prevSessionId(mMinSessionId);
                archivedMessages = db.archivedMessageDao().getArchivedMessagesDesc(mMinSessionId);
                for(ArchivedMessage archivedMessage: archivedMessages) {
                    Message message = new BigMessage(archivedMessage.getMessageContent(), archivedMessage.getMessageType());
                    add(message, archivedMessage.isOutgoing(), archivedMessage.getSignature(), archivedMessage.getAuthor());
                }
            }
            db.close();
            if(archivedMessages != null && !archivedMessages.isEmpty()) {
                ((ArchiveActivity)mContext.get()).mAdapter.notifyDataSetChanged();
            }
        }

        private void add(Message message, boolean outgoing, String signature, String uuid) {

            String mimeType;
            try {
                InputStream is = new BufferedInputStream(new ByteArrayInputStream(message.getContent()));
                mimeType = URLConnection.guessContentTypeFromStream(is);
            }
            catch(Exception emt) {
                mimeType = "";
            }

            if("text/plain".equals(message.getType())) {
                String txt = new String(message.getContent()).trim();
                if(!DetectHtml.isHtml(txt)) txt = txt.replace("\n","<br>");
                if(txt.contains("<html>") && txt.contains("<head>") && txt.contains("</head>") && txt.contains("</html>")) {
                    txt = txt.substring(0, txt.indexOf("</head>")) + "<link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/>" + txt.substring(txt.indexOf("</head>"));
                }
                else {
                    txt = "<!DOCTYPE html>\n<html><head><title>Message</title><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+txt+"</body></html>";
                }
                if(outgoing) txt = "#outgoing "+txt;
                if(!mArchive.contains(txt)) {
                    mArchive.add(txt);
                    ((ArchiveActivity)mContext.get()).mHashCodes.put(mArchive.size()-1, Arrays.hashCode(message.getContent()));
                }
            }
            else if (mimeType != null && mimeType.startsWith("video/")) {
                try {

                    File tmp = File.createTempFile(mContext.get().getString(R.string.incoming_file_prefix), message.getType().substring(message.getType().lastIndexOf(".")), mContext.get().getCacheDir());
                    FileOutputStream tmpos = new FileOutputStream(tmp);
                    tmpos.write(message.getContent());
                    tmpos.flush();
                    tmpos.close();

                    // String uuid = message.getType().substring(0,message.getType().indexOf("."));

                    /*DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                    DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                    String datetime = "<div class=\"datetime\">"+dateFormat.format(new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf(".")))))+" "+timeFormat.format(new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf(".")))))+"</div>";
*/
                    Date mexDateTimeObj = new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf("."))));
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>";
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj)+"</div>";
                    }

                    Uri tmpUri = getUriForFile(mContext.get(), "it.mircosoderi.fileprovider", tmp);
                    mContext.get().grantUriPermission(mContext.get().getPackageName(), tmpUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext.get());
                    if (sharedPref.getBoolean(SettingsFragment.INLINE_VIDEOS, true)) {
                        String newMex = "<html><head><meta name=\"author\" content=\""+uuid+"\"><title>"+tmp.getName()+"</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+mContext.get().getString(R.string.media_warning)+"</p>"+"<p>Put a look at this "+"<a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + getMimeType(tmpUri.toString()).substring(1 + getMimeType(tmpUri.toString()).indexOf("/")) + " "+getMimeType(tmpUri.toString()).substring(0,getMimeType(tmpUri.toString()).indexOf("/"))+"</b></a>!"+"</p>"+(!signature.isEmpty()?"<p>"+mContext.get().getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"")+"<p><object style=\"width:100%;\" data=\""+tmpUri.toString()+"\" type=\""+getMimeType(tmpUri.toString())+"\" /></p></body></html>";
                        if(outgoing) newMex = "#outgoing "+newMex;
                        if(!mArchive.contains(newMex)) {
                            mArchive.add(newMex);
                            ((ArchiveActivity)mContext.get()).mHashCodes.put(mArchive.size()-1, Arrays.hashCode(message.getContent()));
                        }
                    } else {
                        String newMex = "<html><head><meta name=\"author\" content=\""+uuid+"\"><title>" + tmp.getName() + "</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+mContext.get().getString(R.string.media_warning)+"</p><p>Put a look at this <a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + getMimeType(tmpUri.toString()).substring(1 + getMimeType(tmpUri.toString()).indexOf("/")) + " "+getMimeType(tmpUri.toString()).substring(0,getMimeType(tmpUri.toString()).indexOf("/"))+"</b></a>!</p>"+(!signature.isEmpty()?"<p>"+mContext.get().getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"")+"</body></html>";
                        if(outgoing) newMex = "#outgoing "+newMex;
                        if(!mArchive.contains(newMex)) {
                            mArchive.add(newMex);
                            ((ArchiveActivity)mContext.get()).mHashCodes.put(mArchive.size()-1, Arrays.hashCode(message.getContent()));
                        }
                    }

                } catch (IOException e) {
                    Date mexDateTimeObj = new Date();
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                    }
                    mArchive.add(String.format(mContext.get().getString(R.string.file_missed),datetime));
                }
            }
            else if (mimeType != null && mimeType.startsWith("application/pdf")) {
                try {

                    File tmp = File.createTempFile(mContext.get().getString(R.string.incoming_file_prefix), message.getType().substring(message.getType().lastIndexOf(".")), mContext.get().getCacheDir());
                    FileOutputStream tmpos = new FileOutputStream(tmp);
                    tmpos.write(message.getContent());
                    tmpos.flush();
                    tmpos.close();

                    // String uuid = message.getType().substring(0,message.getType().indexOf("."));

                    /*DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                    DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                    String datetime = "<div class=\"datetime\">"+dateFormat.format(new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf(".")))))+" "+timeFormat.format(new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf(".")))))+"</div>";
*/
                    Date mexDateTimeObj = new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf("."))));
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>";
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj)+"</div>";
                    }

                    Uri tmpUri = getUriForFile(mContext.get(), "it.mircosoderi.fileprovider", tmp);
                    mContext.get().grantUriPermission(mContext.get().getPackageName(), tmpUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    String newMex = "<html><head><meta name=\"author\" content=\""+uuid+"\"><title>" + tmp.getName() + "</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+mContext.get().getString(R.string.media_warning)+"</p><p>Put a look at this <a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>PDF document</b></a>!</p>"+(!signature.isEmpty()?"<p>"+mContext.get().getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"")+"</body></html>";
                    if(outgoing) newMex = "#outgoing "+newMex;
                    if(!mArchive.contains(newMex)) {
                        mArchive.add(newMex);
                        ((ArchiveActivity)mContext.get()).mHashCodes.put(mArchive.size()-1, Arrays.hashCode(message.getContent()));
                    }


                } catch (IOException e) {
                    Date mexDateTimeObj = new Date();
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                    }
                    mArchive.add(String.format(mContext.get().getString(R.string.file_missed),datetime));
                }
            }
            else if (mimeType != null && mimeType.startsWith("image/")) {
                try {

                    File tmp = File.createTempFile(mContext.get().getString(R.string.incoming_file_prefix), message.getType().substring(message.getType().lastIndexOf(".")), mContext.get().getCacheDir());
                    FileOutputStream tmpos = new FileOutputStream(tmp);
                    tmpos.write(message.getContent());
                    tmpos.flush();
                    tmpos.close();

                    // String uuid = message.getType().substring(0,message.getType().indexOf("."));

                    /*DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                    DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                    String datetime = "<div class=\"datetime\">"+dateFormat.format(new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf(".")))))+" "+timeFormat.format(new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf(".")))))+"</div>";
*/
                    Date mexDateTimeObj = new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf("."))));
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>";
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj)+"</div>";
                    }

                    Uri tmpUri = getUriForFile(mContext.get(), "it.mircosoderi.fileprovider", tmp);
                    mContext.get().grantUriPermission(mContext.get().getPackageName(), tmpUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext.get());
                    if (getMimeType(tmpUri.toString()).startsWith("image/") && sharedPref.getBoolean(SettingsFragment.INLINE_IMAGES, true)) {
                        String newMex = "<html><head><meta name=\"author\" content=\""+uuid+"\"><title>"+tmp.getName()+"</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+mContext.get().getString(R.string.media_warning)+"</p>"+"<p>Put a look at this "+"<a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + getMimeType(tmpUri.toString()).substring(1 + getMimeType(tmpUri.toString()).indexOf("/")) + " "+getMimeType(tmpUri.toString()).substring(0,getMimeType(tmpUri.toString()).indexOf("/"))+"</b></a>!"+"</p>"+(!signature.isEmpty()?"<p>"+mContext.get().getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"")+"<p><object style=\"width:100%;\" data=\""+tmpUri.toString()+"\" type=\""+getMimeType(tmpUri.toString())+"\" /></p></body></html>";
                        if(outgoing) newMex = "#outgoing "+newMex;
                        if(!mArchive.contains(newMex)) {
                            mArchive.add(newMex);
                            ((ArchiveActivity)mContext.get()).mHashCodes.put(mArchive.size()-1, Arrays.hashCode(message.getContent()));
                        }
                    } else {
                        String newMex = "<html><head><meta name=\"author\" content=\""+uuid+"\"><title>" + tmp.getName() + "</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+mContext.get().getString(R.string.media_warning)+"</p><p>Put a look at this <a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + getMimeType(tmpUri.toString()).substring(1 + getMimeType(tmpUri.toString()).indexOf("/")) + " "+getMimeType(tmpUri.toString()).substring(0,getMimeType(tmpUri.toString()).indexOf("/"))+"</b></a>!</p>"+(!signature.isEmpty()?"<p>"+mContext.get().getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"")+"</body></html>";
                        if(outgoing) newMex = "#outgoing "+newMex;
                        if(!mArchive.contains(newMex)) {
                            mArchive.add(newMex);
                            ((ArchiveActivity)mContext.get()).mHashCodes.put(mArchive.size()-1, Arrays.hashCode(message.getContent()));
                        }
                    }

                } catch (IOException e) {
                    Date mexDateTimeObj = new Date();
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                    }
                    mArchive.add(String.format(mContext.get().getString(R.string.file_missed),datetime));
                }
            }
            else {
                try {

                    File tmptmp = File.createTempFile(mContext.get().getString(R.string.incoming_file_prefix), message.getType().substring(message.getType().lastIndexOf(".")), mContext.get().getCacheDir());
                    FileOutputStream tmpos = new FileOutputStream(tmptmp);
                    tmpos.write(message.getContent());
                    tmpos.flush();
                    tmpos.close();
                    String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                    if(ext == null) ext = message.getType().substring(1+message.getType().lastIndexOf("."));
                    if( (!ext.isEmpty()) && (!ext.startsWith("."))) ext = "."+ext;
                    File tmp;
                    if(!"zip".equals(ext)) {
                        tmp = File.createTempFile(tmptmp.getName(), ".zip", mContext.get().getCacheDir());
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

                    // String uuid = message.getType().substring(0,message.getType().indexOf("."));

                /*DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                String datetime = "<div class=\"datetime\">"+dateFormat.format(new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf(".")))))+" "+timeFormat.format(new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf(".")))))+"</div>";
*/
                    Date mexDateTimeObj = new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf("."))));
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>";
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj)+"</div>";
                    }

                    Uri tmpUri = getUriForFile(mContext.get(), "it.mircosoderi.fileprovider", tmp);
                    mContext.get().grantUriPermission(mContext.get().getPackageName(), tmpUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    String newMex = "<html><head><meta name=\"author\" content=\""+uuid+"\"><title>" + tmp.getName() + "</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+mContext.get().getString(R.string.media_warning)+"</p><p>Put a look at files in this <a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>archive</b></a>!</p>"+(!signature.isEmpty()?"<p>"+mContext.get().getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"")+"</body></html>";
                    if(outgoing) newMex = "#outgoing "+newMex;
                    if(!mArchive.contains(newMex)) {
                        mArchive.add(newMex);
                        ((ArchiveActivity)mContext.get()).mHashCodes.put(mArchive.size()-1, Arrays.hashCode(message.getContent()));
                    }


                } catch (IOException e) {
                    Date mexDateTimeObj = new Date();
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                    }
                    mArchive.add(String.format(mContext.get().getString(R.string.file_missed),datetime));
                }

            }

        }

        private String getMimeType(String url) {
            String type = null;
            String extension = MimeTypeMap.getFileExtensionFromUrl(url);
            if (extension != null) {
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            }
            return type;
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        new Update(this).execute();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        AppDatabase db = Room.databaseBuilder(this,
                AppDatabase.class, "crushare").allowMainThreadQueries().fallbackToDestructiveMigration().build();
        if(db.archivedMessageDao().getArchiveSize() > 0) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.archive, menu);
            return true;
        }
        else {
            return false;
        }
    }

    public static class Delete {

        WeakReference<Context> mContext;

        Delete(Context context) {
            mContext = new WeakReference<>(context);
        }

        void execute() {
            AppDatabase db = Room.databaseBuilder(mContext.get(),
                    AppDatabase.class, "crushare").allowMainThreadQueries().fallbackToDestructiveMigration().build();
            db.archivedMessageDao().clear();
            mArchive.clear();
            ((ArchiveActivity)mContext.get()).mHashCodes.clear();
            Date mexDateTimeObj = new Date();
            String datetime;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
            }
            else {
                DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
                DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext.get());
                datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
            }
            add(new BigMessage(String.format(mContext.get().getString(R.string.archive_is_empty),datetime).getBytes(),"text/plain"));
            db.close();
            ((ArchiveActivity)mContext.get()).mAdapter.notifyDataSetChanged();
        }

        private void add(Message message) {
            String txt = new String(message.getContent()).trim();
            if(!DetectHtml.isHtml(txt)) txt = txt.replace("\n","<br>");
            if(txt.contains("<html>") && txt.contains("<head>") && txt.contains("</head>") && txt.contains("</html>")) {
                txt = txt.substring(0, txt.indexOf("</head>")) + "<link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/>" + txt.substring(txt.indexOf("</head>"));
            }
            else {
                txt = "<!DOCTYPE html>\n<html><head><title>Message</title><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+txt+"</body></html>";
            }
            if(!mArchive.contains(txt)) {
                mArchive.add(txt);
                ((ArchiveActivity)mContext.get()).mHashCodes.put(mArchive.size()-1, Arrays.hashCode(message.getContent()));
            }
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_clear:
                new Delete(this).execute();
                invalidateOptionsMenu();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void hide(int position) {
        mArchive.remove(position);
        SparseIntArray newIncomingHashCodes = new SparseIntArray();
        for(int i = 0; i < mHashCodes.size(); i++) {
            if(mHashCodes.keyAt(i) < position) {
                newIncomingHashCodes.put(mHashCodes.keyAt(i), mHashCodes.valueAt(i));
            }
            else if(mHashCodes.keyAt(i) > position) {
                newIncomingHashCodes.put(mHashCodes.keyAt(i)-1, mHashCodes.valueAt(i));
            }
        }
        mHashCodes = newIncomingHashCodes;
        mAdapter.notifyDataSetChanged();
    }

}
