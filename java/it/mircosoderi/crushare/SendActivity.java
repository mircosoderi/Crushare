package it.mircosoderi.crushare;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.PublishCallback;
import com.google.android.gms.nearby.messages.PublishOptions;
import com.google.android.gms.nearby.messages.Strategy;
import com.google.android.gms.tasks.OnFailureListener;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static android.support.v4.content.FileProvider.getUriForFile;

public class SendActivity extends AppCompatActivity implements SendConfirmationFragment.SendConfirmationFragmentInteractionListener {

    String mType;
    String mText;
    Uri mMediaUri;
    Message mMessage;
    boolean mFailure = false;
    String mExceptionMessage;
    //int sizeLimit = 100000;
    String htmlPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        setContentView(R.layout.activity_send);

    }

    @Override
    public void onStart() {
        super.onStart();

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String uuid = sharedPref.getString(SettingsFragment.INSTANCE_ID, UUID.randomUUID().toString());
        Intent intent = getIntent();
        String action = intent.getAction();
        mType = intent.getType();
        if (Intent.ACTION_SEND.equals(action) || getString(R.string.send_file).toUpperCase().equals(action) || getString(R.string.send_message).equals(action)) {
            if ("text/plain".equals(mType)) {
                mText = "";
                if (intent.getStringExtra(Intent.EXTRA_TEXT) != null)
                    mText = intent.getStringExtra(Intent.EXTRA_TEXT).trim();
                if(intent.getParcelableExtra(Intent.EXTRA_STREAM) != null) {
                    try {
                        mMediaUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                        grantUriPermission(getPackageName(), mMediaUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        InputStream iStream = getContentResolver().openInputStream(mMediaUri);
                        mText = new String(getBytes(iStream));
                    }
                    catch(Exception e) {
                        int messageID = R.string.error_file_not_found;
                        if(e instanceof TooOld) messageID = R.string.too_old;
                        Intent main = new Intent(this, MainActivity.class);
                        Date mexDateTimeObj = new Date();
                        String datetime;
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                            DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                            String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                            datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                        }
                        else {
                            DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                            DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
                            datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                        }
                        main.putExtra("outgoing", String.format(getString(messageID),datetime));
                        main.putExtra("message",new Message(String.format(getString(messageID),datetime).getBytes(), "text/plain)"));
                        startActivity(main);
                        finish();
                    }

                }
                Pattern p = Pattern.compile("<(.*?)>");
                Matcher m = p.matcher(mText);
                while (m.find()) {
                    mText = mText.replace("<" + m.group(1) + ">", "<" + m.group(1).toLowerCase() + ">");
                }
                if (!DetectHtml.isHtml(mText)) mText = mText.replace("\n", "<br>");
                if (mText.contains("<html>") && mText.contains("<head>") && mText.contains("</head>") && mText.contains("</html>") && mText.contains("<body>") && mText.contains("</body>")) {
                    mText = mText.substring(0, mText.indexOf("</head>")) + "<meta name=\"author\" content=\"" + uuid + "\"><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/>" + mText.substring(mText.indexOf("</head>"));
                } else {
                    Date mexDateTimeObj = new Date();
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(SendActivity.this);
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>";
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(SendActivity.this);
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(SendActivity.this);
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj)+"</div>";
                    }
                    mText = "<!DOCTYPE html>\n<html><head><meta name=\"author\" content=\"" + uuid + "\"><title>Message</title><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>" + datetime + mText + "</body></html>";
                }
                final String signature = sharedPref.getString(SettingsFragment.SIGNATURE, getString(R.string.signature_default));
                mText = mText.replace("</body>", (!signature.isEmpty() ? "<p class=\"signature\">" + getString(R.string.signed) + ",<br><span style=\"font-style:italic;\">" + signature + "</span></p>" : "") + "</body>");
                //if(mText.length() < sizeLimit) {
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                SendConfirmationFragment frag = SendConfirmationFragment.newInstance(mText,mText.length());
                ft.add(R.id.send, frag);
                ft.commit();
                /*}
                else {
                    Intent main = new Intent(this, MainActivity.class);
                    Date mexDateTimeObj = new Date();
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                    }
                    main.putExtra("outgoing",String.format(getString(R.string.rejected_too_long),datetime));
                    main.putExtra("message",new Message(String.format(getString(R.string.rejected_too_long),datetime).getBytes(), "text/plain)"));
                    startActivity(main);
                    finish();
                }*/
            } else if (mType != null && mType.startsWith("video/")) {
                try {
                    mMediaUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    if(mMediaUri == null) throw new Exception("File not found.");
                    grantUriPermission(getPackageName(), mMediaUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    String mimeType = "application/octet-stream";
                    Cursor cursor = getContentResolver().query(mMediaUri, null, null, null, null);
                    if(cursor == null) {
                        if(mMediaUri.toString().startsWith("file://")) {
                            throw new TooOld("Too old!");
                        }
                    }
                    if (cursor != null) {
                        cursor.moveToFirst();
                        try {
                            mimeType = cursor.getString(cursor.getColumnIndex("mime_type"));
                        } catch (Exception e) {
                            mimeType = "application/octet-stream";
                        }
                        cursor.close();
                    }
                    InputStream iStream = getContentResolver().openInputStream(mMediaUri);
                    if (iStream != null) {
                        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                        File tmp = File.createTempFile(getString(R.string.incoming_file_prefix), "."+ext, getCacheDir());
                        FileOutputStream tmpos = new FileOutputStream(tmp);
                        tmpos.write(getBytes(iStream));
                        tmpos.flush();
                        tmpos.close();
                        Uri tmpUri = getUriForFile(this, "it.mircosoderi.fileprovider", tmp);
                        grantUriPermission(getPackageName(), tmpUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        final String signature = sharedPref.getString(SettingsFragment.SIGNATURE, getString(R.string.signature_default));
                        Date mexDateTimeObj = new Date();
                        String datetime;
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                            DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(SendActivity.this);
                            String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                            datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>";
                        }
                        else {
                            DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(SendActivity.this);
                            DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(SendActivity.this);
                            datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj)+"</div>";
                        }
                        if (sharedPref.getBoolean(SettingsFragment.INLINE_VIDEOS, true)) {
                            htmlPreview = "<html><head><title>" + tmp.getName() + "</title><meta name=\"author\" content=\""+uuid+"\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5;\">"+getString(R.string.media_warning)+"</p>"+"<p>Put a look at this " + "<a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + mimeType.substring(1 + mimeType.indexOf("/")) + " " + mimeType.substring(0, mimeType.indexOf("/")) + "</b></a>!" + "</p>" + (!signature.isEmpty()?"<p>"+getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"") + "<p><object style=\"max-width:100%; height:auto; border: thin dotted #3F51B5;\" data=\"" + tmpUri.toString() + "\" type=\""+mimeType+"\"><param name=\"autoplay\" value=\"false\" ><param name=\"loop\" value=\"false\" ></object></p></body></html>";
                        } else {
                            htmlPreview = "<html><head><title>" + tmp.getName() + "</title><meta name=\"author\" content=\""+uuid+"\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5;\">"+getString(R.string.media_warning)+"</p>"+"<p>Put a look at this " + "<a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + mimeType.substring(1 + mimeType.indexOf("/")) + " " + mimeType.substring(0, mimeType.indexOf("/")) + "</b></a>!" + "</p>" + (!signature.isEmpty()?"<p>"+getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"") + "</body></html>";
                        }
                        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                        SendConfirmationFragment frag = SendConfirmationFragment.newInstance(htmlPreview,tmp.length());
                        ft.add(R.id.send, frag);
                        ft.commit();
                    }

                }
                catch(Exception e) {
                    int messageID = R.string.error_file_not_found;
                    if(e instanceof TooOld) messageID = R.string.too_old;
                    Intent main = new Intent(this, MainActivity.class);
                    Date mexDateTimeObj = new Date();
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                    }
                    main.putExtra("outgoing", String.format(getString(messageID),datetime));
                    main.putExtra("message",new Message(String.format(getString(messageID),datetime).getBytes(), "text/plain)"));
                    startActivity(main);
                    finish();
                }


            } else if (mType != null && "application/pdf".equals(mType)) {
                try {
                    mMediaUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    if (mMediaUri == null) throw new Exception("File not found.");
                    grantUriPermission(getPackageName(), mMediaUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    String mimeType = "application/pdf";
                    InputStream iStream = getContentResolver().openInputStream(mMediaUri);
                    if (iStream != null) {
                        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                        File tmp = File.createTempFile(getString(R.string.incoming_file_prefix), "." + ext, getCacheDir());
                        FileOutputStream tmpos = new FileOutputStream(tmp);
                        tmpos.write(getBytes(iStream));
                        tmpos.flush();
                        tmpos.close();
                        Uri tmpUri = getUriForFile(this, "it.mircosoderi.fileprovider", tmp);
                        grantUriPermission(getPackageName(), tmpUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        final String signature = sharedPref.getString(SettingsFragment.SIGNATURE, getString(R.string.signature_default));
                        Date mexDateTimeObj = new Date();
                        String datetime;
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                            DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(SendActivity.this);
                            String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                            datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>";
                        }
                        else {
                            DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(SendActivity.this);
                            DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(SendActivity.this);
                            datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj)+"</div>";
                        }
                        htmlPreview = "<html><head><title>" + tmp.getName() + "</title><meta name=\"author\" content=\"" + uuid + "\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>" + datetime + "<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5;\">" + getString(R.string.media_warning) + "</p>" + "<p>Put a look at this " + "<a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>PDF document</b></a>!" + "</p>" + (!signature.isEmpty() ? "<p>" + getString(R.string.signed) + ",<br><span style=\"font-style:italic;\">" + signature + "</span></p>" : "") + "</body></html>";
                        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                        SendConfirmationFragment frag = SendConfirmationFragment.newInstance(htmlPreview,tmp.length());
                        ft.add(R.id.send, frag);
                        ft.commit();
                    }
                } catch (Exception e) {
                    int messageID = R.string.error_file_not_found;
                    if (e instanceof TooOld) messageID = R.string.too_old;
                    Intent main = new Intent(this, MainActivity.class);
                    Date mexDateTimeObj = new Date();
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = dateFormat.format(mexDateTimeObj) + " " + android.text.format.DateFormat.format(timeFormat, mexDateTimeObj);
                    } else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
                        datetime = dateFormat.format(mexDateTimeObj) + " " + timeFormat.format(mexDateTimeObj);
                    }
                    main.putExtra("outgoing", String.format(getString(messageID), datetime));
                    main.putExtra("message", new Message(String.format(getString(messageID), datetime).getBytes(), "text/plain)"));
                    startActivity(main);
                    finish();
                }
            } else if (mType != null && mType.startsWith("image/")) {

                try {

                    mMediaUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);

                    if(mMediaUri == null) throw new Exception("File not found.");

                    grantUriPermission(getPackageName(), mMediaUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    //String size = "0";
                    String mimeType = "application/octet-stream";
                    Cursor cursor = getContentResolver().query(mMediaUri, null, null, null, null);
                    if(cursor == null) {
                       if(mMediaUri.toString().startsWith("file://")) {
                           throw new TooOld("Too old!");
                       }
                    }
                    if (cursor != null) {
                        cursor.moveToFirst();
                        /*try {
                            size = cursor.getString(cursor.getColumnIndex("_size"));
                        } catch (Exception e) {
                            size = "0";
                        }*/
                        try {
                            mimeType = cursor.getString(cursor.getColumnIndex("mime_type"));
                        } catch (Exception e) {
                            mimeType = "application/octet-stream";
                        }
                        cursor.close();
                    }

                    InputStream iStream = getContentResolver().openInputStream(mMediaUri);

                    if (iStream != null) {
                        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                        File tmp = File.createTempFile(getString(R.string.incoming_file_prefix), "."+ext, getCacheDir());
                        FileOutputStream tmpos = new FileOutputStream(tmp);
                        tmpos.write(getBytes(iStream));
                        tmpos.flush();
                        tmpos.close();
                        Uri tmpUri = getUriForFile(this, "it.mircosoderi.fileprovider", tmp);
                        grantUriPermission(getPackageName(), tmpUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        final String signature = sharedPref.getString(SettingsFragment.SIGNATURE, getString(R.string.signature_default));
                        Date mexDateTimeObj = new Date();
                        String datetime;
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                            DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(SendActivity.this);
                            String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                            datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>";
                        }
                        else {
                            DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(SendActivity.this);
                            DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(SendActivity.this);
                            datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj)+"</div>";
                        }
                        if (mimeType.startsWith("image/") && sharedPref.getBoolean(SettingsFragment.INLINE_IMAGES, true)) {
                            htmlPreview = "<html><head><title>" + tmp.getName() + "</title><meta name=\"author\" content=\""+uuid+"\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5;\">"+getString(R.string.media_warning)+"</p>"+"<p>Put a look at this " + "<a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + mimeType.substring(1 + mimeType.indexOf("/")) + " " + mimeType.substring(0, mimeType.indexOf("/")) + "</b></a>!" + "</p>" + (!signature.isEmpty()?"<p>"+getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"") + "<p><object style=\"max-width:100%; height:auto;\" data=\"" + tmpUri.toString() + "\" type=\"" + mimeType + "\" /></p></body></html>";
                        } else {
                            htmlPreview = "<html><head><title>" + tmp.getName() + "</title><meta name=\"author\" content=\""+uuid+"\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5;\">"+getString(R.string.media_warning)+"</p>"+"<p>Put a look at this <a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + mimeType.substring(1 + mimeType.indexOf("/")) + " " + mimeType.substring(0, mimeType.indexOf("/")) + "</b></a>!</p>"+(!signature.isEmpty()?"<p>"+getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"")+"</body></html>";
                        }
                        //DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
                        //DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                        //htmlPreview = htmlPreview.replace("<body>","<body><div class=\"datetime\">"+dateFormat.format(new Date())+" "+timeFormat.format(new Date())+"</div>");

                        //if (Integer.parseInt(size) < sizeLimit) {
                            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                            SendConfirmationFragment frag = SendConfirmationFragment.newInstance(htmlPreview,tmp.length());
                            ft.add(R.id.send, frag);
                            ft.commit();
                        /*} else {
                            Intent main = new Intent(this, MainActivity.class);
                            Date mexDateTimeObj = new Date();
                            String datetime;
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                                DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                                String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                                datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                            }
                            else {
                                DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                                DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
                                datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                            }
                            main.putExtra("outgoing", String.format(getString(R.string.rejected_too_long),datetime));
                            main.putExtra("message",new Message(String.format(getString(R.string.rejected_too_long),datetime).getBytes(), "text/plain)"));
                            startActivity(main);
                            finish();
                        }*/
                    }

                }
                catch(Exception e) {
                    int messageID = R.string.error_file_not_found;
                    if(e instanceof TooOld) messageID = R.string.too_old;
                    Intent main = new Intent(this, MainActivity.class);
                    Date mexDateTimeObj = new Date();
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                    }
                    main.putExtra("outgoing", String.format(getString(messageID),datetime));
                    main.putExtra("message",new Message(String.format(getString(messageID),datetime).getBytes(), "text/plain)"));
                    startActivity(main);
                    finish();
                }
            }
            else {
                try {
                    mMediaUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    if(mMediaUri == null) throw new Exception("File not found.");
                    grantUriPermission(getPackageName(), mMediaUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    String mimeType = "application/octet-stream";
                    String cData = "";
                    Cursor cursor = getContentResolver().query(mMediaUri, null, null, null, null);
                    if(cursor == null) {
                        if(mMediaUri.toString().startsWith("file://")) {
                            throw new TooOld("Too old!");
                        }
                    }
                    if (cursor != null) {
                        cursor.moveToFirst();
                        try {
                            mimeType = cursor.getString(cursor.getColumnIndex("mime_type"));
                        } catch (Exception e) {
                            mimeType = "application/octet-stream";
                        }
                        try {
                            cData = cursor.getString(cursor.getColumnIndex("_data"));
                            if(cData == null) cData = cursor.getString(cursor.getColumnIndex("_display_name"));
                        } catch (Exception e) {
                            cData = "";
                        }
                        cursor.close();
                    }
                    if(mimeType == null) mimeType = "application/octet-stream";

                    InputStream iStream = getContentResolver().openInputStream(mMediaUri);
                    if (iStream != null) {
                        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                        if(ext == null && cData != null && (!cData.isEmpty()) && cData.lastIndexOf(".") != -1) ext=cData.substring(1+cData.lastIndexOf("."));
                        if( ext != null && (!ext.isEmpty()) && (!ext.startsWith("."))) ext = "."+ext;
                        File tmp = File.createTempFile(getString(R.string.incoming_file_prefix), ext, getCacheDir());
                        FileOutputStream tmpos = new FileOutputStream(tmp);
                        tmpos.write(getBytes(iStream));
                        tmpos.flush();
                        tmpos.close();
                        File ziptmp;
                        if(!"zip".equals(ext)) {
                            ziptmp = File.createTempFile(tmp.getName(), ".zip", getCacheDir());
                            FileOutputStream dest = new FileOutputStream(ziptmp);
                            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));
                            String entryName = tmp.getName();
                            if(cData != null && -1 < cData.lastIndexOf("/")) {
                                entryName = cData.substring(cData.lastIndexOf("/")+1);
                            }
                            ZipEntry entry = new ZipEntry(entryName);
                            out.putNextEntry(entry);
                            FileInputStream fi = new FileInputStream(tmp);
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
                            ziptmp = tmp;
                        }
                        Uri tmpUri = getUriForFile(this, "it.mircosoderi.fileprovider", ziptmp);
                        grantUriPermission(getPackageName(), tmpUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        final String signature = sharedPref.getString(SettingsFragment.SIGNATURE, getString(R.string.signature_default));
                        Date mexDateTimeObj = new Date();
                        String datetime;
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                            DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(SendActivity.this);
                            String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                            datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>";
                        }
                        else {
                            DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(SendActivity.this);
                            DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(SendActivity.this);
                            datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj)+"</div>";
                        }
                        htmlPreview = "<html><head><title>" + tmp.getName() + "</title><meta name=\"author\" content=\""+uuid+"\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5;\">"+getString(R.string.media_warning)+"</p>"+"<p>Put a look at files in this " + "<a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>archive</b></a>!" + "</p>" + (!signature.isEmpty()?"<p>"+getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"") + "</body></html>";
                        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                        SendConfirmationFragment frag = SendConfirmationFragment.newInstance(htmlPreview,tmp.length());
                        ft.add(R.id.send, frag);
                        ft.commit();
                    }

                }
                catch(Exception e) {
                    int messageID = R.string.error_file_not_found;
                    if(e instanceof TooOld) messageID = R.string.too_old;
                    Intent main = new Intent(this, MainActivity.class);
                    Date mexDateTimeObj = new Date();
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                    }
                    main.putExtra("outgoing", String.format(getString(messageID),datetime));
                    main.putExtra("message",new Message(String.format(getString(messageID),datetime).getBytes(), "text/plain)"));
                    startActivity(main);
                    finish();
                }
            }

        }
        else if(getString(R.string.send_file).equals(action)) {
            Intent sendFileIntent = new Intent(Intent.ACTION_GET_CONTENT);
            sendFileIntent.addCategory(Intent.CATEGORY_OPENABLE);
            sendFileIntent.setType("*/*");
            startActivityForResult(sendFileIntent, 42);
        }
        else {
            finish();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {
        if (requestCode == 42 && resultCode == Activity.RESULT_OK) {
            Uri uri;
            if (resultData != null) {
                uri = resultData.getData();
                if(uri != null) {
                    Intent intent = new Intent(this, SendActivity.class);
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.setAction(getString(R.string.send_file).toUpperCase());
                    ContentResolver cr = getContentResolver();
                    intent.setType(cr.getType(uri));
                    startActivity(intent);
                    finish();
                }
            }
        }
    }

    @Override
    public void onSendConfirmed() {
        if ("text/plain".equals(mType)) {
            PublishOptions options = new PublishOptions.Builder()
                    .setStrategy(Strategy.DEFAULT)
                    .setCallback(new PublishCallback() {
                        @Override
                        public void onExpired() {
                            super.onExpired();
                            finish();
                        }
                    }).build();

            /*
            DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
            DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
            mText = mText.replace("<body>","<body><div class=\"datetime\">"+dateFormat.format(new Date())+" "+timeFormat.format(new Date())+"</div>");
            */
            Date mexDateTimeObj = new Date();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                if(mText.contains("<div class=\"datetime\">")) {
                    mText = mText.replace(mText.substring(mText.indexOf("<div class=\"datetime\">"),mText.indexOf("</div>",mText.indexOf("<div class=\"datetime\">"))),"<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj));
                }
                else {
                    mText = mText.replace("<body>","<body><div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>");
                }

            }
            else {
                DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
                if(mText.contains("<div class=\"datetime\">")) {
                    mText = mText.replace(mText.substring(mText.indexOf("<div class=\"datetime\">"),mText.indexOf("</div>",mText.indexOf("<div class=\"datetime\">"))), "<div class=\"datetime\">" + dateFormat.format(mexDateTimeObj) + " " + timeFormat.format(mexDateTimeObj) );
                }
                else {
                    mText = mText.replace("<body>", "<body><div class=\"datetime\">" + dateFormat.format(mexDateTimeObj) + " " + timeFormat.format(mexDateTimeObj) + "</div>");
                }
            }

            if(mText.length() < 80000) mMessage = new Message(mText.getBytes(),"text/plain");
            else mMessage = new BigMessage(mText.getBytes(),"text/plain");
            MainActivity.displayed.add(String.valueOf(Arrays.hashCode(mMessage.getContent())));

            if(mMessage.getContent().length < 80000) {
                Nearby.getMessagesClient(this)
                    .publish(mMessage, options)
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            mExceptionMessage = e.getMessage();
                            mFailure = true;
                        }
                    });
            }
            else {
                if(mMessage.getContent().length < 10000000)
                    AsyncTask.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                int totalSize = mMessage.getContent().length;
                                int deliveredBytes = 0;
                                int deliveredMessages = 0;
                                Random r = new Random();
                                while (totalSize > deliveredBytes) {
                                    int chunkSize = 50000+(deliveredMessages*10000%50000);
                                    String chunkMime;
                                    if(deliveredBytes+chunkSize>totalSize) {
                                        chunkSize=totalSize-deliveredBytes;
                                        chunkMime = "##"+String.valueOf(deliveredMessages)+" "+mMessage.getType();
                                    }
                                    else {
                                        chunkMime = "#"+String.valueOf(deliveredMessages)+" "+mMessage.getType();
                                    }
                                    byte[] chunk = Arrays.copyOfRange(mMessage.getContent(),deliveredBytes,chunkSize);
                                    PublishOptions options = new PublishOptions.Builder()
                                            .setStrategy(Strategy.DEFAULT)
                                            .setCallback(new PublishCallback() {
                                                @Override
                                                public void onExpired() {
                                                    super.onExpired();
                                                    finish();
                                                }
                                            }).build();
                                    Nearby.getMessagesClient(SendActivity.this)
                                            .publish(new Message(chunk,chunkMime), options)
                                            .addOnFailureListener(new OnFailureListener() {
                                                @Override
                                                public void onFailure(@NonNull Exception e) {
                                                    mExceptionMessage = e.getMessage();
                                                    mFailure = true;
                                                }
                                            });
                                    deliveredBytes+=chunkSize;
                                    deliveredMessages++;
                                    int sleepTime = r.nextInt(3000)+1000;
                                    Thread.sleep(sleepTime);
                                }
                            }
                            catch(Exception e) {
                                Toast.makeText(SendActivity.this, getString(R.string.bigsized_delivery_error),Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                );
            }

            if(!mFailure) {
                new AsyncDoArchiveMessage(this,mMessage,new Date().getTime(), true, null, null).execute();
                if(900000 > mText.getBytes().length + mMessage.getContent().length+mMessage.getType().length()+4) {
                    Intent main = new Intent(this, MainActivity.class);
                    main.putExtra("outgoing",mText);
                    main.putExtra("message",mMessage);
                    main.putExtra("bigMessageHash",Arrays.hashCode(mMessage.getContent()));
                    startActivity(main);
                }
                else {
                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
                    final int delay = Integer.parseInt(sharedPref.getString(SettingsFragment.DELIVERY_INTERVAL, "10"));
                    final int attempts = Integer.parseInt(sharedPref.getString(SettingsFragment.DELIVERY_ATTEMPTS, "2"));
                    new Postman(this, mMessage, delay, attempts-1).run();
                    Intent main = new Intent(this, MainActivity.class);
                    main.putExtra("outgoing",mText);
                    main.putExtra("bigMessageHash",Arrays.hashCode(mMessage.getContent()));
                    startActivity(main);
                }
                finish();
            }
            else {
                Intent main = new Intent(this, MainActivity.class);
                mexDateTimeObj = new Date();
                String datetime;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                    String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                    datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                }
                else {
                    DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                    DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
                    datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                }
                main.putExtra("outgoing",String.format(getString(R.string.error),datetime,mExceptionMessage));
                main.putExtra("message", new Message(String.format(getString(R.string.error),datetime,mExceptionMessage).getBytes(),"text/plain"));
                startActivity(main);
                finish();
            }
        }
        else {
            try {
                InputStream iStream = getContentResolver().openInputStream(mMediaUri);
                if(iStream == null) throw new Exception("Send Error!");
                byte[] media = getBytes(iStream);
                PublishOptions options = new PublishOptions.Builder()
                        .setStrategy(Strategy.DEFAULT)
                        .setCallback(new PublishCallback() {
                            @Override
                            public void onExpired() {
                                super.onExpired();
                                finish();
                            }
                        }).build();
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
                String uuid = sharedPref.getString(SettingsFragment.INSTANCE_ID, UUID.randomUUID().toString());
                Date mexDateTimeObj = new Date();

                if(media.length < 80000) mMessage = new Message(media,uuid.substring(0,uuid.indexOf("-"))+"."+mexDateTimeObj.getTime()+getFileName(mMediaUri).substring(getFileName(mMediaUri).lastIndexOf(".")));
                else mMessage = new BigMessage(media,uuid.substring(0,uuid.indexOf("-"))+"."+mexDateTimeObj.getTime()+getFileName(mMediaUri).substring(getFileName(mMediaUri).lastIndexOf(".")));
                MainActivity.displayed.add(String.valueOf(Arrays.hashCode(mMessage.getContent())));
                final String signature = uuid+sharedPref.getString(SettingsFragment.SIGNATURE, getString(R.string.signature_default));
                Nearby.getMessagesClient(this)
                        .publish(new Message(signature.getBytes(), "text/sign "+uuid.substring(0,uuid.indexOf("-"))+" "+String.valueOf(Arrays.hashCode(mMessage.getContent()))),options);
                if(mMessage.getContent().length < 80000) {
                    Nearby.getMessagesClient(this)
                            .publish(mMessage, options)
                            .addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    mExceptionMessage = e.getMessage();
                                    mFailure = true;
                                }
                            });
                }
                else {
                    if(mMessage.getContent().length < 10000000)
                    AsyncTask.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                int totalSize = mMessage.getContent().length;
                                int deliveredBytes = 0;
                                int deliveredMessages = 0;
                                Random r = new Random();
                                while (totalSize > deliveredBytes) {
                                    int chunkSize = 50000+(deliveredMessages*10000%50000);
                                    String chunkMime;
                                    if(deliveredBytes+chunkSize>totalSize) {
                                        chunkSize=totalSize-deliveredBytes;
                                        chunkMime = "##"+String.valueOf(deliveredMessages)+" "+mMessage.getType();
                                    }
                                    else {
                                        chunkMime = "#"+String.valueOf(deliveredMessages)+" "+mMessage.getType();
                                    }
                                    byte[] chunk = Arrays.copyOfRange(mMessage.getContent(),deliveredBytes,deliveredBytes+chunkSize);
                                    PublishOptions options = new PublishOptions.Builder()
                                            .setStrategy(Strategy.DEFAULT)
                                            .setCallback(new PublishCallback() {
                                                @Override
                                                public void onExpired() {
                                                    super.onExpired();
                                                    finish();
                                                }
                                            }).build();
                                    Nearby.getMessagesClient(SendActivity.this)
                                            .publish(new Message(chunk,chunkMime), options)
                                            .addOnFailureListener(new OnFailureListener() {
                                                @Override
                                                public void onFailure(@NonNull Exception e) {
                                                    mExceptionMessage = e.getMessage();
                                                    mFailure = true;
                                                }
                                            });
                                    deliveredBytes+=chunkSize;
                                    deliveredMessages++;
                                    Thread.sleep(r.nextInt(1000) + 1000);
                                    // also repeat the delivery of signature
                                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(SendActivity.this);
                                    String uuid = sharedPref.getString(SettingsFragment.INSTANCE_ID, UUID.randomUUID().toString());
                                    Nearby.getMessagesClient(SendActivity.this)
                                            .publish(new Message(signature.getBytes(), "text/sign "+uuid.substring(0,uuid.indexOf("-"))+" "+String.valueOf(Arrays.hashCode(mMessage.getContent()))),options);
                                    //
                                    int sleepTime = r.nextInt(3000)+1000;
                                    Thread.sleep(sleepTime);
                                }
                            }
                            catch(Exception e) {
                                mFailure = true;
                                Toast.makeText(SendActivity.this, getString(R.string.bigsized_delivery_error),Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
                if(!mFailure) {
                    new AsyncDoArchiveMessage(this,mMessage,new Date().getTime(), true, (signature+" ").substring(36).trim(),(signature+" ").substring(0,36)).execute();
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        if(htmlPreview.contains("<div class=\"datetime\">")) {
                            htmlPreview = htmlPreview.replace(htmlPreview.substring(htmlPreview.indexOf("<div class=\"datetime\">"),htmlPreview.indexOf("</div>",htmlPreview.indexOf("<div class=\"datetime\">"))),"<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj));
                        }
                        else {
                            htmlPreview = htmlPreview.replace("<body>","<body><div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>");
                        }

                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
                        if(htmlPreview.contains("<div class=\"datetime\">")) {
                            htmlPreview = htmlPreview.replace(htmlPreview.substring(htmlPreview.indexOf("<div class=\"datetime\">"),htmlPreview.indexOf("</div>",htmlPreview.indexOf("<div class=\"datetime\">"))), "<div class=\"datetime\">" + dateFormat.format(mexDateTimeObj) + " " + timeFormat.format(mexDateTimeObj) );
                        }
                        else {
                            htmlPreview = htmlPreview.replace("<body>", "<body><div class=\"datetime\">" + dateFormat.format(mexDateTimeObj) + " " + timeFormat.format(mexDateTimeObj) + "</div>");
                        }
                    }

                    if(900000 > htmlPreview.getBytes().length + 4 + mMessage.getType().getBytes().length + signature.getBytes().length + ("text/sign " + uuid.substring(0, uuid.indexOf("-")) + " " + String.valueOf(Arrays.hashCode(mMessage.getContent()))).getBytes().length + mMessage.getContent().length ) {
                        Intent main = new Intent(this, MainActivity.class);
                        main.putExtra("outgoing", htmlPreview);
                        main.putExtra("bigMessageContentHash", Arrays.hashCode(mMessage.getContent()));
                        main.putExtra("bigMessageType", mMessage.getType());
                        main.putExtra("signature", new Message(signature.getBytes(), "text/sign " + uuid.substring(0, uuid.indexOf("-")) + " " + String.valueOf(Arrays.hashCode(mMessage.getContent()))));
                        main.putExtra("bigMessageContent", mMessage.getContent());
                        startActivity(main);
                    }
                    else {
                        SharedPreferences sharedPrf = PreferenceManager.getDefaultSharedPreferences(this);
                        final int delay = Integer.parseInt(sharedPrf.getString(SettingsFragment.DELIVERY_INTERVAL, "10"));
                        final int attempts = Integer.parseInt(sharedPrf.getString(SettingsFragment.DELIVERY_ATTEMPTS, "2"));
                        new Postman(this, mMessage, delay, attempts-1).run();
                        Intent main = new Intent(this, MainActivity.class);
                        main.putExtra("outgoing", htmlPreview);
                        main.putExtra("bigMessageContentHash", Arrays.hashCode(mMessage.getContent()));
                        startActivity(main);
                    }
                    finish();
                }
                else {
                    Intent main = new Intent(this, MainActivity.class);
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                    }
                    main.putExtra("outgoing",String.format(getString(R.string.error),datetime,mExceptionMessage));
                    main.putExtra("message", new Message(String.format(getString(R.string.error),datetime,mExceptionMessage).getBytes(),"text/plain"));
                    startActivity(main);
                    finish();
                }
            }
            catch(Exception e) {
                Intent main = new Intent(this, MainActivity.class);
                Date mexDateTimeObj = new Date();
                String datetime;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                    String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                    datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                }
                else {
                    DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                    DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
                    datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                }
                main.putExtra("outgoing",String.format(getString(R.string.error),datetime,e.getMessage()));
                main.putExtra("message", new Message(String.format(getString(R.string.error),datetime,e.getMessage()).getBytes(),"text/plain"));
                startActivity(main);
                finish();
            }
        }
    }

    @Override
    public void onSendCanceled(boolean back) {
        finish();
    }

    @Override
    public void onError(String message) {
        Intent main = new Intent(this, MainActivity.class);
        Date mexDateTimeObj = new Date();
        String datetime;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
            DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
            String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
            datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
        }
        else {
            DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
            DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
            datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
        }
        main.putExtra("outgoing",String.format(getString(R.string.error),datetime,message));
        startActivity(main);
        finish();
    }

    @Override
    public void onStop() {
        if(mMessage != null) Nearby.getMessagesClient(this).unpublish(mMessage);
        super.onStop();
    }

    private byte[] getBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];
        int len;
        // long totalLen = 0;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
            // totalLen+=len;
            //if(totalLen > sizeLimit) break;
        }
        return byteBuffer.toByteArray();
    }

    private String getFileName(Uri uri) {
        String result = null;
        String mimeType = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                    mimeType = cursor.getString(cursor.getColumnIndex("mime_type"));
                    cursor.close();
                }
            } catch(Exception e) {
                cursor.close();
            }
        }
        if(result != null && !result.contains(".")) {
            try {
                final MimeTypeMap mime = MimeTypeMap.getSingleton();
                String extension = mime.getExtensionFromMimeType(mimeType);
                result = result + "." + extension;
            }
            catch(Exception eee) { eee.printStackTrace();}
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }

        return result;
    }

    class TooOld extends Exception {
        TooOld(String message) {
            super(message);
        }

    }

    class Postman implements Runnable {

        Context context;
        Message message;
        int delay;
        int attempts;
        Handler handler = new Handler();
        boolean init = false;
        long time;

        Postman(Context context, Message message, int delay, int attempts) {
            this.context = context;
            this.message = message;
            long size = message.getContent().length;
            long chunks = 0;
            long chunksIndex = 0;
            while(size > 0) {
                size = size - 50000+(chunksIndex*10000%50000);
                chunksIndex++;
                chunks++;
            }
            Random r = new Random();
            long time = 0;
            for(int c = 0; c < chunks; c++) {
                time+=r.nextInt(4000)+2000;
            }
            this.time = Math.round(time);
            this.delay = 1000*delay;
            this.attempts = attempts;
        }

        public void run(){

            if(0 == attempts) {
                return;
            }

            if(!init) {
                init = true;
                handler.postDelayed(this, time+Double.valueOf(Math.abs(new Random().nextGaussian())*delay).longValue());
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
                                Nearby.getMessagesClient(SendActivity.this)
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
            handler.postDelayed(this, time + Double.valueOf(Math.abs(new Random().nextGaussian()) * delay).longValue());

        }

    }

}