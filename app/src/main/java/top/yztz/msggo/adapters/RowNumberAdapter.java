/*
 * Copyright (C) 2026 yztz
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 */

package top.yztz.msggo.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

import top.yztz.msggo.R;

public class RowNumberAdapter extends RecyclerView.Adapter<RowNumberAdapter.RowNumberHolder> {
    private final List<Integer> rowNumbers;

    public RowNumberAdapter(List<Integer> rowNumbers) {
        this.rowNumbers = rowNumbers != null ? rowNumbers : Collections.emptyList();
    }

    @NonNull
    @Override
    public RowNumberHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.layout_row_number_item, parent, false);
        return new RowNumberHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RowNumberHolder holder, int position) {
        holder.rowNumber.setText(String.valueOf(rowNumbers.get(position)));
    }

    @Override
    public int getItemCount() {
        return rowNumbers.size();
    }

    static class RowNumberHolder extends RecyclerView.ViewHolder {
        private final TextView rowNumber;

        RowNumberHolder(@NonNull View itemView) {
            super(itemView);
            rowNumber = itemView.findViewById(R.id.tv_row_number);
        }
    }
}
