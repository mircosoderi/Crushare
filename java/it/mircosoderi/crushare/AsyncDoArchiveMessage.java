package it.mircosoderi.crushare;

import android.arch.persistence.room.Room;
import android.content.Context;

import com.google.android.gms.nearby.messages.Message;

import java.lang.ref.WeakReference;
import java.util.Arrays;
/*
public class AsyncDoArchiveMessage extends AsyncTask<Void, Void, Void> {

    private WeakReference<Context> mContext;
    private Message mMessage;
    private long mSessionId;
    private boolean mOutgoing;

    AsyncDoArchiveMessage(Context context, Message message, long sessionId, boolean outgoing) {
        mContext = new WeakReference<>(context);
        mMessage = message;
        mSessionId = sessionId;
        mOutgoing = outgoing;
    }

    @Override
    protected Void doInBackground(Void... params) {
        if(!new String(mMessage.getContent()).startsWith("DESTROY ")) {
            AppDatabase db = Room.databaseBuilder(mContext.get(),
                    AppDatabase.class, "crushare").fallbackToDestructiveMigration().build();
            ArchivedMessage archivedMessage = new ArchivedMessage();
            archivedMessage.setSessionId(mSessionId);
            archivedMessage.setMessageType(mMessage.getType());
            archivedMessage.setMessageContent(mMessage.getContent());
            archivedMessage.setOutgoing(mOutgoing);
            archivedMessage.setMexHash(mMessage.hashCode());
            db.archivedMessageDao().insert(archivedMessage);
        }
        return null;
    }

}*/

public class AsyncDoArchiveMessage {

    private WeakReference<Context> mContext;
    private Message mMessage;
    private long mSessionId;
    private boolean mOutgoing;
    private String mSignature;
    private String mAuthor;

    AsyncDoArchiveMessage(Context context, Message message, long sessionId, boolean outgoing, String signature, String author) {
        mContext = new WeakReference<>(context);
        mMessage = message;
        mSessionId = sessionId;
        mOutgoing = outgoing;
        mSignature = signature;
        mAuthor = author;
    }

    public void execute() {
        if(! ( mMessage.getType().equals("text/plain") && new String(mMessage.getContent()).startsWith("DESTROY "))) {
            AppDatabase db = Room.databaseBuilder(mContext.get(),
                    AppDatabase.class, "crushare").allowMainThreadQueries().fallbackToDestructiveMigration().build();
            ArchivedMessage archivedMessage = new ArchivedMessage();
            archivedMessage.setSessionId(mSessionId);
            archivedMessage.setMessageType(mMessage.getType());
            archivedMessage.setMessageContent(mMessage.getContent());
            archivedMessage.setOutgoing(mOutgoing);
            archivedMessage.setMexHash(Arrays.hashCode(mMessage.getContent()));
            archivedMessage.setSignature(mSignature);
            archivedMessage.setAuthor(mAuthor);
            db.archivedMessageDao().insert(archivedMessage);
        }
    }

}