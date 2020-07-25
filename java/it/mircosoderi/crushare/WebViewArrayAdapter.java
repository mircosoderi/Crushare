package it.mircosoderi.crushare;

import android.arch.persistence.room.Room;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.PopupMenu;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.MimeTypeMap;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.PublishCallback;
import com.google.android.gms.nearby.messages.PublishOptions;
import com.google.android.gms.nearby.messages.Strategy;
import com.google.android.gms.tasks.OnFailureListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import static android.support.v4.content.FileProvider.getUriForFile;

public class WebViewArrayAdapter extends ArrayAdapter<String> {

    private Context mContext;

    private List<String> messages;

    private HashMap<View, String> viewMessages = new HashMap<>();

    WebViewArrayAdapter(@NonNull Context context, ArrayList<String> list) {

        super(context, 0, list);

        PreferenceManager.setDefaultValues(context, R.xml.preferences, false);

        mContext = context;

        messages = list;

    }

    class MyWebViewOnLongClickListener implements View.OnLongClickListener {

        int mPosition;

        MyWebViewOnLongClickListener(int position) {
            super();
            this.mPosition = position;
        }

        class MyWebViewMenuItemClickListener implements PopupMenu.OnMenuItemClickListener {

            int mPosition;

            MyWebViewMenuItemClickListener(int position) {
                super();
                this.mPosition = position;
            }

            @Override
            public boolean onMenuItemClick(MenuItem item) {
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
                int delay = Integer.parseInt(sharedPref.getString(SettingsFragment.DELIVERY_INTERVAL, "10"));
                int attempts = Integer.parseInt(sharedPref.getString(SettingsFragment.DELIVERY_ATTEMPTS, "2"));
                WeakReference<Context> context = new WeakReference<>(mContext);
                AppDatabase db = Room.databaseBuilder(context.get(), AppDatabase.class, "crushare").allowMainThreadQueries().fallbackToDestructiveMigration().build();
                byte[] messageContent = new byte[]{};
                String hash = null;
                if(mContext instanceof MainActivity) {
                    hash = String.valueOf(((MainActivity)mContext).mIncomingHashCodes.get(mPosition));
                    messageContent = db.archivedMessageDao().getRawContent(Integer.parseInt(hash));

                }
                else if(mContext instanceof ArchiveActivity) {
                    hash = String.valueOf(((ArchiveActivity)mContext).mHashCodes.get(mPosition));
                    messageContent = db.archivedMessageDao().getRawContent(Integer.parseInt(hash));
                }
                String messageType = "";
                if(mContext instanceof MainActivity) {
                    messageType = db.archivedMessageDao().getMessageType(((MainActivity)mContext).mIncomingHashCodes.get(mPosition));
                }
                else if(mContext instanceof ArchiveActivity) {
                    messageType = db.archivedMessageDao().getMessageType(((ArchiveActivity)mContext).mHashCodes.get(mPosition));
                }
                switch (item.getItemId()) {
                    /*case R.id.message_html:
                        String messageString = "";
                        for (byte aRawContent : messageContent) {
                            if (aRawContent > 0)
                                messageString = messageString.concat(new String(new byte[]{aRawContent}));
                            else
                                messageString = messageString.concat(String.format("%02X ", aRawContent));
                        }
                        db.close();
                        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                        builder.setMessage(messageString).setTitle(R.string.raw_mex);
                        AlertDialog dialog = builder.create();
                        dialog.show();
                        break;*/
                    case R.id.send_again:
                        if(!("text/plain".equals(messageType))) {
                            String uuid = db.archivedMessageDao().getMediaAuthor(Integer.parseInt(hash));
                            String author =  uuid.substring(0,uuid.indexOf("-"));
                            String sign = db.archivedMessageDao().getMediaSignature(Integer.parseInt(hash));
                            String signature = uuid+sign;
                            Message signMex = new Message(signature.getBytes(), "text/sign "+author+" "+hash);
                            new Postman(mContext, signMex, delay, attempts).run();
                        }
                        Message message = new BigMessage(messageContent,messageType);
                        new Postman(mContext, message, delay, attempts).run();
                        Toast.makeText(mContext, mContext.getString(R.string.done),Toast.LENGTH_LONG).show();
                        break;
                    case R.id.hide_message:
                        if(mContext instanceof MainActivity) {
                            ((MainActivity)mContext).hide(mPosition);
                            ((MainActivity)mContext).mDeleted.add(mPosition);
                        }
                        else {
                            ((ArchiveActivity)mContext).hide(mPosition);
                        }
                        break;
                    case R.id.destroy_message:
                        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                switch (which){
                                    case DialogInterface.BUTTON_POSITIVE:
                                        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
                                        int delay = Integer.parseInt(sharedPref.getString(SettingsFragment.DELIVERY_INTERVAL, "10"));
                                        int attempts = Integer.parseInt(sharedPref.getString(SettingsFragment.DELIVERY_ATTEMPTS, "2"));
                                        WeakReference<Context> context = new WeakReference<>(mContext);
                                        AppDatabase db = Room.databaseBuilder(context.get(), AppDatabase.class, "crushare").allowMainThreadQueries().fallbackToDestructiveMigration().build();
                                        String uuid = sharedPref.getString(SettingsFragment.INSTANCE_ID, UUID.randomUUID().toString());
                                        if(mContext instanceof MainActivity) {
                                            int hashToDelete = ((MainActivity)mContext).mIncomingHashCodes.get(MyWebViewOnLongClickListener.this.mPosition);
                                            String destroyContent = "DESTROY "+uuid+" "+String.valueOf(hashToDelete);
                                            Message destroyCommand = new Message(destroyContent.getBytes(),"text/plain");
                                            new Postman(mContext, destroyCommand, delay, attempts).run();
                                            ((MainActivity)mContext).hide(MyWebViewOnLongClickListener.this.mPosition);
                                            ((MainActivity)mContext).mDeleted.add(MyWebViewOnLongClickListener.this.mPosition);
                                            db.archivedMessageDao().delete(hashToDelete);
                                        }
                                        else {
                                            int hashToDelete = ((ArchiveActivity)mContext).mHashCodes.get(MyWebViewOnLongClickListener.this.mPosition);
                                            String destroyContent = "DESTROY "+uuid+" "+String.valueOf(hashToDelete);
                                            Message destroyCommand = new Message(destroyContent.getBytes(),"text/plain");
                                            new Postman(mContext, destroyCommand, delay, attempts).run();
                                            ((ArchiveActivity)mContext).hide(MyWebViewOnLongClickListener.this.mPosition);
                                            db.archivedMessageDao().delete(hashToDelete);
                                            if(0 == db.archivedMessageDao().getArchiveSize()) new ArchiveActivity.Delete(mContext).execute();
                                        }

                                        break;

                                    case DialogInterface.BUTTON_NEGATIVE:
                                        break;
                                }
                            }
                        };

                        AlertDialog.Builder cdBuilder = new AlertDialog.Builder(mContext);
                        cdBuilder.setTitle(mContext.getString(R.string.confirm_destroy_title )).setMessage(mContext.getString(R.string.confirm_destroy_content)).setPositiveButton("Yes", dialogClickListener)
                                .setNegativeButton("No", dialogClickListener).show();


