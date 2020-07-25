package it.mircosoderi.crushare;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

public class EditMessageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_message);
        EditText editText = findViewById(R.id.edit_message);
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    try {
                        Intent intent = new Intent(EditMessageActivity.this, SendActivity.class);
                        intent.setAction(getString(R.string.send_message));
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_TEXT, v.getText());
                        startActivity(intent);
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_send:
                Intent intent = new Intent(EditMessageActivity.this, SendActivity.class);
                intent.setAction(getString(R.string.send_message));
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, ((EditText)findViewById(R.id.edit_message)).getText().toString());
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.edit_message, menu);
        return true;
    }
}
