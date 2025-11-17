package com.hilfritz.blescanner.adapters;

import android.content.Context;
import android.widget.ArrayAdapter;

import java.util.List;

public class ServiceListAdapter extends ArrayAdapter<String> {

    public ServiceListAdapter(Context context, List<String> items) {
        super(context, android.R.layout.simple_list_item_1, items);
    }

    public void setItems(List<String> newItems) {
        clear();
        addAll(newItems);
        notifyDataSetChanged();
    }
}
