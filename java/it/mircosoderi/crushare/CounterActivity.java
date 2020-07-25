package it.mircosoderi.crushare;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageListener;
import com.google.android.gms.nearby.messages.PublishOptions;
import com.google.android.gms.nearby.messages.Strategy;
import com.google.android.gms.nearby.messages.SubscribeCallback;
import com.google.android.gms.nearby.messages.SubscribeOptions;
import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;

public class CounterActivity extends AppCompatActivity {

    MessageListener mMessageListener;
    int count = 0;
    ArrayList<String> identifiers = new ArrayList<>();

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

    public void connect() {

        Toast.makeText(this, getString(R.string.connecting), Toast.LENGTH_SHORT).show();

        Nearby.getMessagesClient(this).subscribe(mMessageListener, mOptions).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(CounterActivity.this, getString(R.string.connection_error),
                        Toast.LENGTH_SHORT).show();
            }
        }).addOnCanceledListener(new OnCanceledListener() {
            @Override
            public void onCanceled() {
                Toast.makeText(CounterActivity.this, getString(R.string.canceled),
                        Toast.LENGTH_SHORT).show();
            }
        }).addOnSuccessListener(new OnSuccessListener<Object>() {
            @Override
            public void onSuccess(Object result) {
                Toast.makeText(CounterActivity.this, getString(R.string.connected),
                        Toast.LENGTH_SHORT).show();
                iAmHere();
            }
        });

    }

    public void disconnect() {

        Toast.makeText(CounterActivity.this, getString(R.string.disconnecting), Toast.LENGTH_SHORT).show();

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
        setContentView(R.layout.activity_counter);

    }

    @Override
    public void onStart() {

        super.onStart();

        mMessageListener = new CounterActivity.NearbyMessageListener(this);

        ((TextView) findViewById(R.id.counter)).setText(String.valueOf(count));

        connect();

    }

    @Override
    public void onStop() {
        disconnect();
        super.onStop();
    }

    class NearbyMessageListener extends MessageListener {

        AppCompatActivity counterActivity;

        NearbyMessageListener(AppCompatActivity counterActivity) {
            this.counterActivity = counterActivity;
        }

        @Override
        public void onFound(Message message) {
            if(message.getType().equals("text/uuid")) {
                if (!identifiers.contains(new String(message.getContent()).trim())) {
                    count++;
                    ((TextView) findViewById(R.id.counter)).setText(String.valueOf(count));
                    identifiers.add(new String(message.getContent()).trim());
                }
            }
        }

        @Override
        public void onLost(Message message) {}

    }

    class Postman implements Runnable {

        Context context;
        int delay;
        Handler handler = new Handler();

        Postman(Context context, int delay) {
            this.context = context;
            this.delay = 1000*delay;
        }

        public void run(){
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
            String uuid = sharedPref.getString(SettingsFragment.INSTANCE_ID, UUID.randomUUID().toString());
            Message message = new Message(uuid.getBytes(),"text/uuid");
            Nearby.getMessagesClient(context)
                    .publish(message, new PublishOptions.Builder().setStrategy(Strategy.DEFAULT).build());
            handler.postDelayed(this, Double.valueOf(Math.abs(new Random().nextGaussian()) * delay).longValue());

        }

    }

    private void iAmHere() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        int delay = Integer.parseInt(sharedPref.getString(SettingsFragment.DELIVERY_INTERVAL, "10"));
        new Postman(this, delay).run();
    }

}
