package com.android.alarmmusic;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import com.android.deskclock.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by gkipnis on 3/18/16.
 */
public class AlarmMusicProviderPicker extends AlertDialog {
    private Context mContext;

    public interface OnAlarmMusicProviderSelectListener {
        public void onSelect(int provider);
    }
    private OnAlarmMusicProviderSelectListener mListener;

    public AlarmMusicProviderPicker(Context context, OnAlarmMusicProviderSelectListener listener) {
        super(context);
        mContext = context;
        mListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.alarm_music_provider_picker);

        ListView listView = (ListView)findViewById(R.id.alarm_music_provider_list);

        // need to populate this in a smart way, for now just hard code 2 items
        List<String> providers = new ArrayList<String>();
        providers.add("Spotify");
        providers.add("Built In Ringtones");


        final ArrayAdapter<String> adapter =
                new ArrayAdapter<String>(
                        mContext,
                        android.R.layout.simple_list_item_single_choice,
                        providers
                );
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String item = adapter.getItem(position);
                mListener.onSelect(position);
                dismiss();
            }
        });
        setCanceledOnTouchOutside(false);
    }
}
