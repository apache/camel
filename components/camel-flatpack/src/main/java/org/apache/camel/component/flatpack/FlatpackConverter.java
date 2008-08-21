/**
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
package org.apache.camel.component.flatpack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.flatpack.DataSet;
import org.apache.camel.Converter;

/**
 * @version $Revision$
 */
@Converter
public final class FlatpackConverter {

    private FlatpackConverter() {
        // helper class
    }

    @Converter
    public static Map toMap(DataSet dataSet) {
        Map<String, Object> map = new HashMap<String, Object>();
        putValues(map, dataSet);
        return map;
    }

    @Converter
    public static List toList(DataSet dataSet) {
        List<Map<String, Object>> answer = new ArrayList<Map<String, Object>>();
        dataSet.goTop();

        while (dataSet.next()) {
            Map<String, Object> map = new HashMap<String, Object>();
            putValues(map, dataSet);
            answer.add(map);
        }

        return answer;
    }

    /**
     * Puts the values of the dataset into the map
     */
    public static void putValues(Map<String, Object> map, DataSet dataSet) {
        boolean header = dataSet.isRecordID(FlatpackComponent.HEADER_ID);
        boolean trailer = dataSet.isRecordID(FlatpackComponent.TRAILER_ID);

        // the columns can vary depending on header, body or trailer
        String[] columns;
        if (header) {
            columns = dataSet.getColumns(FlatpackComponent.HEADER_ID);
        } else if (trailer) {
            columns = dataSet.getColumns(FlatpackComponent.TRAILER_ID);
        } else {
            columns = dataSet.getColumns();
        }

        for (String column : columns) {
            String value = dataSet.getString(column);
            map.put(column, value);
        }
    }

}
