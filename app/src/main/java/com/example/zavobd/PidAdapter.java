package com.example.zavobd;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;

public class PidAdapter extends ArrayAdapter<PID> {

    private final Context mContext;
    private final ArrayList<PID> pidList;

    public PidAdapter(@NonNull Context context, ArrayList<PID> list) {
        super(context, 0, list);
        this.mContext = context;
        this.pidList = list;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View listItem = convertView;
        if(listItem == null)
            listItem = LayoutInflater.from(mContext).inflate(android.R.layout.simple_list_item_multiple_choice, parent, false);

        PID currentPid = pidList.get(position);

        TextView description = listItem.findViewById(android.R.id.text1);
        description.setText(currentPid.getDescription());

        // This ensures the checkbox state is persistent when scrolling
        ((android.widget.CheckedTextView) description).setChecked(currentPid.isSelected());

        return listItem;
    }
}