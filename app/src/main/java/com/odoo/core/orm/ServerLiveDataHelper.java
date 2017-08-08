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
package com.odoo.core.orm;

import android.content.Context;

import com.odoo.App;
import com.odoo.core.orm.fields.OColumn;
import com.odoo.core.rpc.Odoo;
import com.odoo.core.rpc.helper.ODomain;
import com.odoo.core.rpc.helper.OdooFields;
import com.odoo.core.rpc.helper.utils.gson.OdooRecord;
import com.odoo.core.rpc.helper.utils.gson.OdooResult;
import com.odoo.core.service.OSyncAdapter;
import com.odoo.core.support.OUser;
import com.odoo.core.utils.OdooRecordUtils;
import com.odoo.datas.OConstants;

import java.util.ArrayList;
import java.util.List;

public class ServerLiveDataHelper {
    public static final String TAG = ServerLiveDataHelper.class.getSimpleName();
    private OModel mModel;
    private Context mContext;
    private Odoo mOdoo;
    private App mApp;

    public ServerLiveDataHelper(Context context, OModel model, OUser user) {
        mContext = context;
        mModel = model;
        mApp = (App) mContext.getApplicationContext();
        mOdoo = mApp.getOdoo(user);
        if (mOdoo == null)
            mOdoo = OSyncAdapter.createOdooInstance(mContext, model.getUser());
    }

    public List<ODataRow> searchRead(OdooFields fields, ODomain domain,
            int offset, int limit, String sort) {
        List<ODataRow> items = new ArrayList<>();

        if (fields == null) {
            fields = new OdooFields(mModel.getColumns(false));
        }

        if (mApp.inNetwork()) {
            OdooResult result = mOdoo
                    .withRetryPolicy(OConstants.RPC_REQUEST_TIME_OUT, OConstants.RPC_REQUEST_RETRIES)
                    .searchRead(mModel.getModelName(), fields, domain, offset, limit, sort);
            if (result != null && !result.getRecords().isEmpty()) {
                for (OdooRecord record : result.getRecords()) {
                    items.add(OdooRecordUtils.toDataRow(record));
                }
            }
        }

        for (ODataRow item : items) {
            for (OColumn localColumn : mModel.getColumns(true)) {
                String columnName = localColumn.getName();
                if (localColumn.isFunctionalColumn() && localColumn.canFunctionalStore()) {
                    List<String> depends = localColumn.getFunctionalStoreDepends();
                    OValues dependValues = new OValues();
                    for (String depend : depends) {
                        if (item.contains(depend)) {
                            dependValues.put(depend, item.get(depend));
                        }
                    }
                    Object value = mModel.getFunctionalMethodValue(localColumn, dependValues);
                    item.put(columnName, value);
                } else if (columnName.equals(OColumn.ROW_ID) && item.contains("id")) {
                    item.put(columnName, item.getInt("id"));
                } else if (columnName.equals("_write_date") && item.contains("write_date")) {
                    item.put(columnName, item.getString("write_date"));
                } else {
                    item.put(localColumn.getName(), localColumn.getDefaultValue());
                }
            }
        }

        return items;
    }

    public Odoo getOdoo() {
        return mOdoo;
    }

}
