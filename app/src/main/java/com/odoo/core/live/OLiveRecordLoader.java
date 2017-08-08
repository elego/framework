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
package com.odoo.core.live;

import android.content.Context;
import android.support.v4.content.AsyncTaskLoader;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.odoo.core.orm.ODataRow;
import com.odoo.core.orm.OModel;
import com.odoo.core.orm.fields.OColumn;
import com.odoo.core.rpc.helper.ODomain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.odoo.core.orm.fields.OColumn.RelationType.ManyToOne;
import static com.odoo.core.orm.fields.OColumn.RelationType.OneToMany;

public class OLiveRecordLoader extends AsyncTaskLoader<List<ODataRow>> {

    private OModel model;
    private ODomain domain = new ODomain();

    private List<String> relationColumns = new ArrayList<>();

    private int offset = 0;
    private int limit = 10;
    private String sort = null;
    private boolean hasMoreData = true;

    /**
     * Stores away the application context associated with context.
     * Since Loaders can be used across multiple activities it's dangerous to
     * store the context directly; always use {@link #getContext()} to retrieve
     * the Loader's Context, don't use the constructor argument directly.
     * The Context returned by {@link #getContext} is safe to use across
     * Activity instances.
     *
     * @param context used to retrieve the application context.
     * @param model to retrieve data for
     */
    public OLiveRecordLoader(Context context, OModel model) {
        super(context);
        this.model = model;
    }

    public OLiveRecordLoader setDomain(ODomain domain) {
        if (domain != null) {
            this.domain = domain;
        }
        return this;
    }

    public OLiveRecordLoader setLimit(int limit) {
        if (limit > 0) {
            this.limit = limit;
        }
        return this;
    }

    public OLiveRecordLoader setSort(String sort) {
        this.sort = sort;
        return this;
    }

    public OLiveRecordLoader setRelationsToLoad(List<String> relationColumns) {
        if (relationColumns != null) {
            this.relationColumns = relationColumns;
        }
        return this;
    }

    public OLiveRecordLoader setRelationsToLoad(String... relationColumns) {
        return setRelationsToLoad(Arrays.asList(relationColumns));
    }

    @Override
    public List<ODataRow> loadInBackground() {
        List<ODataRow> records = new ArrayList<>();

        if (hasMoreData) {
            records = model.getServerLiveDataHelper().searchRead(null, domain, offset, limit, sort);
            offset += limit;

            if (records.size() < limit) {
                hasMoreData = false;
            }

            if (!relationColumns.isEmpty()) {
                // Collect record ids to search by 'in clause' for better performance

                for (String relationColumnName : relationColumns) {
                    OColumn relColumn = model.getColumn(relationColumnName);
                    if (relColumn == null) {
                        continue;
                    }

                    OModel relModel = model.createInstance(relColumn.getType());
                    Set<Integer> relIds = collectColumnIds(records, relColumn);

                    ODomain relDomain = new ODomain().add("id", "in", new ArrayList<>(relIds));
                    List<ODataRow> relRecordResult =
                            relModel.getServerLiveDataHelper().searchRead(null, relDomain, 0, 0, null);

                    assignRelationRecords(records, relColumn, relRecordResult);
                }
            }
        }

        return records;
    }

    private static Set<Integer> collectColumnIds(List<ODataRow> records, OColumn column) {
        Set<Integer> result = new HashSet<>();

        for (ODataRow record : records) {
            Object value = record.get(column.getName());
            if (value == null) {
                continue;
            }

            if (column.getRelationType() == ManyToOne) {
                try {
                    result.add(((Double) ((List) value).get(0)).intValue());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    for (Object item : (List) value) {
                        result.add(((Double) item).intValue());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return result;
    }

    private static void assignRelationRecords(List<ODataRow> records, OColumn relColumn,
            List<ODataRow> relRecords) {

        SparseArray<ODataRow> relRecordsArray = new SparseArray<>();
        for (ODataRow relRecord : relRecords) {
            relRecordsArray.put(relRecord.getInt("id"), relRecord);
        }

        for (ODataRow record : records) {
            Object value = record.get(relColumn.getName());
            if (value == null) {
                continue;
            }

            if (relColumn.getRelationType() == ManyToOne) {
                try {
                    int id = ((Double) ((List) value).get(0)).intValue();
                    record.put(relColumn.getName(), relRecordsArray.get(id));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    List<ODataRow> assignedRelRecords = new ArrayList<>();
                    for (Object item : (List) value) {
                        int id = ((Double) item).intValue();
                        assignedRelRecords.add(relRecordsArray.get(id));
                    }
                    record.put(relColumn.getName(), assignedRelRecords);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}