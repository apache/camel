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

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import net.sf.flatpack.DataSet;
import org.apache.camel.Converter;

/**
 * @version 
 */
@Converter
public final class FlatpackConverter {

    private FlatpackConverter() {
        // helper class
    }

    @Converter
    public static Map<String, Object> toMap(DataSet dataSet) {
        Map<String, Object> map = new HashMap<String, Object>();
        putValues(map, dataSet);
        return map;
    }

    @Converter
    public static List<Map<String, Object>> toList(DataSet dataSet) {
        List<Map<String, Object>> answer = new ArrayList<Map<String, Object>>();
        dataSet.goTop();

        while (dataSet.next()) {
            Map<String, Object> map = new HashMap<String, Object>();
            putValues(map, dataSet);
            answer.add(map);
        }

        return answer;
    }

    @Converter
    public static Document toDocument(DataSet dataSet) throws ParserConfigurationException {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

        if (dataSet.getIndex() == -1) {
            Element list = doc.createElement("Dataset");

            dataSet.goTop();
            while (dataSet.next()) {
                list.appendChild(createDatasetRecord(dataSet, doc));
            }

            doc.appendChild(list);
        } else {
            doc.appendChild(createDatasetRecord(dataSet, doc));
        }

        return doc;
    }

    /**
     * Puts the values of the dataset into the map
     */
    private static void putValues(Map<String, Object> map, DataSet dataSet) {
        String[] columns = getColumns(dataSet);

        for (String column : columns) {
            String value = dataSet.getString(column);
            map.put(column, value);
        }
    }

    private static Element createDatasetRecord(DataSet dataSet, Document doc) {
        Element record;
        if (dataSet.isRecordID(FlatpackComponent.HEADER_ID)) {
            record = doc.createElement("DatasetHeader");
        } else if (dataSet.isRecordID(FlatpackComponent.TRAILER_ID)) {
            record = doc.createElement("DatasetTrailer");
        } else {
            record = doc.createElement("DatasetRecord");
        }

        String[] columns = getColumns(dataSet);

        for (String column : columns) {
            String value = dataSet.getString(column);

            Element columnElement = doc.createElement("Column");
            columnElement.setAttribute("name", column);
            columnElement.setTextContent(value);

            record.appendChild(columnElement);
        }

        return record;
    }

    private static String[] getColumns(DataSet dataSet) {
        // the columns can vary depending on header, body or trailer
        if (dataSet.isRecordID(FlatpackComponent.HEADER_ID)) {
            return dataSet.getColumns(FlatpackComponent.HEADER_ID);
        } else if (dataSet.isRecordID(FlatpackComponent.TRAILER_ID)) {
            return dataSet.getColumns(FlatpackComponent.TRAILER_ID);
        } else {
            return dataSet.getColumns();
        }
    }
}
