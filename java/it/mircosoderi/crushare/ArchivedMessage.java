package it.mircosoderi.crushare;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

@Entity(tableName = "archived_message")
public class ArchivedMessage {

    @PrimaryKey(autoGenerate = true)
    private long messageId;

    @ColumnInfo(name = "session_id")
    private long sessionId;

    @ColumnInfo(name = "message_type")
    private String messageType;

    @ColumnInfo(name = "message_content")
    private byte[] messageContent;

    @ColumnInfo(name = "outgoing")
    private boolean outgoing;

    @ColumnInfo(name = "mex_hash")
    private int mexHash;

    @ColumnInfo(name = "signature")
    private String signature;

    @ColumnInfo(name = "author")
    private String author;

    public long getMessageId() {
        return messageId;
    }

    public void setMessageId(long messageId) {
        this.messageId = messageId;
    }

    public long getSessionId() {
        return sessionId;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public byte[] getMessageContent() {
        return messageContent;
    }

    public void setMessageContent(byte[] messageContent) {
        this.messageContent = messageContent;
    }

    public boolean isOutgoing() {
        return outgoing;
    }

    public void setOutgoing(boolean outgoing) {
        this.outgoing = outgoing;
    }

    public int getMexHash() {
        return mexHash;
    }

    public void setMexHash(int mexHash) {
        this.mexHash = mexHash;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }
}
