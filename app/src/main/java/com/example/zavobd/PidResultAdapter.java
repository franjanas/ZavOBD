package com.example.zavobd;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;

public class PidResultAdapter extends ArrayAdapter<PidResult> {

    public PidResultAdapter(@NonNull Context context, ArrayList<PidResult> list) {
        super(context, 0, list);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View listItem = convertView;
        if(listItem == null)
            // Use a built-in Android layout that supports two lines of text
            listItem = LayoutInflater.from(getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);

        PidResult currentResult = getItem(position);

        TextView text1 = listItem.findViewById(android.R.id.text1);
        TextView text2 = listItem.findViewById(android.R.id.text2);

        if (currentResult != null) {
            text1.setText(currentResult.getDescription());
            text2.setText(currentResult.getValue());
        }

        return listItem;
    }
}