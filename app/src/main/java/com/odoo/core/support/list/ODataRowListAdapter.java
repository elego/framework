/**
 * Odoo, Open Source Management Solution
 * Copyright (C) 2012-today Odoo SA (<http:www.odoo.com>)
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details
 * <p/>
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http:www.gnu.org/licenses/>
 */
package com.odoo.core.support.list;

import android.content.Context;
import android.support.annotation.LayoutRes;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.SectionIndexer;

import com.odoo.core.orm.ODataRow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class ODataRowListAdapter extends BaseAdapter implements SectionIndexer {

    private LayoutInflater mInflater;
    private List<ODataRow> mRecords;

    private OnViewBindListener mOnViewBindListener = null;

    private @LayoutRes int mLayout = 0;

    private Boolean hasIndexers = false;
    private String mIndexerColumn = null;
    private HashMap<String, Integer> azIndexers = new HashMap<>();
    private String[] sections = new String[0];

    public ODataRowListAdapter(Context context, @LayoutRes int layout) {
        this(context, layout, new ArrayList<ODataRow>());
    }

    public ODataRowListAdapter(Context context, @LayoutRes int layout, List<ODataRow> records) {
        mRecords = records;
        mLayout = layout;
        mInflater = LayoutInflater.from(context);
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        ODataRow record = (ODataRow) getItem(position);
        if (view == null) {
            view = mInflater.inflate(mLayout, null);
        }

        if (view != null && mOnViewBindListener != null) {
            mOnViewBindListener.onViewBind(view, record);
        }

        return view;
    }

    @Override
    public ODataRow getItem(int position) {
        return mRecords.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getCount() {
        return mRecords.size();
    }

    public void add(ODataRow record) {
        mRecords.add(record);
    }

    public void addAll(Collection<ODataRow> records) {
        mRecords.addAll(records);
    }

    public void clear() {
        mRecords.clear();
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
        if (hasIndexers && mIndexerColumn != null) {
            int pos = 0;
            List<String> keys = new ArrayList<>();
            azIndexers.clear();
            if (getCount() > 0) {
                for (ODataRow record : mRecords) {
                    String colValue = record.getString(mIndexerColumn);
                    if (colValue != null && colValue.length() > 0) {
                        String key = colValue.substring(0, 1);
                        if (!azIndexers.containsKey(key)) {
                            azIndexers.put(key, pos);
                        }
                    }
                    pos++;
                }
            }
            sections = keys.toArray(new String[keys.size()]);
        }
    }

    public void setOnViewBindListener(OnViewBindListener bindListener) {
        mOnViewBindListener = bindListener;
    }

    public void setHasSectionIndexers(boolean hasSectionIndexers, String onColumn) {
        hasIndexers = hasSectionIndexers;
        mIndexerColumn = onColumn;
    }

    @Override
    public Object[] getSections() {
        return sections;
    }

    @Override
    public int getPositionForSection(int sectionIndex) {
        return azIndexers.get(sections[sectionIndex]);
    }

    @Override
    public int getSectionForPosition(int position) {
        return azIndexers.get(sections[position]);
    }

    public interface OnViewBindListener {

        void onViewBind(View view, ODataRow row);

    }
}
