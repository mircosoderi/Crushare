package it.mircosoderi.crushare;

import android.os.Parcelable;

import com.google.android.gms.nearby.messages.Message;

import java.util.Arrays;

public class BigMessage extends Message {

    private byte[] bigContent;
    private String bigType;

    BigMessage(byte[] bytes, String s) {
        super(new byte[0]);
        bigContent = bytes;
        bigType = s;
    }

    @Override
    public String getType() {
        return bigType;
    }

    @Override
    public byte[] getContent() {
        return bigContent;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bigContent);
    }
}