                        break;

                    default:
                        db.close();
                        return false;
                }
                db.close();
                return true;
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
                                                }
                                            }).build();
                                    Nearby.getMessagesClient(mContext)
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
        public boolean onLongClick(View arg0) {

            WeakReference<Context> context = new WeakReference<>(mContext);
            AppDatabase db = Room.databaseBuilder(context.get(), AppDatabase.class, "crushare").allowMainThreadQueries().fallbackToDestructiveMigration().build();
            List<ArchivedMessage> messageByHash = new ArrayList<>();
            if(mContext instanceof MainActivity) {
                messageByHash = db.archivedMessageDao().getMessageByHash(((MainActivity)mContext).mIncomingHashCodes.get(mPosition));
            }
            else if(mContext instanceof ArchiveActivity) {
                messageByHash = db.archivedMessageDao().getMessageByHash(((ArchiveActivity)mContext).mHashCodes.get(mPosition));
            }

            if(!messageByHash.isEmpty()) {
                PopupMenu popup = new PopupMenu(mContext, arg0);
                MenuInflater inflater = popup.getMenuInflater();
                inflater.inflate(R.menu.message, popup.getMenu());
                if (mContext instanceof MainActivity || mContext instanceof ArchiveActivity) {
                    popup.setOnMenuItemClickListener(new MyWebViewMenuItemClickListener(mPosition));
                }
                popup.show();
                return false;
            }

            return false;

        }

    }

    @NonNull
    @Override
    public View getView(final int position, @Nullable View convertView, @NonNull ViewGroup parent) {

        View listItem = convertView;
        if (listItem == null)
            listItem = LayoutInflater.from(mContext).inflate(R.layout.webview, parent, false);

        final View fListItem = listItem;

        boolean outgoing = messages.get(position).startsWith("#outgoing ");
        boolean preview = messages.get(position).startsWith("#preview ");
        String tMessage = messages.get(position);
        if (outgoing) tMessage = tMessage.substring("#outgoing ".length());
        if (preview) tMessage = tMessage.substring("#preview ".length());
        final String message = tMessage;

        viewMessages.put(fListItem, message);
        final WebView messageView = fListItem.findViewById(R.id.message);
        if (getShape(message) != null) messageView.setBackground(getShape(message));

        messageView.setOnLongClickListener(new MyWebViewOnLongClickListener(position));

        int margin = 200;
        if (outgoing) {
            setMargins(fListItem.findViewById(R.id.message), margin, 0);
            setMargins(fListItem.findViewById(R.id.progress), margin, 0);
            setMargins(fListItem.findViewById(R.id.error), margin, 0);
            setMargins(fListItem.findViewById(R.id.js), margin, 0);
            setMargins(fListItem.findViewById(R.id.console), margin, 0);
        } else if (!preview) {
            setMargins(fListItem.findViewById(R.id.message), 0, margin);
            setMargins(fListItem.findViewById(R.id.progress), 0, margin);
            setMargins(fListItem.findViewById(R.id.error), 0, margin);
            setMargins(fListItem.findViewById(R.id.js), 0, margin);
            setMargins(fListItem.findViewById(R.id.console), 0, margin);
        }
        messageView.setBackgroundColor(Color.TRANSPARENT);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
        boolean allowContentAccess = sharedPref.getBoolean(SettingsFragment.INLINE_IMAGES, true);
        boolean displayVideo = sharedPref.getBoolean(SettingsFragment.INLINE_VIDEOS, true);
        boolean executeJavascript = sharedPref.getBoolean(SettingsFragment.EXECUTE_JAVASCRIPT, false);
        boolean safeBrowsing = sharedPref.getBoolean(SettingsFragment.SAFE_BROWSING, true);
        WebSettings settings = messageView.getSettings();
        settings.setAllowContentAccess(allowContentAccess);
        settings.setJavaScriptEnabled(executeJavascript);
        if(displayVideo) {
            settings.setPluginState(WebSettings.PluginState.ON);
        }

        if (Build.VERSION.SDK_INT >= 26) {
            settings.setSafeBrowsingEnabled(safeBrowsing);
        }

        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        messageView.setWebChromeClient(new WebChromeClient() {

            public void onProgressChanged(WebView view, int progress) {
                ((TextView) fListItem.findViewById(R.id.progress)).setText(String.format(mContext.getString(R.string.loading), String.valueOf(progress)));
                if (progress == 100) {
                    fListItem.findViewById(R.id.progress).setVisibility(View.GONE);
                }
            }

            public void onCloseWindow(WebView w) {
                super.onCloseWindow(w);
                for (int index = 0; index < ((ViewGroup) fListItem).getChildCount(); ++index) {
                    ((ViewGroup) fListItem).getChildAt(index).setVisibility(View.GONE);
                }
                messages.remove(viewMessages.get(fListItem));
                notifyDataSetChanged();
            }

            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                fListItem.findViewById(R.id.console).setVisibility(View.VISIBLE);
                if (getShape(messages.get(position)) != null)
                    fListItem.findViewById(R.id.console).setBackground(getShape(messages.get(position)));
                ((TextView) fListItem.findViewById(R.id.console)).setText(mContext.getString(R.string.webview_console, consoleMessage.messageLevel().toString(), consoleMessage.message()));
                return true;
            }

            public boolean onJsAlert(WebView view, String url, final String message, final JsResult result) {
                fListItem.findViewById(R.id.js).setVisibility(View.VISIBLE);
                if (getShape(messages.get(position)) != null)
                    fListItem.findViewById(R.id.js).setBackground(getShape(messages.get(position)));
                ((TextView) fListItem.findViewById(R.id.js)).setText(mContext.getString(R.string.js_alert));
                fListItem.findViewById(R.id.js).setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        new AlertDialog.Builder(mContext)
                                .setTitle(mContext.getString(R.string.web_mex_alert))
                                .setMessage(message)
                                .setPositiveButton(android.R.string.ok,
                                        new AlertDialog.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int which) {
                                                result.confirm();
                                                fListItem.findViewById(R.id.js).setVisibility(View.GONE);
                                            }
                                        }).setCancelable(false).create().show();

                    }

                });
                return true;
            }

            public boolean onJsConfirm(WebView view, String url, final String message, final JsResult result) {
                fListItem.findViewById(R.id.js).setVisibility(View.VISIBLE);
                if (getShape(messages.get(position)) != null)
                    fListItem.findViewById(R.id.js).setBackground(getShape(messages.get(position)));
                ((TextView) fListItem.findViewById(R.id.js)).setText(mContext.getString(R.string.js_confirm));
                fListItem.findViewById(R.id.js).setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        new AlertDialog.Builder(mContext)
                                .setTitle(mContext.getString(R.string.web_mex_confirmation))
                                .setMessage(message)
                                .setCancelable(true)
                                .setPositiveButton(android.R.string.yes,
                                        new AlertDialog.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int which) {
                                                result.confirm();
                                                fListItem.findViewById(R.id.js).setVisibility(View.GONE);
                                            }
                                        })
                                .setNegativeButton(android.R.string.no,
                                        new AlertDialog.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int which) {
                                                result.cancel();
                                                fListItem.findViewById(R.id.js).setVisibility(View.GONE);
                                            }
                                        })
                                .create()
                                .show();
                    }
                });
                return true;
            }

            public boolean onJsPrompt(final WebView view, String url, final String message, final String defaultValue, final JsPromptResult result) {
                fListItem.findViewById(R.id.js).setVisibility(View.VISIBLE);
                if (getShape(messages.get(position)) != null)
                    fListItem.findViewById(R.id.js).setBackground(getShape(messages.get(position)));
                ((TextView) fListItem.findViewById(R.id.js)).setText(mContext.getString(R.string.js_prompt));
                fListItem.findViewById(R.id.js).setOnClickListener(new View.OnClickListener() {
                    public void onClick(View ov) {

                        final LayoutInflater factory = LayoutInflater.from(mContext);
                        final View v = factory.inflate(R.layout.javascript_prompt_dialog, view, false);

                        ((TextView) v.findViewById(R.id.prompt_message_text)).setText(message);
                        ((EditText) v.findViewById(R.id.prompt_input_field)).setText(defaultValue);

                        new AlertDialog.Builder(mContext)
                                .setTitle(R.string.web_mex_prompt)
                                .setCancelable(true)
                                .setView(v)
                                .setPositiveButton(android.R.string.ok,
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int whichButton) {
                                                String value = ((EditText) v.findViewById(R.id.prompt_input_field)).getText().toString();
                                                result.confirm(value);
                                                fListItem.findViewById(R.id.js).setVisibility(View.GONE);
                                            }
                                        })
                                .setNegativeButton(android.R.string.cancel,
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int whichButton) {
                                                result.cancel();
                                                fListItem.findViewById(R.id.js).setVisibility(View.GONE);
                                            }
                                        })
                                .setOnCancelListener(
                                        new DialogInterface.OnCancelListener() {
                                            public void onCancel(DialogInterface dialog) {
                                                result.cancel();
                                                fListItem.findViewById(R.id.js).setVisibility(View.GONE);
                                            }
                                        })
                                .create()
                                .show();

                    }
                });
                return true;
            }

        });

        messageView.setWebViewClient(new WebViewClient() {

            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                fListItem.findViewById(R.id.error).setVisibility(View.VISIBLE);
                if (getShape(messages.get(position)) != null)
                    fListItem.findViewById(R.id.error).setBackground(getShape(messages.get(position)));
                ((TextView) fListItem.findViewById(R.id.error)).setText(String.format(mContext.getString(R.string.webview_error), errorCode, description));
            }

            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && url.startsWith("settings://display.now")) {
                    view.getContext().startActivity(new Intent(view.getContext(), SettingsActivity.class));
                    return true;
                }
                else if (url != null && url.startsWith("display://")) {
                    view.getContext().startActivity(new Intent().setData(Uri.parse(url)));
                    return true;
                }
                else if(url != null && url.startsWith("video://")) {
                    view.getContext().startActivity(new Intent().setDataAndType(Uri.parse(url.replace("video://","")), "video/avc"));
                    return true;
                } else if ("connect://the.message.receiver".equals(url) && view.getContext() instanceof MainActivity) {
                    ((MainActivity) view.getContext()).connect();
                    return true;
                } else if ("disconnect://the.message.receiver".equals(url) && view.getContext() instanceof MainActivity) {
                    ((MainActivity) view.getContext()).disconnect(false);
                    return true;
                } else if ("share://for.old.devices".equals(url) && view.getContext() instanceof MainActivity) {
                    ((MainActivity) view.getContext()).share();
                    return true;
                } else if(url != null && url.startsWith("iarchive://") && view.getContext() instanceof MainActivity ) {
                    if(((MainActivity) view.getContext()).mArchiveSession == 0) {
                        Toast.makeText(view.getContext(),view.getContext().getString(R.string.end_of_archive_reached),Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    WeakReference<Context> mContext = new WeakReference<>(view.getContext());
                    AppDatabase db = Room.databaseBuilder(mContext.get(),
                            AppDatabase.class, "crushare").allowMainThreadQueries().fallbackToDestructiveMigration().build();
                    List<ArchivedMessage> messages = db.archivedMessageDao().getArchivedMessagesDesc(((MainActivity)view.getContext()).mArchiveSession);
                    if(!messages.isEmpty()) {
                        SparseIntArray mIncomingHashCodes = new SparseIntArray();
                        for (int i = 0; i < ((MainActivity) view.getContext()).mIncomingHashCodes.size(); i++) {
                            mIncomingHashCodes.put(messages.size() + ((MainActivity) view.getContext()).mIncomingHashCodes.keyAt(i), ((MainActivity) view.getContext()).mIncomingHashCodes.valueAt(i));
                        }
                        ((MainActivity) view.getContext()).mIncomingHashCodes = mIncomingHashCodes;
                        int alreadyAdded = 0;
                        for (ArchivedMessage archivedMessage : messages) {
                            Message message = new Message(archivedMessage.getMessageContent(), archivedMessage.getMessageType());
                            add(mContext, messages.size() - alreadyAdded, message, archivedMessage.isOutgoing(), archivedMessage.getSignature(), archivedMessage.getAuthor());
                            alreadyAdded++;
                        }

                        ListView lv = ((MainActivity) view.getContext()).findViewById(R.id.incoming);
                        int index = lv.getFirstVisiblePosition();
                        View v = lv.getChildAt(0);
                        int top = (v == null) ? 0 : v.getTop();
                        ((MainActivity) view.getContext()).redraw();
                        lv.setSelectionFromTop(index, top);
                        try {
                            ((MainActivity) view.getContext()).mArchiveSession = db.archivedMessageDao().prevSessionId(((MainActivity) view.getContext()).mArchiveSession);
                        } catch(Exception e) {
                            ((MainActivity) view.getContext()).mArchiveSession = 0;
                        }
                    }
                    return true;
                }
                else if(url != null && url.startsWith("move://to.previous.of/") && view.getContext() instanceof MainActivity ) {
                    String currentId = url.replace("move://to.previous.of/","").substring(0, url.replace("move://to.previous.of/","").indexOf("/"));
                    String index = url.substring(1+url.lastIndexOf("/"));
                    Integer iIndex = Integer.parseInt(index);
                    for(int x = 0; x < ((MainActivity)view.getContext()).mDeleted.size(); x++) {
                        if(((MainActivity)view.getContext()).mDeleted.get(x) < Integer.parseInt(index)) {
                            iIndex--;
                        }
                    }
                    index = String.valueOf(iIndex);
                    ((MainActivity)view.getContext()).mDeleted.clear();
                    WeakReference<Context> mContext = new WeakReference<>(view.getContext());
                    AppDatabase db = Room.databaseBuilder(mContext.get(), AppDatabase.class, "crushare").allowMainThreadQueries().fallbackToDestructiveMigration().build();
                    ArchivedMessage messageToDisplay = db.archivedMessageDao().getPrevMexById(Long.parseLong(currentId)).get(0);
                    String signature = messageToDisplay.getSignature();
                    ArchivedMessage prevOfMessageToDisplay;
                    String prevOfPrevAuthorIfImg;
                    String prevAuthorIfImg;
                    String prevOfPrevAuthorIfTxt;
                    String prevAuthorIfTxt;
                    try {
                        prevOfMessageToDisplay = db.archivedMessageDao().getPrevMexById(messageToDisplay.getMessageId()).get(0);
                        prevOfPrevAuthorIfImg = prevOfMessageToDisplay.getMessageType().substring(0, (prevOfMessageToDisplay.getMessageType()+".").indexOf("."));
                        prevAuthorIfImg = messageToDisplay.getMessageType().substring(0, (messageToDisplay.getMessageType()+".").indexOf("."));
                        prevOfPrevAuthorIfTxt = new String(prevOfMessageToDisplay.getMessageContent()).substring(Math.max(new String(prevOfMessageToDisplay.getMessageContent()).indexOf("<meta name=\"author\" content=\""),0), Math.max(new String(prevOfMessageToDisplay.getMessageContent()).indexOf(">",Math.max(new String(prevOfMessageToDisplay.getMessageContent()).indexOf("<meta name=\"author\" content=\""),0)),0));
                        prevAuthorIfTxt = new String(messageToDisplay.getMessageContent()).substring(Math.max(new String(messageToDisplay.getMessageContent()).indexOf("<meta name=\"author\" content=\""),0), Math.max(new String(messageToDisplay.getMessageContent()).indexOf(">",Math.max(new String(messageToDisplay.getMessageContent()).indexOf("<meta name=\"author\" content=\""),0)),0));
                    }
                    catch(Exception e) {
                        prevOfMessageToDisplay = null;
                        prevOfPrevAuthorIfImg = null;
                        prevAuthorIfImg = null;
                        prevOfPrevAuthorIfTxt = null;
                        prevAuthorIfTxt = null;
                    }
                     if(prevOfMessageToDisplay != null && messageToDisplay.getSessionId() == prevOfMessageToDisplay.getSessionId() && ( ( prevOfPrevAuthorIfImg.equals(prevAuthorIfImg) && !"text/plain".equals(prevOfPrevAuthorIfImg)) || prevOfPrevAuthorIfImg.equals(prevAuthorIfTxt) || prevOfPrevAuthorIfTxt.equals(prevAuthorIfImg) || prevOfPrevAuthorIfTxt.equals(prevAuthorIfTxt))) {
                        if("text/plain".equals(messageToDisplay.getMessageType())) {
                            String html = new String(messageToDisplay.getMessageContent());
                            html = html.replace("</body>","<div style=\"position: absolute; top:12px; width:91%;\"><div style=\"float:right;\"><a style=\"font-size:x-small; text-decoration:none !important; color: #3F51B5; margin-right:12px;\" title=\"down\" href=\"move://to.next.of/"+messageToDisplay.getMessageId()+"/in/"+index+"\">&#9660;</a><a style=\"font-size: x-small; margin-right:6px; text-decoration:none !important; color: #3F51B5; \" title=\"up\" href=\"move://to.previous.of/"+messageToDisplay.getMessageId()+"/in/"+index+"\">&#9650;</a></div></div></body>");
                            ((MainActivity) view.getContext()).mIncoming.remove(Integer.parseInt(index));
                            ((MainActivity) view.getContext()).mIncoming.add(Integer.parseInt(index),html);
                            ((MainActivity) view.getContext()).mIncomingHashCodes.put(Integer.parseInt(index),messageToDisplay.getMexHash());
                            ((MainActivity) view.getContext()).redraw();
                        }
                        else {
                            String uuid = messageToDisplay.getAuthor();
                            /*DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(view.getContext());
                            DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(view.getContext());
                            String datetime = "<div class=\"datetime\">" + dateFormat.format(new Date(Long.valueOf(messageToDisplay.getMessageType().substring(1 + messageToDisplay.getMessageType().indexOf("."), messageToDisplay.getMessageType().lastIndexOf("."))))) + " " + timeFormat.format(new Date(Long.valueOf(messageToDisplay.getMessageType().substring(1 + messageToDisplay.getMessageType().indexOf("."), messageToDisplay.getMessageType().lastIndexOf("."))))) + "</div>";
                            */
                            Date mexDateTimeObj = new Date(Long.valueOf(messageToDisplay.getMessageType().substring(1 + messageToDisplay.getMessageType().indexOf("."), messageToDisplay.getMessageType().lastIndexOf("."))));
                            String datetime;
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                                DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(view.getContext());
                                String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                                datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>";
                            }
                            else {
                                DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(view.getContext());
                                DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(view.getContext());
                                datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj)+"</div>";
                            }

                            String html;
                            try {
                                File tmp = File.createTempFile(view.getContext().getString(R.string.incoming_file_prefix), messageToDisplay.getMessageType().substring(messageToDisplay.getMessageType().lastIndexOf(".")), view.getContext().getCacheDir());
                                FileOutputStream tmpos = new FileOutputStream(tmp);
                                tmpos.write(messageToDisplay.getMessageContent());
                                tmpos.flush();
                                tmpos.close();
                                Uri tmpUri = getUriForFile(view.getContext(), "it.mircosoderi.fileprovider", tmp);
                                view.getContext().grantUriPermission(view.getContext().getPackageName(), tmpUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(view.getContext());
                                if (getMimeType(tmpUri.toString()).startsWith("image/") && sharedPref.getBoolean(SettingsFragment.INLINE_IMAGES, true)) {
                                    html = "<html><head><meta name=\"author\" content=\"" + uuid + "\"><title>" + tmp.getName() + "</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>" + datetime +"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+mContext.get().getString(R.string.media_warning)+"</p>"+ "<p>" + "Put a look at this <a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + getMimeType(tmpUri.toString()).substring(1 + getMimeType(tmpUri.toString()).indexOf("/")) + " " + getMimeType(tmpUri.toString()).substring(0, getMimeType(tmpUri.toString()).indexOf("/")) + "</b></a>!" + "</p>" + (!signature.isEmpty()?"<p>"+mContext.get().getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"")+ "<div style=\"position: absolute; top:12px; width:91%;\"><div style=\"float:right;\"><a style=\"font-size:x-small; text-decoration:none !important; color: #3F51B5; margin-right:12px;\" title=\"down\" href=\"move://to.next.of/" + String.valueOf(messageToDisplay.getMessageId()) + "/in/" + index + "\">&#9660;</a><a style=\"font-size:x-small; margin-right:6px; text-decoration:none !important; color: #3F51B5; \" title=\"up\" href=\"move://to.previous.of/" + messageToDisplay.getMessageId() + "/in/" + index + "\">&#9650;</a></div></div>" + "<p><object style=\"width:100%;\" data=\"" + tmpUri.toString() + "\" type=\"" + getMimeType(tmpUri.toString()) + "\" /></p></body></html>";
                                } else {
                                    html = "<html><head><meta name=\"author\" content=\"" + uuid + "\"><title>" + tmp.getName() + "</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>" + datetime +"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+mContext.get().getString(R.string.media_warning)+"</p>"+ "<p>Put a look at this <a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + getMimeType(tmpUri.toString()).substring(1 + getMimeType(tmpUri.toString()).indexOf("/")) + " " + getMimeType(tmpUri.toString()).substring(0, getMimeType(tmpUri.toString()).indexOf("/")) + "</b></a>!</p>"+ (!signature.isEmpty()?"<p>"+mContext.get().getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"")+ "<div style=\"position: absolute; top:12px; width:91%;\"><div style=\"float:right;\"><a style=\"font-size:x-small; text-decoration:none !important; color: #3F51B5; margin-right:12px;\" title=\"down\" href=\"move://to.next.of/" + String.valueOf(messageToDisplay.getMessageId()) + "/in/" + index + "\">&#9660;</a><a style=\"font-size:x-small; margin-right:6px; text-decoration:none !important; color: #3F51B5; \" title=\"up\" href=\"move://to.previous.of/" + messageToDisplay.getMessageId() + "/in/" + index + "\">&#9650;</a></div></div>"+"</body></html>";
                                }
                            }
                            catch(Exception e){
                                html = "<html><head><meta name=\"author\" content=\"" + uuid + "\"><title>Unable to display image</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>" + datetime + "<p style=\"color:#3F51B5;\">Something wrong happened. The image preview cannot be displayed. We apologize for the inconvenience.</p></body></html>";
                            }
                            ((MainActivity) view.getContext()).mIncoming.remove(Integer.parseInt(index));
                            ((MainActivity) view.getContext()).mIncoming.add(Integer.parseInt(index),html);
                            ((MainActivity) view.getContext()).mIncomingHashCodes.put(Integer.parseInt(index),messageToDisplay.getMexHash());
                            ((MainActivity) view.getContext()).redraw();
                        }
                    }
                    else {
                        if("text/plain".equals(messageToDisplay.getMessageType())) {
                            String html = new String(messageToDisplay.getMessageContent());
                            html = html.replace("</body>","<div style=\"position: absolute; top:12px; width:91%;\"><a style=\"font-size:x-small; margin-right: 6px; float:right; text-decoration:none !important; color: #3F51B5; \" title=\"down\" href=\"move://to.next.of/"+String.valueOf(messageToDisplay.getMessageId())+"/in/"+index+"\">&#9660;</a></div></body>");
                            ((MainActivity) view.getContext()).mIncoming.remove(Integer.parseInt(index));
                            ((MainActivity) view.getContext()).mIncoming.add(Integer.parseInt(index),html);
                            ((MainActivity) view.getContext()).mIncomingHashCodes.put(Integer.parseInt(index),messageToDisplay.getMexHash());
                            ((MainActivity) view.getContext()).redraw();
                        }
                        else {
                            String uuid = messageToDisplay.getAuthor();
                            /*DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(view.getContext());
                            DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(view.getContext());
                            String datetime = "<div class=\"datetime\">" + dateFormat.format(new Date(Long.valueOf(messageToDisplay.getMessageType().substring(1 + messageToDisplay.getMessageType().indexOf("."), messageToDisplay.getMessageType().lastIndexOf("."))))) + " " + timeFormat.format(new Date(Long.valueOf(messageToDisplay.getMessageType().substring(1 + messageToDisplay.getMessageType().indexOf("."), messageToDisplay.getMessageType().lastIndexOf("."))))) + "</div>";*/
                            Date mexDateTimeObj = new Date(Long.valueOf(messageToDisplay.getMessageType().substring(1 + messageToDisplay.getMessageType().indexOf("."), messageToDisplay.getMessageType().lastIndexOf("."))));
                            String datetime;
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                                DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(view.getContext());
                                String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                                datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>";
                            }
                            else {
                                DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(view.getContext());
                                DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(view.getContext());
                                datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj)+"</div>";
                            }
                            String html;
                            try {
                                File tmp = File.createTempFile(view.getContext().getString(R.string.incoming_file_prefix), messageToDisplay.getMessageType().substring(messageToDisplay.getMessageType().lastIndexOf(".")), view.getContext().getCacheDir());
                                FileOutputStream tmpos = new FileOutputStream(tmp);
                                tmpos.write(messageToDisplay.getMessageContent());
                                tmpos.flush();
                                tmpos.close();
                                Uri tmpUri = getUriForFile(view.getContext(), "it.mircosoderi.fileprovider", tmp);
                                view.getContext().grantUriPermission(view.getContext().getPackageName(), tmpUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(view.getContext());
                                if (getMimeType(tmpUri.toString()).startsWith("image/") && sharedPref.getBoolean(SettingsFragment.INLINE_IMAGES, true)) {
                                    html = "<html><head><meta name=\"author\" content=\"" + uuid + "\"><title>" + tmp.getName() + "</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>" + datetime +"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+mContext.get().getString(R.string.media_warning)+"</p>"+ "<p>" + "Put a look at this <a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + getMimeType(tmpUri.toString()).substring(1 + getMimeType(tmpUri.toString()).indexOf("/")) + " " + getMimeType(tmpUri.toString()).substring(0, getMimeType(tmpUri.toString()).indexOf("/")) + "</b></a>!" + "</p>" + (!signature.isEmpty()?"<p>"+mContext.get().getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"")+ "<div style=\"position: absolute; top:12px; width:91%;\"><a style=\"font-size:x-small; margin-right:6px; float:right; text-decoration:none !important; color: #3F51B5; \" title=\"down\" href=\"move://to.next.of/"+String.valueOf(messageToDisplay.getMessageId())+"/in/"+index+"\">&#9660;</a></div>" + "<p><object style=\"width:100%;\" data=\"" + tmpUri.toString() + "\" type=\"" + getMimeType(tmpUri.toString()) + "\" /></p></body></html>";
                                } else {
                                    html = "<html><head><meta name=\"author\" content=\"" + uuid + "\"><title>" + tmp.getName() + "</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>" + datetime +"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+mContext.get().getString(R.string.media_warning)+"</p>"+ "<p>Put a look at this <a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + getMimeType(tmpUri.toString()).substring(1 + getMimeType(tmpUri.toString()).indexOf("/")) + " " + getMimeType(tmpUri.toString()).substring(0, getMimeType(tmpUri.toString()).indexOf("/")) + "</b></a>!</p> " + (!signature.isEmpty()?"<p>"+mContext.get().getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"")+ "<div style=\"position: absolute; top:12px; width:91%;\"><a style=\"font-size:x-small; margin-right:6px; float:right; text-decoration:none !important; color: #3F51B5; \" title=\"down\" href=\"move://to.next.of/"+String.valueOf(messageToDisplay.getMessageId())+"/in/"+index+"\">&#9660;</a></div>"+" </body></html>";
                                }
                            }
                            catch(Exception e){
                                html = "<html><head><meta name=\"author\" content=\"" + uuid + "\"><title>Unable to display image</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>" + datetime + "<p style=\"color:#3F51B5;\">Something wrong happened. The image preview cannot be displayed. We apologize for the inconvenience.</p></body></html>";
                            }
                            ((MainActivity) view.getContext()).mIncoming.remove(Integer.parseInt(index));
                            ((MainActivity) view.getContext()).mIncoming.add(Integer.parseInt(index),html);
                            ((MainActivity) view.getContext()).mIncomingHashCodes.put(Integer.parseInt(index),messageToDisplay.getMexHash());
                            ((MainActivity) view.getContext()).redraw();
                        }
                    }
                    return true;
                }
                else if(url != null && url.startsWith("move://to.next.of/") && view.getContext() instanceof MainActivity ) {
                    String currentId = url.replace("move://to.next.of/","").substring(0, url.replace("move://to.next.of/","").indexOf("/"));
                    String index = url.substring(1+url.lastIndexOf("/"));
                    Integer iIndex = Integer.parseInt(index);
                    for(int x = 0; x < ((MainActivity)view.getContext()).mDeleted.size(); x++) {
                        if(((MainActivity)view.getContext()).mDeleted.get(x) < Integer.parseInt(index)) {
                            iIndex--;
                        }
                    }
                    index = String.valueOf(iIndex);
                    ((MainActivity)view.getContext()).mDeleted.clear();
                    WeakReference<Context> mContext = new WeakReference<>(view.getContext());
                    AppDatabase db = Room.databaseBuilder(mContext.get(),
                    AppDatabase.class, "crushare").allowMainThreadQueries().fallbackToDestructiveMigration().build();
                    final ArchivedMessage messageToDisplay = db.archivedMessageDao().getNextMexById(Long.parseLong(currentId)).get(0);
                    String signature = messageToDisplay.getSignature();
                    ArchivedMessage nextOfMessageToDisplay;
                    String nextOfNextAuthorIfImg;
                    String nextAuthorIfImg;
                    String nextOfNextAuthorIfTxt;
                    String nextAuthorIfTxt;
                    try {
                        nextOfMessageToDisplay = db.archivedMessageDao().getNextMexById(messageToDisplay.getMessageId()).get(0);
                        nextOfNextAuthorIfImg = nextOfMessageToDisplay.getMessageType().substring(0, (nextOfMessageToDisplay.getMessageType()+".").indexOf("."));
                        nextAuthorIfImg = messageToDisplay.getMessageType().substring(0, (messageToDisplay.getMessageType()+".").indexOf("."));
                        nextOfNextAuthorIfTxt = new String(nextOfMessageToDisplay.getMessageContent()).substring(Math.max(new String(nextOfMessageToDisplay.getMessageContent()).indexOf("<meta name=\"author\" content=\""),0), Math.max(new String(nextOfMessageToDisplay.getMessageContent()).indexOf(">",Math.max(new String(nextOfMessageToDisplay.getMessageContent()).indexOf("<meta name=\"author\" content=\""),0)),0));
                        nextAuthorIfTxt = new String(messageToDisplay.getMessageContent()).substring(Math.max(new String(messageToDisplay.getMessageContent()).indexOf("<meta name=\"author\" content=\""),0), Math.max(new String(messageToDisplay.getMessageContent()).indexOf(">",Math.max(new String(messageToDisplay.getMessageContent()).indexOf("<meta name=\"author\" content=\""),0)),0));
                    }
                    catch(Exception e) {
                        nextOfMessageToDisplay = null;
                        nextOfNextAuthorIfImg = null;
                        nextAuthorIfImg = null;
                        nextOfNextAuthorIfTxt = null;
                        nextAuthorIfTxt = null;
                    }
                    if(nextOfMessageToDisplay != null && ((nextOfNextAuthorIfImg.equals(nextAuthorIfImg) && !"text/plain".equals(nextOfNextAuthorIfImg)) || nextOfNextAuthorIfImg.equals(nextAuthorIfTxt) || nextOfNextAuthorIfTxt.equals(nextAuthorIfImg) || nextOfNextAuthorIfTxt.equals(nextAuthorIfTxt))) {
                        if("text/plain".equals(messageToDisplay.getMessageType())) {
                            String html = new String(messageToDisplay.getMessageContent());
                            html = html.replace("</html>","<div style=\"position: absolute; top:12px; width:91%;\"><div style=\"float:right;\"><a style=\" font-size:x-small; text-decoration:none !important; color: #3F51B5; margin-right:12px;\" title=\"down\" href=\"move://to.next.of/"+messageToDisplay.getMessageId()+"/in/"+index+"\">&#9660;</a><a style=\"font-size:x-small; margin-right: 6px; text-decoration:none !important; color: #3F51B5; \" title=\"up\" href=\"move://to.previous.of/"+messageToDisplay.getMessageId()+"/in/"+index+"\">&#9650;</a></div></div></html>");
                            ((MainActivity) view.getContext()).mIncoming.remove(Integer.parseInt(index));
                            ((MainActivity) view.getContext()).mIncoming.add(Integer.parseInt(index),html);
                            ((MainActivity) view.getContext()).mIncomingHashCodes.put(Integer.parseInt(index),messageToDisplay.getMexHash());
                            ((MainActivity) view.getContext()).redraw();
                        }
                        else {
                            String uuid = messageToDisplay.getAuthor();
                            /*DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(view.getContext());
                            DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(view.getContext());
                            String datetime = "<div class=\"datetime\">" + dateFormat.format(new Date(Long.valueOf(messageToDisplay.getMessageType().substring(1 + messageToDisplay.getMessageType().indexOf("."), messageToDisplay.getMessageType().lastIndexOf("."))))) + " " + timeFormat.format(new Date(Long.valueOf(messageToDisplay.getMessageType().substring(1 + messageToDisplay.getMessageType().indexOf("."), messageToDisplay.getMessageType().lastIndexOf("."))))) + "</div>";*/
                            Date mexDateTimeObj = new Date(Long.valueOf(messageToDisplay.getMessageType().substring(1 + messageToDisplay.getMessageType().indexOf("."), messageToDisplay.getMessageType().lastIndexOf("."))));
                            String datetime;
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                                DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(view.getContext());
                                String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                                datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>";
                            }
                            else {
                                DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(view.getContext());
                                DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(view.getContext());
                                datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj)+"</div>";
                            }
                            String html;
                            try {
                                File tmp = File.createTempFile(view.getContext().getString(R.string.incoming_file_prefix), messageToDisplay.getMessageType().substring(messageToDisplay.getMessageType().lastIndexOf(".")), view.getContext().getCacheDir());
                                FileOutputStream tmpos = new FileOutputStream(tmp);
                                tmpos.write(messageToDisplay.getMessageContent());
                                tmpos.flush();
                                tmpos.close();
                                Uri tmpUri = getUriForFile(view.getContext(), "it.mircosoderi.fileprovider", tmp);
                                view.getContext().grantUriPermission(view.getContext().getPackageName(), tmpUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(view.getContext());
                                if (getMimeType(tmpUri.toString()).startsWith("image/") && sharedPref.getBoolean(SettingsFragment.INLINE_IMAGES, true)) {
                                    html = "<html><head><meta name=\"author\" content=\"" + uuid + "\"><title>" + tmp.getName() + "</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>" + datetime +"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+mContext.get().getString(R.string.media_warning)+"</p>"+ "<p>Put a look at this " + "<a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + getMimeType(tmpUri.toString()).substring(1 + getMimeType(tmpUri.toString()).indexOf("/")) + " " + getMimeType(tmpUri.toString()).substring(0, getMimeType(tmpUri.toString()).indexOf("/")) + "</b></a>!" + "</p>" + (!signature.isEmpty()?"<p>"+mContext.get().getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"")+ "<div style=\"position: absolute; top:12px; width:91%;\"><div style=\"float:right;\"><a style=\"font-size:x-small; text-decoration:none !important; color: #3F51B5; margin-right:12px;\" title=\"down\" href=\"move://to.next.of/" + messageToDisplay.getMessageId() + "/in/" + index + "\">&#9660;</a><a style=\"font-size:x-small; margin-right:6px; text-decoration:none !important; color: #3F51B5; \" title=\"up\" href=\"move://to.previous.of/" + messageToDisplay.getMessageId() + "/in/" + index + "\">&#9650;</a></div></div>"+ "<p><object style=\"width:100%;\" data=\"" + tmpUri.toString() + "\" type=\"" + getMimeType(tmpUri.toString()) + "\" /></p></body></html>";
                                } else {
                                    html = "<html><head><meta name=\"author\" content=\"" + uuid + "\"><title>" + tmp.getName() + "</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>" + datetime +"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+mContext.get().getString(R.string.media_warning)+"</p>"+ "<p>Put a look at this <a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + getMimeType(tmpUri.toString()).substring(1 + getMimeType(tmpUri.toString()).indexOf("/")) + " " + getMimeType(tmpUri.toString()).substring(0, getMimeType(tmpUri.toString()).indexOf("/")) + "</b></a>!</p>" + (!signature.isEmpty()?"<p>"+mContext.get().getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"")+ "<div style=\"position: absolute; top:12px; width:91%;\"><div style=\"float:right;\"><a style=\"font-size:x-small; text-decoration:none !important; color: #3F51B5; margin-right:12px;\" title=\"down\" href=\"move://to.next.of/" + messageToDisplay.getMessageId() + "/in/" + index + "\">&#9660;</a><a style=\"font-size:x-small; margin-right:6px; text-decoration:none !important; color: #3F51B5; \" title=\"up\" href=\"move://to.previous.of/" + messageToDisplay.getMessageId() + "/in/" + index + "\">&#9650;</a></div></div>"+"</body></html>";
                                }
                            }
                            catch(Exception e){
                                html = "<html><head><meta name=\"author\" content=\"" + uuid + "\"><title>Unable to display image</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>" + datetime + "<p style=\"color:#3F51B5;\">Something wrong happened. The image preview cannot be displayed. We apologize for the inconvenience.</p></body></html>";
                            }
                            ((MainActivity) view.getContext()).mIncoming.remove(Integer.parseInt(index));
                            ((MainActivity) view.getContext()).mIncoming.add(Integer.parseInt(index),html);
                            ((MainActivity) view.getContext()).mIncomingHashCodes.put(Integer.parseInt(index),messageToDisplay.getMexHash());
                            ((MainActivity) view.getContext()).redraw();
                        }
                    }
                    else {
                        if("text/plain".equals(messageToDisplay.getMessageType())) {
                            String html = new String(messageToDisplay.getMessageContent());
                            html = html.replace("</html>","<div style=\"position: absolute; top:12px; width:91%;\"><a style=\"font-size:x-small; margin-right:6px; float:right; text-decoration:none !important; color: #3F51B5; \" title=\"up\" href=\"move://to.previous.of/"+messageToDisplay.getMessageId()+"/in/"+index+"\">&#9650;</a></div></html>");
                            ((MainActivity) view.getContext()).mIncoming.remove(Integer.parseInt(index));
                            ((MainActivity) view.getContext()).mIncoming.add(Integer.parseInt(index),html);
                            ((MainActivity) view.getContext()).mIncomingHashCodes.put(Integer.parseInt(index),messageToDisplay.getMexHash());
                            ((MainActivity) view.getContext()).redraw();
                        }
                        else {
                            String uuid = messageToDisplay.getAuthor();
                            /*DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(view.getContext());
                            DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(view.getContext());
                            String datetime = "<div class=\"datetime\">" + dateFormat.format(new Date(Long.valueOf(messageToDisplay.getMessageType().substring(1 + messageToDisplay.getMessageType().indexOf("."), messageToDisplay.getMessageType().lastIndexOf("."))))) + " " + timeFormat.format(new Date(Long.valueOf(messageToDisplay.getMessageType().substring(1 + messageToDisplay.getMessageType().indexOf("."), messageToDisplay.getMessageType().lastIndexOf("."))))) + "</div>";*/
                            Date mexDateTimeObj = new Date(Long.valueOf(messageToDisplay.getMessageType().substring(1 + messageToDisplay.getMessageType().indexOf("."), messageToDisplay.getMessageType().lastIndexOf("."))));
                            String datetime;
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                                DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(view.getContext());
                                String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                                datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ android.text.format.DateFormat.format(timeFormat,mexDateTimeObj)+"</div>";
                            }
                            else {
                                DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(view.getContext());
                                DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(view.getContext());
                                datetime = "<div class=\"datetime\">"+dateFormat.format(mexDateTimeObj)+" "+ timeFormat.format(mexDateTimeObj)+"</div>";
                            }
                            String html;
                            try {
                                File tmp = File.createTempFile(view.getContext().getString(R.string.incoming_file_prefix), messageToDisplay.getMessageType().substring(messageToDisplay.getMessageType().lastIndexOf(".")), view.getContext().getCacheDir());
                                FileOutputStream tmpos = new FileOutputStream(tmp);
                                tmpos.write(messageToDisplay.getMessageContent());
                                tmpos.flush();
                                tmpos.close();
                                Uri tmpUri = getUriForFile(view.getContext(), "it.mircosoderi.fileprovider", tmp);
                                view.getContext().grantUriPermission(view.getContext().getPackageName(), tmpUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(view.getContext());
                                if (getMimeType(tmpUri.toString()).startsWith("image/") && sharedPref.getBoolean(SettingsFragment.INLINE_IMAGES, true)) {
                                    html = "<html><head><meta name=\"author\" content=\"" + uuid + "\"><title>" + tmp.getName() + "</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>" + datetime +"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+mContext.get().getString(R.string.media_warning)+"</p>"+ "<p>Put a look at this " + "<a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + getMimeType(tmpUri.toString()).substring(1 + getMimeType(tmpUri.toString()).indexOf("/")) + " " + getMimeType(tmpUri.toString()).substring(0, getMimeType(tmpUri.toString()).indexOf("/")) + "</b></a>" + "</p>" + (!signature.isEmpty()?"<p>"+mContext.get().getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"")+ "<div style=\"position: absolute; top:12px; width:91%;\"><a style=\"font-size:x-small; margin-right:6px; float:right; text-decoration:none !important; color: #3F51B5; \" title=\"up\" href=\"move://to.previous.of/"+messageToDisplay.getMessageId()+"/in/"+index+"\">&#9650;</a></div>"+ "<p><object style=\"width:100%;\" data=\"" + tmpUri.toString() + "\" type=\"" + getMimeType(tmpUri.toString()) + "\" /></p></body></html>";
                                } else {
                                    html = "<html><head><meta name=\"author\" content=\"" + uuid + "\"><title>" + tmp.getName() + "</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>" + datetime +"<p style=\"border: thin dotted #3F51B5; padding: 1em; color: #3F51B5; \">"+mContext.get().getString(R.string.media_warning)+"</p>"+ "<p>Put a look at this <a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + getMimeType(tmpUri.toString()).substring(1 + getMimeType(tmpUri.toString()).indexOf("/")) + " " + getMimeType(tmpUri.toString()).substring(0, getMimeType(tmpUri.toString()).indexOf("/")) + "</b></a></p>" + (!signature.isEmpty()?"<p>"+mContext.get().getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"")+ "<div style=\"position: absolute; top:12px; width:91%;\"><a style=\"font-size:x-small; margin-right:6px; float:right; text-decoration:none !important; color: #3F51B5; \" title=\"up\" href=\"move://to.previous.of/"+messageToDisplay.getMessageId()+"/in/"+index+"\">&#9650;</a></div>"+"</body></html>";
                                }
                            }
                            catch(Exception e){
                                html = "<html><head><meta name=\"author\" content=\"" + uuid + "\"><title>Unable to display image</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>" + datetime + "<p style=\"color:#3F51B5;\">Something wrong happened. The image preview cannot be displayed. We apologize for the inconvenience.</p></body></html>";
                            }
                            ((MainActivity) view.getContext()).mIncoming.remove(Integer.parseInt(index));
                            ((MainActivity) view.getContext()).mIncoming.add(Integer.parseInt(index),html);
                            ((MainActivity) view.getContext()).mIncomingHashCodes.put(Integer.parseInt(index),messageToDisplay.getMexHash());
                            ((MainActivity) view.getContext()).redraw();
                        }
                    }
                    return true;
                }
                else {
                    if(view.getContext() instanceof ArchiveActivity) {
                        Toast.makeText(mContext, mContext.getString(R.string.action_disabled_in_archive),Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    return false;
                }
            }

            private void add(WeakReference<Context> mContext, int pos, Message message, boolean outgoing, String signature, String uuid) {
                ArrayList<String> mArchive = ((MainActivity)mContext.get()).mIncoming;
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
                        mArchive.add(0,txt);
                        ((MainActivity)mContext.get()).mIncomingHashCodes.put(pos, message.hashCode());
                    }
                }
                else {
                    try {

                        File tmp = File.createTempFile(mContext.get().getString(R.string.incoming_file_prefix), message.getType().substring(message.getType().lastIndexOf(".")), mContext.get().getCacheDir());
                        FileOutputStream tmpos = new FileOutputStream(tmp);
                        tmpos.write(message.getContent());
                        tmpos.flush();
                        tmpos.close();

                        /*
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext.get());
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
                            String newMex = "<html><head><meta name=\"author\" content=\""+uuid+"\"><title>"+tmp.getName()+"</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p>"+"<a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + getMimeType(tmpUri.toString()).substring(1 + getMimeType(tmpUri.toString()).indexOf("/")) + " "+getMimeType(tmpUri.toString()).substring(0,getMimeType(tmpUri.toString()).indexOf("/"))+"</b></a>"+"</p>"+"<p><object style=\"width:100%;\" data=\""+tmpUri.toString()+"\" type=\""+getMimeType(tmpUri.toString())+"\" /></p></body></html>";
                            if(outgoing) newMex = "#outgoing "+newMex;
                            newMex = newMex.replace("<p><object",(!signature.isEmpty()?"<p>"+mContext.get().getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"")+"<p><object");
                            if(!mArchive.contains(newMex)) {
                                mArchive.add(0,newMex);
                                ((MainActivity)mContext.get()).mIncomingHashCodes.put(pos, message.hashCode());
                            }
                        } else {
                            String newMex = "<html><head><meta name=\"author\" content=\""+uuid+"\"><title>" + tmp.getName() + "</title><link rel=\"stylesheet\" type=\"text/css\" href=\"about_style.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"body_margin.css\"/></head><body>"+datetime+"<p><a class=\"crushare\" href=\"display://" + tmpUri.toString() + "\" title=\"Preview\" style=\"color:#303F9F;\"><b>" + getMimeType(tmpUri.toString()).substring(1 + getMimeType(tmpUri.toString()).indexOf("/")) + " "+getMimeType(tmpUri.toString()).substring(0,getMimeType(tmpUri.toString()).indexOf("/"))+"</b></a></p></body></html>";
                            if(outgoing) newMex = "#outgoing "+newMex;
                            newMex = newMex.replace("</body>",(!signature.isEmpty()?"<p>"+mContext.get().getString(R.string.signed)+",<br><span style=\"font-style:italic;\">"+signature+"</span></p>":"")+"</body>");
                            if(!mArchive.contains(newMex)) {
                                mArchive.add(0,newMex);
                                ((MainActivity)mContext.get()).mIncomingHashCodes.put(pos, message.hashCode());
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
                        mArchive.add(0,String.format(mContext.get().getString(R.string.file_missed),datetime));
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

        });

        messageView.loadDataWithBaseURL("file:///android_asset/", message, "text/html", "utf-8", null);

        return fListItem;

    }

    private void setMargins(View v, int l, int r) {
        if (v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            p.setMargins(l, 0, r, 0);
            v.requestLayout();
        }
    }

    private LayerDrawable getShape(String message) {

        LayerDrawable layerDrawable = (LayerDrawable) ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.webview_textview_bg, null);

        int authorStart = message.indexOf("<meta name=\"author\" content=\"");
        if (layerDrawable != null && authorStart > -1) {
            layerDrawable.mutate();
            int authorEnd = message.indexOf("\">", authorStart);
            String author = message.substring(authorStart, authorEnd).replace("<meta name=\"author\" content=\"", "");
            String colorString = "#" + author.substring(0, 8);
            int color = adjustAlpha(Color.parseColor(colorString));
            layerDrawable.getDrawable(0).mutate();
            ((GradientDrawable) layerDrawable.getDrawable(0)).setStroke((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, mContext.getResources().getDisplayMetrics()), color);
            ((GradientDrawable) layerDrawable.getDrawable(0)).setColors(new int[]{Color.WHITE, Color.WHITE, color});
        }

        return layerDrawable;

    }

    @ColorInt
    private static int adjustAlpha(@ColorInt int color) {
        int alpha = Math.round(205 + Color.alpha(color) / 255 * (255 - 205));
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
        if(mContext instanceof MainActivity) ((MainActivity)mContext).invalidateOptionsMenu();
    }

}
