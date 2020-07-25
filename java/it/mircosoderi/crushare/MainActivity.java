package it.mircosoderi.crushare;

import android.app.ActivityManager;
import android.arch.persistence.room.Room;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
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
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static android.support.v4.content.FileProvider.getUriForFile;

public class MainActivity extends AppCompatActivity implements AbsListView.OnScrollListener {

    MessageListener mMessageListener = new NearbyMessageListener(this);
    ArrayList<String> mIncoming = new ArrayList<>();
    SparseIntArray mIncomingHashCodes = new SparseIntArray();
    WebViewArrayAdapter mAdapter;
    boolean isConnected = false;
    static ArrayList<String> displayed = new ArrayList<>();
    ArrayList<ImgSign> imgSigns = new ArrayList<>();
    ArrayList<Integer> mDeleted = new ArrayList<>();
    HashMap<String,SparseArray<byte[]>> bigMessages = new HashMap<>();

    int mMaxViewed = 0;
    long mSessionId = 0;
    long mArchiveSession = -1;

    SubscribeOptions mOptions = new SubscribeOptions.Builder()
            .setStrategy(Strategy.DEFAULT)
            .setCallback(new SubscribeCallback() {
                @Override
                public void onExpired() {
                    super.onExpired();
                    isConnected = false; invalidateOptionsMenu();
                    Date mexDateTimeObj = new Date();
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(MainActivity.this);
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                    }
                    mIncoming.add(String.format(getString(R.string.automatic_reconnect),datetime));
                    new AsyncDoArchiveMessage(MainActivity.this, new Message(String.format(getString(R.string.automatic_reconnect),datetime).getBytes(),"text/plain"), MainActivity.this.mSessionId, false, null, null).execute();
                    mIncomingHashCodes.put(mIncoming.size()-1,-1);
                    connect();
                }
            })
            .build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        setContentView(R.layout.activity_main);

        mAdapter = new WebViewArrayAdapter(this, mIncoming);
        ListView listView = findViewById(R.id.incoming);
        listView.setAdapter(mAdapter);
        listView.setOnScrollListener(this);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String uuid = sharedPref.getString(SettingsFragment.INSTANCE_ID, UUID.randomUUID().toString());
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(SettingsFragment.INSTANCE_ID, uuid);
        editor.apply();

    }

    @Override
    public void onStart() {

        super.onStart();

        mDeleted.clear();

        mSessionId = new Date().getTime();

        AppDatabase db = Room.databaseBuilder(this, AppDatabase.class, "crushare").allowMainThreadQueries().fallbackToDestructiveMigration().build();
        try {
            mArchiveSession = db.archivedMessageDao().maxSessionId();
        } catch(Exception e) {
            mArchiveSession = 0;
        }

        mIncoming.clear();
        mIncomingHashCodes.clear();

        redraw();

        String outgoing = getIntent().getStringExtra("outgoing");

        if(outgoing != null) {
            try {
                mArchiveSession = db.archivedMessageDao().prevSessionId(mArchiveSession);
            } catch(Exception e) {
                mArchiveSession = 0;
            }
            mIncoming.add("#outgoing "+outgoing);
            if(getIntent().hasExtra("message")) {
                mIncomingHashCodes.put(mIncoming.size()-1, Arrays.hashCode(((Message)getIntent().getParcelableExtra("message")).getContent()));
            }
            else if(getIntent().hasExtra("bigMessageHash")){
                mIncomingHashCodes.put(mIncoming.size() - 1, getIntent().getIntExtra("bigMessageHash", -1));
            }
            redraw();
            if(getIntent().hasExtra("signature") && getIntent().hasExtra("bigMessageContent") && getIntent().hasExtra("bigMessageType")) deliver((Message)getIntent().getParcelableExtra("signature"));
            if(getIntent().hasExtra("signature") && getIntent().hasExtra("bigMessageContent") && getIntent().hasExtra("bigMessageType")) deliver(new BigMessage(getIntent().getByteArrayExtra("bigMessageContent"),getIntent().getStringExtra("bigMessageType")));
            if(getIntent().hasExtra("message")) { deliver((Message)getIntent().getParcelableExtra("message")); }
            getIntent().removeExtra("outgoing");
            if(getIntent().hasExtra("signature")) getIntent().removeExtra("signature");
            if(getIntent().hasExtra("message")) getIntent().removeExtra("message");
            if(getIntent().hasExtra("bigMessageContent")) getIntent().removeExtra("bigMessageContent");
            if(getIntent().hasExtra("bigMessageType")) getIntent().removeExtra("bigMessageType");
        }

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                connect(true);
            }
        }, 2000);

        final EditText editText = findViewById(R.id.edit_message);
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    try {
                        Toast.makeText(MainActivity.this,MainActivity.this.getString(R.string.sending_quick_message), Toast.LENGTH_SHORT).show();
                        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                        String uuid = sharedPref.getString(SettingsFragment.INSTANCE_ID, UUID.randomUUID().toString());
                        String mText = editText.getText().toString();
                        Pattern p = Pattern.compile("<(.*?)>");
                        Matcher m = p.matcher(mText);
                        while(m.find()) {
                            mText = mText.replace("<"+m.group(1)+">","<"+m.group(1).toLowerCase()+">");
                        }
                        if(!DetectHtml.isHtml(mText)) mText = mText.replace("\n","<br>");
                        if(mText.contains("<html>") && mText.contains("<head>") && mText.contains("<body>") && mText.contains("</head>") && mText.contains("</html>") && mText.contains("</body>") ) {
                            mText = mText.substring(0, mText.indexOf("</head>")) + "<meta name=\"author\" content=\""+uuid+"\"><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/>" + mText.substring(mText.indexOf("</head>"));
                        }
                        else {
                            mText = "<!DOCTYPE html>\n<html><head><meta name=\"author\" content=\""+uuid+"\"><title>Message</title><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+mText+"</body></html>";
                        }

                        Date mexDateTimeObj = new Date();
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                            DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                            String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                            mText = mText.replace("<body>","<body><div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>");
                        }
                        else {
                            DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                            DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(MainActivity.this);
                            mText = mText.replace("<body>","<body><div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj)+"</div>");
                        }
                        final String signature = sharedPref.getString(SettingsFragment.SIGNATURE, getString(R.string.signature_default));
                        mText = mText.replace("</body>",(!signature.isEmpty()?"<p class=\"signature\">"+getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"")+"</body>");

                        PublishOptions options = new PublishOptions.Builder()
                                .setStrategy(Strategy.DEFAULT)
                                .setCallback(new PublishCallback() {
                                    @Override
                                    public void onExpired() {
                                        super.onExpired();
                                        finish();
                                    }
                                }).build();
                        final Message mMessage = new Message(mText.getBytes(),"text/plain");
                        MainActivity.displayed.add(String.valueOf(Arrays.hashCode(mMessage.getContent())));
                        Nearby.getMessagesClient(MainActivity.this)
                            .publish(mMessage,options)
                            .addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    Toast.makeText(MainActivity.this,MainActivity.this.getString(R.string.quick_message_send_error), Toast.LENGTH_LONG).show();
                                }
                            })
                            .addOnSuccessListener(new OnSuccessListener<Void>() {
                                @Override
                                public void onSuccess(Void aVoid) {
                                    new AsyncDoArchiveMessage(MainActivity.this, mMessage, MainActivity.this.mSessionId, true, null, null).execute();
                                    try {
                                        AppDatabase db = Room.databaseBuilder(MainActivity.this, AppDatabase.class, "crushare").allowMainThreadQueries().fallbackToDestructiveMigration().build();
                                        mArchiveSession = db.archivedMessageDao().prevSessionId(mArchiveSession);
                                    } catch(Exception e) {
                                        mArchiveSession = 0;
                                    }
                                    mIncoming.add("#outgoing "+new String(mMessage.getContent()));
                                    mIncomingHashCodes.put(mIncoming.size()-1,Arrays.hashCode(mMessage.getContent()));
                                    InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                                    if(imm != null) imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
                                    editText.getText().clear();
                                    redraw();
                                    ((ListView)findViewById(R.id.incoming)).smoothScrollToPosition(mIncoming.size()-1);
                                    deliver(mMessage);
                                }
                            });
                        handled = true;
                    }
                    catch(Exception e) {
                        handled = false;
                    }
                }
                return handled;
            }
        });

    }

    private void deliver(final Message message) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        final int delay = Integer.parseInt(sharedPref.getString(SettingsFragment.DELIVERY_INTERVAL, "10"));
        final int attempts = Integer.parseInt(sharedPref.getString(SettingsFragment.DELIVERY_ATTEMPTS, "2"));
        new Postman(this, message, delay, attempts-1).run();
    }

    @Override
    public void onStop() {
        disconnect(true);
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu (Menu menu) {

        if(isConnected) {
            menu.findItem(R.id.listen).setVisible(false);
            menu.findItem(R.id.stop_listening).setVisible(true);
        }
        else {
            menu.findItem(R.id.stop_listening).setVisible(false);
            menu.findItem(R.id.listen).setVisible(true);
        }

        if(mMaxViewed < mIncoming.size()-1) {
            menu.findItem(R.id.new_messages).setVisible(true);
        }
        else {
            menu.findItem(R.id.new_messages).setVisible(false);
        }

        return true;

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.new_messages:
                ListView lw = findViewById(R.id.incoming);
                lw.smoothScrollToPosition(mMaxViewed+1);
                return true;
            case R.id.listen:
                connect();
                return true;
            case R.id.stop_listening:
                disconnect(false);
                return true;
            case R.id.send_message:
                Intent sendMessageIntent = new Intent(this, EditMessageActivity.class);
                startActivity(sendMessageIntent);
                return true;
            case R.id.send_file:
                Intent share = new Intent(this, SendActivity.class);
                share.setAction(getString(R.string.send_file));
                startActivity(share);
                return true;
            case R.id.clear:
                mIncoming.clear();
                mIncomingHashCodes.clear();
                redraw();
                return true;
            case R.id.slideshow:
                startActivity(new Intent(this,SlideshowActivity.class));
                return true;
            case R.id.counter:
                startActivity(new Intent(this,CounterActivity.class));
                return true;
            case R.id.archive:
                startActivity(new Intent(this,ArchiveActivity.class));
                return true;
            case R.id.action_settings:
                startActivity(new Intent(this,SettingsActivity.class));
                return true;
            case R.id.action_about:
                startActivity(new Intent(this,AboutActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void connect() {
        connect(false);
    }

    public void connect(boolean offerArchive) {

        Date mexDateTimeObj = new Date();
        String datetime;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
            DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
            String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
            datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
        }
        else {
            DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
            DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(MainActivity.this);
            datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
        }

        if(offerArchive && 0 < mArchiveSession) {
            mIncoming.add(String.format(getString(R.string.connecting_html_offering_archive),datetime));
            new AsyncDoArchiveMessage(MainActivity.this, new Message(String.format(getString(R.string.connecting_html_offering_archive),datetime).getBytes(),"text/plain"), MainActivity.this.mSessionId, false, null, null).execute();
        }
        else {
            mIncoming.add(String.format(getString(R.string.connecting_html),datetime));
            new AsyncDoArchiveMessage(MainActivity.this, new Message(String.format(getString(R.string.connecting_html),datetime).getBytes(),"text/plain"), MainActivity.this.mSessionId, false, null, null).execute();
        }

        mIncomingHashCodes.put(mIncoming.size()-1,-1);
        redraw();

        Nearby.getMessagesClient(this).subscribe(mMessageListener, mOptions).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                isConnected = false; invalidateOptionsMenu();
                Date mexDateTimeObj = new Date();
                String datetime;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                    String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                    datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                }
                else {
                    DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                    DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(MainActivity.this);
                    datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                }
                mIncoming.add(String.format(getString(R.string.you_are_disconnected), datetime));
                new AsyncDoArchiveMessage(MainActivity.this, new Message(String.format(getString(R.string.you_are_disconnected), datetime).getBytes(),"text/plain"), MainActivity.this.mSessionId, false, null, null).execute();
                mIncomingHashCodes.put(mIncoming.size()-1,-1);
                redraw();
            }
        }).addOnCanceledListener(new OnCanceledListener() {
            @Override
            public void onCanceled() {
                isConnected = false; invalidateOptionsMenu();
                Date mexDateTimeObj = new Date();
                String datetime;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                    String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                    datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                }
                else {
                    DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                    DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(MainActivity.this);
                    datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                }
                mIncoming.add(String.format(getString(R.string.you_are_disconnected), datetime));
                new AsyncDoArchiveMessage(MainActivity.this, new Message(String.format(getString(R.string.you_are_disconnected), datetime).getBytes(),"text/plain"), MainActivity.this.mSessionId, false, null, null).execute();
                mIncomingHashCodes.put(mIncoming.size()-1,-1);
                redraw();
            }
        }).addOnSuccessListener(new OnSuccessListener<Object>() {
            @Override
            public void onSuccess(Object result) {
                isConnected = true; invalidateOptionsMenu();
                Date mexDateTimeObj = new Date();
                String datetime;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                    String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                    datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                }
                else {
                    DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                    DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(MainActivity.this);
                    datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                }
                mIncoming.add(String.format(getString(R.string.you_are_connected),datetime));
                new AsyncDoArchiveMessage(MainActivity.this, new Message(String.format(getString(R.string.you_are_connected),datetime).getBytes(),"text/plain"), MainActivity.this.mSessionId, false, null, null).execute();
                mIncomingHashCodes.put(mIncoming.size()-1,-1);
                redraw();
            }
        });

    }

    public void disconnect(final boolean toast) {

        if(toast) Toast.makeText(MainActivity.this, getString(R.string.disconnecting), Toast.LENGTH_SHORT).show();
        Date mexDateTimeObj = new Date();
        String datetime;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
            DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
            String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
            datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
        }
        else {
            DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
            DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(MainActivity.this);
            datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
        }
        mIncoming.add(String.format(getString(R.string.disconnecting_html),datetime));
        new AsyncDoArchiveMessage(MainActivity.this, new Message(String.format(getString(R.string.disconnecting_html),datetime).getBytes(),"text/plain"), MainActivity.this.mSessionId, false, null, null).execute();
        mIncomingHashCodes.put(mIncoming.size()-1,-1);
        redraw();

        Nearby.getMessagesClient(this).unsubscribe(mMessageListener).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                isConnected = true; invalidateOptionsMenu();
                if(toast) Toast.makeText(MainActivity.this, getString(R.string.disconnection_error),
                        Toast.LENGTH_SHORT).show();
            }
        }).addOnSuccessListener(new OnSuccessListener<Object>() {
            @Override
            public void onSuccess(Object result) {
                isConnected = false; invalidateOptionsMenu();
                Date mexDateTimeObj = new Date();
                String datetime;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                    String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                    datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                }
                else {
                    DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                    DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(MainActivity.this);
                    datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                }
                mIncoming.add(String.format(getString(R.string.you_are_disconnected), datetime));
                new AsyncDoArchiveMessage(MainActivity.this, new Message(String.format(getString(R.string.you_are_disconnected), datetime).getBytes(),"text/plain"), MainActivity.this.mSessionId, false, null, null).execute();
                mIncomingHashCodes.put(mIncoming.size()-1,-1);
                redraw();
                if(toast) Toast.makeText(MainActivity.this, getString(R.string.disconnected),
                        Toast.LENGTH_SHORT).show();
            }
        });

    }

    public void share() {
        Intent share = new Intent(this, SendActivity.class);
        share.setAction(getString(R.string.send_file));
        startActivity(share);
    }

    public void redraw() {
        mAdapter.notifyDataSetChanged();
    }

    class NearbyMessageListener extends MessageListener {

        MainActivity mainActivity;

        NearbyMessageListener(MainActivity mainActivity) {
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
                if( !bigMessages.containsKey(type) ) {
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
                             bigMessages.get(type).put(j,new byte[1]);
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

            if((!(message instanceof BigMessage)) && message.getType().equals("text/uuid")) return;

            if((!(message instanceof BigMessage)) && displayed.contains(String.valueOf(Arrays.hashCode(message.getContent())))) return;

            if((!(message instanceof BigMessage)) && new String(message.getContent()).startsWith("DESTROY")) {
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
                        mAdapter.notifyDataSetChanged();
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

            if(!(message instanceof BigMessage)) displayed.add(String.valueOf(Arrays.hashCode(message.getContent())));

            String signatureToBeArchived = null;

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
                if(!DetectHtml.isHtml(txt)) txt = txt.replace("\n","<br>");
                if(txt.contains("<html>") && txt.contains("<head>") && txt.contains("</head>") && txt.contains("</html>")) {
                    txt = txt.substring(0, txt.indexOf("</head>")) + "<link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/>" + txt.substring(txt.indexOf("</head>"));
                }
                else {
                    txt = "<!DOCTYPE html>\n<html><head><title>Message</title><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+txt+"</body></html>";
                }
                if(!(mIncoming.contains(txt) || mIncoming.contains("#outgoing "+txt) )) {
                    AppDatabase db = Room.databaseBuilder(mainActivity, AppDatabase.class, "crushare").allowMainThreadQueries().fallbackToDestructiveMigration().build();
                    String lastMessageContent;
                    String lastMessageAuthor;
                    try {
                        lastMessageContent = new String(db.archivedMessageDao().getArchivedMessagesDesc(mSessionId).get(0).getMessageContent());
                        lastMessageAuthor = db.archivedMessageDao().getArchivedMessagesDesc(mSessionId).get(0).getAuthor();
                    }
                    catch(Exception e) {
                        lastMessageContent = "";
                        lastMessageAuthor = "";
                    }
                    if(lastMessageContent.isEmpty()) lastMessageContent = "empty";
                    if(lastMessageAuthor == null || lastMessageAuthor.isEmpty()) lastMessageAuthor = "nobody";
                    if(!(txt.substring(Math.max(txt.indexOf("<meta name=\"author\" content=\""),0), Math.max(txt.indexOf(">",Math.max(txt.indexOf("<meta name=\"author\" content=\""),0)),0)).equals(lastMessageContent.substring(Math.max(lastMessageContent.indexOf("<meta name=\"author\" content=\""),0), Math.max(lastMessageContent.indexOf(">",Math.max(lastMessageContent.indexOf("<meta name=\"author\" content=\""),0)),0))) || txt.contains("<meta name=\"author\" content=\""+lastMessageAuthor))) {
                        mIncoming.add(txt);
                        mIncomingHashCodes.put(-1 + mIncoming.size(), Arrays.hashCode(message.getContent()));
                    }
                    else {
                        List<ArchivedMessage> list = db.archivedMessageDao().getArchivedMessagesDesc(mSessionId);
                        int index = -1;
                        int i = 0;
                        while(index == -1 && i < list.size()) {
                            index = mIncomingHashCodes.indexOfValue(list.get(i).getMexHash());
                            i++;
                        }

                        txt = txt.replace("</body>","<div style=\"position: absolute; top:12px; width:91%;\"><a style=\"font-size: x-small; float:right; margin-right:6px; text-decoration:none !important; color: #3F51B5; \" title=\"up\" href=\"move://to.previous.of/"+(1+db.archivedMessageDao().getLastMessage().get(0).getMessageId())+"/in/"+index+"\">&#9650;</a></div></body>");
                        if(index > -1) {
                            mIncoming.remove(index);
                            mIncoming.add(index, txt);
                            mIncomingHashCodes.put(index, Arrays.hashCode(message.getContent()));
                        }
                        else {
                            mIncoming.add(txt);
                            mIncomingHashCodes.put(-1 + mIncoming.size(), Arrays.hashCode(message.getContent()));
                        }
                    }
                }

            }
            else if (mimeType != null && mimeType.startsWith("video/")) {
                try {

                    File tmp = File.createTempFile(getString(R.string.incoming_file_prefix), message.getType().substring(message.getType().lastIndexOf(".")), getCacheDir());
                    FileOutputStream tmpos = new FileOutputStream(tmp);
                    tmpos.write(message.getContent());
                    tmpos.flush();
                    tmpos.close();

                    String uuid = message.getType().substring(0,message.getType().indexOf("."));
                    /*DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                    DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(MainActivity.this);
                    String datetime = "<div class=\"datetime\">"+dateFormat.format(new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf(".")))))+" "+timeFormat.format(new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf(".")))))+"</div>";
*/
                    Date mexDateTimeObj = new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf("."))));
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>";
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(MainActivity.this);
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj)+"</div>";
                    }

                    signatureToBeArchived = new ImgSign().getSign(uuid,String.valueOf(Arrays.hashCode(message.getContent())));
                    new ImgSign().delSign(uuid,String.valueOf(Arrays.hashCode(message.getContent())));

                    Uri tmpUri = getUriForFile(MainActivity.this, "it.mircosoderi.fileprovider", tmp);
                    grantUriPermission(getPackageName(), tmpUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mainActivity);
                    if (sharedPref.getBoolean(SettingsFragment.INLINE_VIDEOS, true)) {
                        String newMex = "<html><head><meta name=\"author\" content=\""+(signatureToBeArchived+" ").substring(0, 36)+"\"><title>"+tmp.getName()+"</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+getString(R.string.media_warning)+"</p>"+"<p>Put a look at this "+"<a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + getMimeType(tmpUri.toString()).substring(1 + getMimeType(tmpUri.toString()).indexOf("/")) + " "+getMimeType(tmpUri.toString()).substring(0,getMimeType(tmpUri.toString()).indexOf("/"))+"</b></a>!"+"</p>"+(signatureToBeArchived.length() > 36?"<p>"+getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signatureToBeArchived.substring(36)+"</span></p>":"")+"<p>"+"<object style=\"max-width:100%; height:auto; border: thin dotted #3F51B5;\" type=\""+mimeType+"\" data=\"" + tmpUri.toString() + "\"><param name=\"autoplay\" value=\"false\" ><param name=\"loop\" value=\"false\" ></object>"+"</p></body></html>";
                        if (!(mIncoming.contains(newMex) || mIncoming.contains("#outgoing "+newMex))) {
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
                            if(!( ( (signatureToBeArchived+" ").substring(0,36).equals(lastMessageAuthor) || lastMessageContent.contains("<meta name=\"author\" content=\""+(signatureToBeArchived+" ").substring(0,36))) && String.valueOf(mSessionId).equals(lastMessageSession) )) {
                                mIncoming.add(newMex);
                                mIncomingHashCodes.put(-1+mIncoming.size(),Arrays.hashCode(message.getContent()));
                            }
                            else {
                                List<ArchivedMessage> list = db.archivedMessageDao().getArchivedMessagesDesc(mSessionId);
                                int index = -1;
                                int i = 0;
                                while(index == -1 && i < list.size()) {
                                    index = mIncomingHashCodes.indexOfValue(list.get(i).getMexHash());
                                    i++;
                                }

                                newMex = newMex.replace("<p><object","<div style=\"position: absolute; top:12px; width:91%;\"><a style=\"font-size:x-small; margin-right:6px; float:right; text-decoration:none !important; color: #3F51B5; \" title=\"up\" href=\"move://to.previous.of/"+(1+db.archivedMessageDao().getLastMessage().get(0).getMessageId())+"/in/"+index+"\">&#9650;</a></div><p><object");
                                if(index > -1) {
                                    mIncoming.remove(index);
                                    mIncoming.add(index, newMex);
                                    mIncomingHashCodes.put(index,Arrays.hashCode(message.getContent()));
                                }
                                else {
                                    mIncoming.add(newMex);
                                    mIncomingHashCodes.put(-1+mIncoming.size(),Arrays.hashCode(message.getContent()));
                                }
                            }
                        }
                    } else {
                        String newMex = "<html><head><meta name=\"author\" content=\""+(signatureToBeArchived+" ").substring(0, 36)+"\"><title>" + tmp.getName() + "</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+getString(R.string.media_warning)+"</p>"+"<p>Put a look at this <a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + getMimeType(tmpUri.toString()).substring(1 + getMimeType(tmpUri.toString()).indexOf("/")) + " "+getMimeType(tmpUri.toString()).substring(0,getMimeType(tmpUri.toString()).indexOf("/"))+"</b></a>!</p>"+(signatureToBeArchived.length() > 36?"<p>"+getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signatureToBeArchived.substring(36)+"</span></p>":"")+"</body></html>";
                        if (!(mIncoming.contains(newMex) || mIncoming.contains("#outgoing "+newMex))) {
                            AppDatabase db = Room.databaseBuilder(mainActivity, AppDatabase.class, "crushare").allowMainThreadQueries().fallbackToDestructiveMigration().build();
                            String lastMessageContent;
                            String lastMessageAuthor;
                            try {
                                lastMessageContent = new String(db.archivedMessageDao().getLastMessage().get(0).getMessageContent());
                                lastMessageAuthor = db.archivedMessageDao().getLastMessage().get(0).getAuthor();
                            }
                            catch(Exception e) {
                                lastMessageContent = "";
                                lastMessageAuthor = "";
                            }
                            if(lastMessageContent.isEmpty()) lastMessageContent = "empty";
                            if(lastMessageAuthor == null || lastMessageAuthor.isEmpty()) lastMessageAuthor = "nobody";
                            if(!((signatureToBeArchived+" ").substring(0,36).equals(lastMessageAuthor) || lastMessageContent.contains("<meta name=\"author\" content=\""+(signatureToBeArchived+" ").substring(0,36)))) {
                                mIncoming.add(newMex);
                                mIncomingHashCodes.put(-1+mIncoming.size(),Arrays.hashCode(message.getContent()));
                            }
                            else {
                                List<ArchivedMessage> list = db.archivedMessageDao().getArchivedMessagesDesc(mSessionId);
                                int index = -1;
                                int i = 0;
                                while(index == -1 && i < list.size()) {
                                    index = mIncomingHashCodes.indexOfValue(list.get(i).getMexHash());
                                    i++;
                                }

                                newMex = newMex.replace("</body>","<div style=\"position: absolute; top:12px; width:91%;\"><a style=\"font-size: x-small; margin-right:6px; float:right; text-decoration:none !important; color: #3F51B5; \" title=\"up\" href=\"move://to.previous.of/"+(1+db.archivedMessageDao().getLastMessage().get(0).getMessageId())+"/in/"+index+"\">&#9650;</a></div></body>");

                                if(index > -1) {
                                    mIncoming.remove(index);
                                    mIncoming.add(index, newMex);
                                    mIncomingHashCodes.put(index,Arrays.hashCode(message.getContent()));
                                }
                                else {
                                    mIncoming.add(newMex);
                                    mIncomingHashCodes.put(-1+mIncoming.size(),Arrays.hashCode(message.getContent()));
                                }
                            }
                        }
                    }

                } catch (IOException e) {
                    isAllRight = false;
                    Date mexDateTimeObj = new Date();
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(MainActivity.this);
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                    }
                    mIncoming.add(String.format(getString(R.string.file_missed),datetime));
                    new AsyncDoArchiveMessage(MainActivity.this, new Message(String.format(getString(R.string.file_missed),datetime).getBytes(),"text/plain"), MainActivity.this.mSessionId, false, null, null).execute();
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
                    /*DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                    DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(MainActivity.this);
                    String datetime = "<div class=\"datetime\">"+dateFormat.format(new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf(".")))))+" "+timeFormat.format(new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf(".")))))+"</div>";
*/
                    Date mexDateTimeObj = new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf("."))));
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>";
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(MainActivity.this);
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj)+"</div>";
                    }

                    signatureToBeArchived = new ImgSign().getSign(uuid,String.valueOf(Arrays.hashCode(message.getContent())));
                    new ImgSign().delSign(uuid,String.valueOf(Arrays.hashCode(message.getContent())));

                    Uri tmpUri = getUriForFile(MainActivity.this, "it.mircosoderi.fileprovider", tmp);
                    grantUriPermission(getPackageName(), tmpUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    String newMex = "<html><head><meta name=\"author\" content=\""+(signatureToBeArchived+" ").substring(0, 36)+"\"><title>"+tmp.getName()+"</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+getString(R.string.media_warning)+"</p>"+"<p>Put a look at this "+"<a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>PDF document</b></a>!"+"</p>"+(signatureToBeArchived.length() > 36?"<p>"+getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signatureToBeArchived.substring(36)+"</span></p>":"")+"</body></html>";
                    if (!(mIncoming.contains(newMex) || mIncoming.contains("#outgoing "+newMex))) {
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
                        if(!( ( (signatureToBeArchived+" ").substring(0,36).equals(lastMessageAuthor) || lastMessageContent.contains("<meta name=\"author\" content=\""+(signatureToBeArchived+" ").substring(0,36))) && String.valueOf(mSessionId).equals(lastMessageSession) )) {
                            mIncoming.add(newMex);
                            mIncomingHashCodes.put(-1+mIncoming.size(),Arrays.hashCode(message.getContent()));
                        }
                        else {
                            List<ArchivedMessage> list = db.archivedMessageDao().getArchivedMessagesDesc(mSessionId);
                            int index = -1;
                            int i = 0;
                            while(index == -1 && i < list.size()) {
                                index = mIncomingHashCodes.indexOfValue(list.get(i).getMexHash());
                                i++;
                            }

                            newMex = newMex.replace("</body>","<div style=\"position: absolute; top:12px; width:91%;\"><a style=\"font-size:x-small; margin-right:6px; float:right; text-decoration:none !important; color: #3F51B5; \" title=\"up\" href=\"move://to.previous.of/"+(1+db.archivedMessageDao().getLastMessage().get(0).getMessageId())+"/in/"+index+"\">&#9650;</a></div></body>");
                            if(index > -1) {
                                mIncoming.remove(index);
                                mIncoming.add(index, newMex);
                                mIncomingHashCodes.put(index,Arrays.hashCode(message.getContent()));
                            }
                            else {
                                mIncoming.add(newMex);
                                mIncomingHashCodes.put(-1+mIncoming.size(),Arrays.hashCode(message.getContent()));
                            }
                        }
                    }
                } catch (IOException e) {
                    isAllRight = false;
                    Date mexDateTimeObj = new Date();
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(MainActivity.this);
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                    }
                    mIncoming.add(String.format(getString(R.string.file_missed),datetime));
                    new AsyncDoArchiveMessage(MainActivity.this, new Message(String.format(getString(R.string.file_missed),datetime).getBytes(),"text/plain"), MainActivity.this.mSessionId, false, null, null).execute();
                    mIncomingHashCodes.put(mIncoming.size()-1,-1);
                }


            }
            else if (mimeType != null && mimeType.startsWith("image/")) {
                try {

                    File tmp = File.createTempFile(getString(R.string.incoming_file_prefix), message.getType().substring(message.getType().lastIndexOf(".")), getCacheDir());
                    FileOutputStream tmpos = new FileOutputStream(tmp);
                    tmpos.write(message.getContent());
                    tmpos.flush();
                    tmpos.close();

                    String uuid = message.getType().substring(0,message.getType().indexOf("."));
                    /*DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                    DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(MainActivity.this);
                    String datetime = "<div class=\"datetime\">"+dateFormat.format(new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf(".")))))+" "+timeFormat.format(new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf(".")))))+"</div>";
*/
                    Date mexDateTimeObj = new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf("."))));
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>";
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(MainActivity.this);
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj)+"</div>";
                    }

                    signatureToBeArchived = new ImgSign().getSign(uuid,String.valueOf(Arrays.hashCode(message.getContent())));
                    new ImgSign().delSign(uuid,String.valueOf(Arrays.hashCode(message.getContent())));

                    Uri tmpUri = getUriForFile(MainActivity.this, "it.mircosoderi.fileprovider", tmp);
                    grantUriPermission(getPackageName(), tmpUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mainActivity);
                    if (getMimeType(tmpUri.toString()).startsWith("image/") && sharedPref.getBoolean(SettingsFragment.INLINE_IMAGES, true)) {
                        String newMex = "<html><head><meta name=\"author\" content=\""+(signatureToBeArchived+" ").substring(0, 36)+"\"><title>"+tmp.getName()+"</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+getString(R.string.media_warning)+"</p>"+"<p>Put a look at this "+"<a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + getMimeType(tmpUri.toString()).substring(1 + getMimeType(tmpUri.toString()).indexOf("/")) + " "+getMimeType(tmpUri.toString()).substring(0,getMimeType(tmpUri.toString()).indexOf("/"))+"</b></a>!"+"</p>"+(signatureToBeArchived.length() > 36?"<p>"+getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signatureToBeArchived.substring(36)+"</span></p>":"")+"<p><object style=\"width:100%;\" data=\""+tmpUri.toString()+"\" type=\""+getMimeType(tmpUri.toString())+"\" /></p></body></html>";
                        if (!(mIncoming.contains(newMex) || mIncoming.contains("#outgoing "+newMex))) {
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
                            if(!( ( (signatureToBeArchived+" ").substring(0,36).equals(lastMessageAuthor) || lastMessageContent.contains("<meta name=\"author\" content=\""+(signatureToBeArchived+" ").substring(0,36))) && String.valueOf(mSessionId).equals(lastMessageSession) )) {
                                mIncoming.add(newMex);
                                mIncomingHashCodes.put(-1+mIncoming.size(),Arrays.hashCode(message.getContent()));
                            }
                            else {
                                List<ArchivedMessage> list = db.archivedMessageDao().getArchivedMessagesDesc(mSessionId);
                                int index = -1;
                                int i = 0;
                                while(index == -1 && i < list.size()) {
                                    index = mIncomingHashCodes.indexOfValue(list.get(i).getMexHash());
                                    i++;
                                }

                                newMex = newMex.replace("<p><object","<div style=\"position: absolute; top:12px; width:91%;\"><a style=\"font-size:x-small; margin-right:6px; float:right; text-decoration:none !important; color: #3F51B5; \" title=\"up\" href=\"move://to.previous.of/"+(1+db.archivedMessageDao().getLastMessage().get(0).getMessageId())+"/in/"+index+"\">&#9650;</a></div><p><object");
                                if(index > -1) {
                                    mIncoming.remove(index);
                                    mIncoming.add(index, newMex);
                                    mIncomingHashCodes.put(index,Arrays.hashCode(message.getContent()));
                                }
                                else {
                                    mIncoming.add(newMex);
                                    mIncomingHashCodes.put(-1+mIncoming.size(),Arrays.hashCode(message.getContent()));
                                }
                            }
                        }
                    } else {
                        String newMex = "<html><head><meta name=\"author\" content=\""+(signatureToBeArchived+" ").substring(0, 36)+"\"><title>" + tmp.getName() + "</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+getString(R.string.media_warning)+"</p>"+"<p>Put a look at this <a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + getMimeType(tmpUri.toString()).substring(1 + getMimeType(tmpUri.toString()).indexOf("/")) + " "+getMimeType(tmpUri.toString()).substring(0,getMimeType(tmpUri.toString()).indexOf("/"))+"</b></a>!</p>"+(signatureToBeArchived.length() > 36?"<p>"+getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signatureToBeArchived.substring(36)+"</span></p>":"")+"</body></html>";
                        if (!(mIncoming.contains(newMex) || mIncoming.contains("#outgoing "+newMex))) {
                            AppDatabase db = Room.databaseBuilder(mainActivity, AppDatabase.class, "crushare").allowMainThreadQueries().fallbackToDestructiveMigration().build();
                            String lastMessageContent;
                            String lastMessageAuthor;
                            try {
                                lastMessageContent = new String(db.archivedMessageDao().getLastMessage().get(0).getMessageContent());
                                lastMessageAuthor = db.archivedMessageDao().getLastMessage().get(0).getAuthor();
                            }
                            catch(Exception e) {
                                lastMessageContent = "";
                                lastMessageAuthor = "";
                            }
                            if(lastMessageContent.isEmpty()) lastMessageContent = "empty";
                            if(lastMessageAuthor == null || lastMessageAuthor.isEmpty()) lastMessageAuthor = "nobody";
                            if(!((signatureToBeArchived+" ").substring(0,36).equals(lastMessageAuthor) || lastMessageContent.contains("<meta name=\"author\" content=\""+(signatureToBeArchived+" ").substring(0,36)))) {
                                mIncoming.add(newMex);
                                mIncomingHashCodes.put(-1+mIncoming.size(),Arrays.hashCode(message.getContent()));
                            }
                            else {
                                List<ArchivedMessage> list = db.archivedMessageDao().getArchivedMessagesDesc(mSessionId);
                                int index = -1;
                                int i = 0;
                                while(index == -1 && i < list.size()) {
                                    index = mIncomingHashCodes.indexOfValue(list.get(i).getMexHash());
                                    i++;
                                }

                                newMex = newMex.replace("</body>","<div style=\"position: absolute; top:12px; width:91%;\"><a style=\"font-size: x-small; margin-right:6px; float:right; text-decoration:none !important; color: #3F51B5; \" title=\"up\" href=\"move://to.previous.of/"+(1+db.archivedMessageDao().getLastMessage().get(0).getMessageId())+"/in/"+index+"\">&#9650;</a></div></body>");

                                if(index > -1) {
                                    mIncoming.remove(index);
                                    mIncoming.add(index, newMex);
                                    mIncomingHashCodes.put(index,Arrays.hashCode(message.getContent()));
                                }
                                else {
                                    mIncoming.add(newMex);
                                    mIncomingHashCodes.put(-1+mIncoming.size(),Arrays.hashCode(message.getContent()));
                                }
                            }
                        }
                    }

                } catch (IOException e) {
                    isAllRight = false;
                    Date mexDateTimeObj = new Date();
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(MainActivity.this);
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                    }
                    mIncoming.add(String.format(getString(R.string.file_missed),datetime));
                    new AsyncDoArchiveMessage(MainActivity.this, new Message(String.format(getString(R.string.file_missed),datetime).getBytes(),"text/plain"), MainActivity.this.mSessionId, false, null, null).execute();
                    mIncomingHashCodes.put(mIncoming.size()-1,-1);
                }
            }
            else {
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
                    /*DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                    DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(MainActivity.this);
                    String datetime = "<div class=\"datetime\">"+dateFormat.format(new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf(".")))))+" "+timeFormat.format(new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf(".")))))+"</div>";
*/
                    Date mexDateTimeObj = new Date(Long.valueOf(message.getType().substring(1+message.getType().indexOf("."),message.getType().lastIndexOf("."))));
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>";
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(MainActivity.this);
                        datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj)+"</div>";
                    }

                    signatureToBeArchived = new ImgSign().getSign(uuid,String.valueOf(Arrays.hashCode(message.getContent())));
                    new ImgSign().delSign(uuid,String.valueOf(Arrays.hashCode(message.getContent())));

                    Uri tmpUri = getUriForFile(MainActivity.this, "it.mircosoderi.fileprovider", tmp);
                    grantUriPermission(getPackageName(), tmpUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    String newMex = "<html><head><meta name=\"author\" content=\""+(signatureToBeArchived+" ").substring(0, 36)+"\"><title>"+tmp.getName()+"</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+getString(R.string.media_warning)+"</p>"+"<p>Put a look at files in this "+"<a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>archive</b></a>!"+"</p>"+(signatureToBeArchived.length() > 36?"<p>"+getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signatureToBeArchived.substring(36)+"</span></p>":"")+"</body></html>";
                    if (!(mIncoming.contains(newMex) || mIncoming.contains("#outgoing "+newMex))) {
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
                        if(!( ( (signatureToBeArchived+" ").substring(0,36).equals(lastMessageAuthor) || lastMessageContent.contains("<meta name=\"author\" content=\""+(signatureToBeArchived+" ").substring(0,36))) && String.valueOf(mSessionId).equals(lastMessageSession) )) {
                            mIncoming.add(newMex);
                            mIncomingHashCodes.put(-1+mIncoming.size(),Arrays.hashCode(message.getContent()));
                        }
                        else {
                            List<ArchivedMessage> list = db.archivedMessageDao().getArchivedMessagesDesc(mSessionId);
                            int index = -1;
                            int i = 0;
                            while(index == -1 && i < list.size()) {
                                index = mIncomingHashCodes.indexOfValue(list.get(i).getMexHash());
                                i++;
                            }

                            newMex = newMex.replace("</body>","<div style=\"position: absolute; top:12px; width:91%;\"><a style=\"font-size:x-small; margin-right:6px; float:right; text-decoration:none !important; color: #3F51B5; \" title=\"up\" href=\"move://to.previous.of/"+(1+db.archivedMessageDao().getLastMessage().get(0).getMessageId())+"/in/"+index+"\">&#9650;</a></div></body>");
                            if(index > -1) {
                                mIncoming.remove(index);
                                mIncoming.add(index, newMex);
                                mIncomingHashCodes.put(index,Arrays.hashCode(message.getContent()));
                            }
                            else {
                                mIncoming.add(newMex);
                                mIncomingHashCodes.put(-1+mIncoming.size(),Arrays.hashCode(message.getContent()));
                            }
                        }
                    }
                } catch (IOException e) {
                    isAllRight = false;
                    Date mexDateTimeObj = new Date();
                    String datetime;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj);
                    }
                    else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainActivity.this);
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(MainActivity.this);
                        datetime = dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj);
                    }
                    mIncoming.add(String.format(getString(R.string.file_missed),datetime));
                    new AsyncDoArchiveMessage(MainActivity.this, new Message(String.format(getString(R.string.file_missed),datetime).getBytes(),"text/plain"), MainActivity.this.mSessionId, false, null, null).execute();
                    mIncomingHashCodes.put(mIncoming.size()-1,-1);
                }


            }

            if(isAllRight) {
                new AsyncDoArchiveMessage(mainActivity, message, mSessionId, false, signatureToBeArchived != null ? (signatureToBeArchived + " ").substring(36).trim() : null, signatureToBeArchived != null ? (signatureToBeArchived + " ").substring(0, 36) : null).execute();
                redraw();
                ((ListView)findViewById(R.id.incoming)).smoothScrollToPosition(mIncoming.size()-1);
                deliver(new BigMessage(message.getContent(),message.getType()));
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

        private void deliver(Message message) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            int delay = Integer.parseInt(sharedPref.getString(SettingsFragment.DELIVERY_INTERVAL, "10"));
            int attempts = Integer.parseInt(sharedPref.getString(SettingsFragment.DELIVERY_ATTEMPTS, "2"));
            new Postman(MainActivity.this, message, delay, attempts).run();
        }

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
                                Nearby.getMessagesClient(MainActivity.this)
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

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem,int visibleItemCount, int totalItemCount) {
        ListView incoming = findViewById(R.id.incoming);
        int lastVisibleRow = incoming.getLastVisiblePosition();
        if(lastVisibleRow > mMaxViewed) mMaxViewed = lastVisibleRow;
        if(mMaxViewed == mIncoming.size()-1) {
            invalidateOptionsMenu();
        }
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {}

    public void hide(int position) {
        mIncoming.remove(position);
        SparseIntArray newIncomingHashCodes = new SparseIntArray();
        for(int i = 0; i < mIncomingHashCodes.size(); i++) {
            if(mIncomingHashCodes.keyAt(i) < position) {
                newIncomingHashCodes.put(mIncomingHashCodes.keyAt(i), mIncomingHashCodes.valueAt(i));
            }
            else if(mIncomingHashCodes.keyAt(i) > position) {
                newIncomingHashCodes.put(mIncomingHashCodes.keyAt(i)-1, mIncomingHashCodes.valueAt(i));
            }
        }
        mIncomingHashCodes = newIncomingHashCodes;
        mAdapter.notifyDataSetChanged();
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