/**
 * Odoo, Open Source Management Solution
 * Copyright (C) 2012-today Odoo SA (<http:www.odoo.com>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http:www.gnu.org/licenses/>
 *
 * Created on 13/1/15 10:16 AM
 */
package com.odoo.base.addons.res;

import android.content.Context;

import com.odoo.core.orm.ODataRow;
import com.odoo.core.orm.OM2MRecord;
import com.odoo.core.orm.OModel;
import com.odoo.core.orm.fields.OColumn;
import com.odoo.core.orm.fields.types.OVarchar;
import com.odoo.core.support.OUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("unused")
public class ResUsers extends OModel {
    public static final String TAG = ResUsers.class.getSimpleName();

    public static final String COLUMN_NAME = "name";
    OColumn name = new OColumn("Name", OVarchar.class)
            .setName(COLUMN_NAME);

    public static final String COLUMN_LOGIN = "login";
    OColumn login = new OColumn("User Login name", OVarchar.class)
            .setName(COLUMN_LOGIN);

    public static final String COLUMN_GROUPS_ID = "groups_id";
    OColumn group_id = new OColumn("Groups", ResGroups.class,
            OColumn.RelationType.ManyToMany)
            .setName(COLUMN_GROUPS_ID)
            .setRelTableName("res_groups_users_rel")
            .setRelBaseColumn("uid")
            .setRelRelationColumn("gid");


    public ResUsers(Context context, OUser user) {
        super(context, "res.users", user);
    }

    @Override
    public boolean allowCreateRecordOnServer() {
        return false;
    }

    @Override
    public boolean allowUpdateRecordOnServer() {
        return false;
    }

    @Override
    public boolean allowDeleteRecordInLocal() {
        return false;
    }

    public static int myId(Context context) {
        ResUsers users = new ResUsers(context, null);
        return users.selectRowId(users.getUser().getUserId());
    }

    public static Set<String> getCurrentUserGroupNames(Context context) {
        Set<String> names = new HashSet<>();

        ResUsers users = new ResUsers(context, null);
        String[] projection = { ResUsers.COLUMN_GROUPS_ID };
        String[] args = { String.valueOf(users.getUser().getUserId()) };
        ODataRow record = users.browse(projection, "id = ?", args);

        if (record == null) {
            return names;
        }

        OM2MRecord groupsRecord = record.getM2MRecord(COLUMN_GROUPS_ID);

        if (groupsRecord == null) {
            return names;
        }

        List<ODataRow> groups = groupsRecord.browseEach();

        for (ODataRow group : groups) {
            String name = group.getString(ResGroups.COLUMN_NAME);
            if (name != null) {
                names.add(name);
            }
        }

        return names;
    }
}
