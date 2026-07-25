/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.duckdb;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

final class DuckDbResultSetMapper {

    private DuckDbResultSetMapper() {
    }

    static List<Map<String, Object>> toListMap(ResultSet resultSet) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        ResultSetMetaData meta = resultSet.getMetaData();
        int columnCount = meta.getColumnCount();
        while (resultSet.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(meta.getColumnLabel(i), resultSet.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }

    static String toJson(List<Map<String, Object>> rows) {
        JsonArray array = new JsonArray();
        for (Map<String, Object> row : rows) {
            JsonObject object = new JsonObject();
            row.forEach(object::put);
            array.add(object);
        }
        return array.toJson();
    }
}
