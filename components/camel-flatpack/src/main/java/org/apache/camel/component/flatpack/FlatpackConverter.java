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
package org.apache.camel.component.flatpack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import net.sf.flatpack.DataSet;
import net.sf.flatpack.Record;
import org.apache.camel.Converter;

@Converter(generateLoader = true)
public final class FlatpackConverter {

    private FlatpackConverter() {
        // helper class
    }

    @Converter
    public static Map<String, Object> toMap(Record recordObj) {
        Map<String, Object> map = new HashMap<>();
        if (recordObj instanceof DataSet dataSet) {
            putValues(map, dataSet);
        } else {
            putValues(map, recordObj);
        }

        return map;
    }

    @Converter
    public static List<Map<String, Object>> toList(DataSet dataSet) {
        List<Map<String, Object>> answer = new ArrayList<>();
        dataSet.goTop();

        while (dataSet.next()) {
            Map<String, Object> map = new HashMap<>();
            putValues(map, dataSet);
            answer.add(map);
        }

        return answer;
    }

    @Converter
    public static String toString(DataSet dataSet) {
        // force using toString from DataSet as we do not want conversion of each element
        return dataSet.toString();
    }

    @Converter
    public static Document toDocument(DataSet dataSet) throws ParserConfigurationException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        Document doc = dbf.newDocumentBuilder().newDocument();

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

    /**
     * Puts the values of the record into the map
     */
    private static void putValues(Map<String, Object> map, Record recordObj) {
        String[] columns = recordObj.getColumns();

        for (String column : columns) {
            String value = recordObj.getString(column);
            map.put(column, value);
        }
    }

    private static Element createDatasetRecord(DataSet dataSet, Document doc) {
        Element element;
        if (dataSet.isRecordID(FlatpackComponent.HEADER_ID)) {
            element = doc.createElement("DatasetHeader");
        } else if (dataSet.isRecordID(FlatpackComponent.TRAILER_ID)) {
            element = doc.createElement("DatasetTrailer");
        } else {
            element = doc.createElement("DatasetRecord");
        }

        String[] columns = getColumns(dataSet);

        for (String column : columns) {
            String value = dataSet.getString(column);

            Element columnElement = doc.createElement("Column");
            columnElement.setAttribute("name", column);
            columnElement.setTextContent(value);

            element.appendChild(columnElement);
        }

        return element;
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
