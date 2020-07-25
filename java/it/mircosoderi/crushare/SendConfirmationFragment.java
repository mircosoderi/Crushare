package it.mircosoderi.crushare;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class SendConfirmationFragment extends DialogFragment {

    private static final String ARG_PREVIEW = "preview";
    private static final String ARG_SIZE = "size";

    private String mPreview;

    public interface SendConfirmationFragmentInteractionListener {
        void onSendConfirmed();
        void onSendCanceled(boolean back);
        void onError(String message);
    }
    SendConfirmationFragmentInteractionListener mListener;

    public SendConfirmationFragment() {

    }

    public static SendConfirmationFragment newInstance(String htmlPreview, long size) {
        SendConfirmationFragment fragment = new SendConfirmationFragment();
        Bundle args = new Bundle();
        /*DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(fragment.getContext());
        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(fragment.getContext());
        String datedHtmlPreview = htmlPreview.replace("<body>","<body><div class=\"datetime\">"+dateFormat.format(new Date())+" "+timeFormat.format(new Date())+"</div>");*/
        args.putLong(ARG_SIZE, size);
        args.putString(ARG_PREVIEW, htmlPreview);

        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setStyle(DialogFragment.STYLE_NORMAL, R.style.FullscreenDialogStyle);
            if (getArguments() != null) {
                mPreview = "#preview "+getArguments().getString(ARG_PREVIEW);
                if(!mPreview.contains("<div class=\"datetime\">")) {
                    Date mexDateTimeObj = new Date();
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(getContext());
                        String timeFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmmss");
                        mPreview = mPreview.replace("<body>", "<body><div class=\"datetime\">" + dateFormat.format(mexDateTimeObj) + " " + android.text.format.DateFormat.format(timeFormat, mexDateTimeObj) + "</div>");
                    } else {
                        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(getContext());
                        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(getContext());
                        mPreview = mPreview.replace("<body>", "<body><div class=\"datetime\">" + dateFormat.format(mexDateTimeObj) + " " + timeFormat.format(mexDateTimeObj) + "</div>");
                    }
                }
            }
        }
        catch (Exception e) {
            mListener.onError(e.getMessage());
            dismiss();
        }
    }

    @Override
    public void onStart() {
        try {
            super.onStart();
            Dialog d = getDialog();
            if (d != null) {
                int width = ViewGroup.LayoutParams.MATCH_PARENT;
                int height = ViewGroup.LayoutParams.MATCH_PARENT;
                if(d.getWindow() != null) d.getWindow().setLayout(width, height);

            }
        }
        catch (Exception e) {
            mListener.onError(e.getMessage());
            dismiss();
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view =  inflater.inflate(R.layout.fragment_send_confirmation, container, false);
            ArrayList<String> previewArray = new ArrayList<>();
            FragmentActivity parentActivity = getActivity();
            if(parentActivity == null) throw new Exception("Unexpected error: null activity.");
            WebViewArrayAdapter adapter = new WebViewArrayAdapter(parentActivity, previewArray);
            ListView listView = view.findViewById(R.id.preview);
            listView.setAdapter(adapter);
            previewArray.add(mPreview);
            adapter.notifyDataSetChanged();

            if(getArguments() == null || !getArguments().containsKey(ARG_SIZE)) {
                throw new Exception("Something wrong happened");
            }
            long size = getArguments().getLong(ARG_SIZE);
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
            time = Math.round(time/1000);
            String declaredTime;
            if(time < 10) declaredTime = "some seconds";
            else if(time > 10 && time < 60) declaredTime = "tens of seconds";
            else if(time > 60 && time < 600) declaredTime = "some minutes";
            else if(time > 600 && time < 3600) declaredTime = "tens of minutes";
            else if(time > 3600 && time < 10*3600) declaredTime = "some hours";
            else if(time > 10*3600 && time < 24*3600) declaredTime = "tens of hours";
            else if(time > 24*3600 && time < 7*24*3600) declaredTime = "days";
            else if(time > 7*24*3600 && time < 31*24*3600) declaredTime = "weeks";
            else if(time > 31*24*3600 && time < 365*24*3600) declaredTime = "months";
            else declaredTime = "years";
            TextView proceed = view.findViewById(R.id.post);
            proceed.setText(parentActivity.getString(R.string.confirm_send_post,declaredTime));

            Button buttonYes = view.findViewById(R.id.button_yes);
            buttonYes.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v){
                    mListener.onSendConfirmed();
                    dismiss();
                }
            });
            Button buttonNo = view.findViewById(R.id.button_no);
            buttonNo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v){
                    mListener.onSendCanceled(false);
                    dismiss();
                }
            });
            return view;
        }
        catch (Exception e) {
            mListener.onError(e.getMessage());
            dismiss();
            return null;
        }
    }

    @Override
    public void onAttach(Context context) {
        try {
            super.onAttach(context);
            if (context instanceof SendConfirmationFragment.SendConfirmationFragmentInteractionListener) {
                mListener = (SendConfirmationFragmentInteractionListener) context;
            }
            else {
                throw new Exception("SendConfirmationFragment not attached to SendActivity");
            }
        }
        catch (Exception e) {
            mListener.onError(e.getMessage());
            dismiss();
        }
    }

    @Override
    public void onDetach() {
        try {
            super.onDetach();
            mListener = null;
        }
        catch (Exception e) {
            mListener.onError(e.getMessage());
            dismiss();
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        mListener.onSendCanceled(true);
    }

}
