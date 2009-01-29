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
package org.apache.camel.dataformat.bindy;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.annotation.Link;
import org.apache.camel.dataformat.bindy.util.AnnotationModelLoader;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The ModelFactory is the core class of the bindy component and allows to a)
 * Generate a model associated to a record (CSV, ...) b) Bind data from a record
 * to the POJOs c) Export data of POJOs to a record (CSV, ...) d) Format data
 * into String, Date, Double, ... according to the format/pattern defined
 */
public class BindyCsvFactory implements BindyFactory {

    private static final transient Log LOG = LogFactory.getLog(BindyCsvFactory.class);

    private AnnotationModelLoader modelsLoader;
    private Set<Class<?>> models;

    private Map<Integer, DataField> mapDataField = new LinkedHashMap<Integer, DataField>();
    private Map<Integer, Field> mapAnnotedField = new LinkedHashMap<Integer, Field>();
    private Map<String, Field> mapAnnotedLinkField = new LinkedHashMap<String, Field>();

    private String separator;
    private boolean skipFirstLine;
    private String packageName;

    public BindyCsvFactory(String packageName) throws Exception {
        modelsLoader = new AnnotationModelLoader();
        this.packageName = packageName;
        initModel();
    }

    /**
     * method uses to initialize the model representing the classes who will
     * bind the data This process will scan for classes according to the package
     * name provided, check the classes and fields annoted and retrieve the
     * separator of the CSV record
     * 
     * @throws Exception
     */
    public void initModel() throws Exception {

        // Find classes defined as Model
        initModelClasses(packageName);

        // Find annotated fields declared in the Model classes
        initAnnotedFields();

        // Get parameters : separator and skipfirstline from
        // @CSVrecord annotation
        initCsvRecordParameters();

    }

    /**
     * Find all the classes defined as model
     */
    private void initModelClasses(String packageName) throws Exception {
        models = modelsLoader.loadModels(packageName);
    }

    /**
     * Find fields annoted in each class of the model
     */
    private void initAnnotedFields() throws Exception {

        for (Class<?> cl : models) {

            for (Field field : cl.getDeclaredFields()) {
                DataField dataField = field.getAnnotation(DataField.class);
                if (dataField != null) {
                    mapDataField.put(dataField.pos(), dataField);
                    mapAnnotedField.put(dataField.pos(), field);
                }

                Link linkField = field.getAnnotation(Link.class);

                if (linkField != null) {
                    mapAnnotedLinkField.put(cl.getName(), field);
                }
            }

        }
    }

    public void bind(List<String> data, Map<String, Object> model) throws Exception {

        int pos = 0;

        while (pos < data.size()) {

            // Set the field with the data received
            // Only when no empty line is provided
            // Data is transformed according to the pattern defined or by
            // default the type of the field (int, double, String, ...)

            if (!data.get(pos).equals("")) {

                DataField dataField = mapDataField.get(pos);
                Field field = mapAnnotedField.get(pos);
                field.setAccessible(true);

                LOG.debug("Pos : " + pos + ", Data : " + data.get(pos) + ", Field type : " + field.getType());

                Format<?> format;
                String pattern = dataField.pattern();

                format = FormatFactory.getFormat(field.getType(), pattern, dataField.precision());
                field.set(model.get(field.getDeclaringClass().getName()), format.parse(data.get(pos)));

            }

            pos++;
        }

    }

    public String unbind(Map<String, Object> model) throws Exception {

        StringBuilder builder = new StringBuilder();

        // must use a tree map to get a sorted iterator by the poisition defined by annotations
        Map<Integer, DataField> dataFields = new TreeMap<Integer, DataField>(mapDataField);
        Iterator<Integer> it = dataFields.keySet().iterator();

        // Check if separator exists
        ObjectHelper.notNull(this.separator, "The separator has not been instantiated or property not defined in the @CsvRecord annotation");

        while (it.hasNext()) {

            DataField dataField = mapDataField.get(it.next());

            // Retrieve the field
            Field field = mapAnnotedField.get(dataField.pos());
            // Change accessibility to allow to read protected/private fields
            field.setAccessible(true);

            // Retrieve the format associated to the type
            Format format;

            String pattern = dataField.pattern();
            format = FormatFactory.getFormat(field.getType(), pattern, dataField.precision());

            Object obj = model.get(field.getDeclaringClass().getName());

            // Convert the content to a String and append it to the builder
            builder.append(format.format(field.get(obj)));
            if (it.hasNext()) {
                builder.append(this.getSeparator());
            }

        }

        return builder.toString();
    }

    /**
     * Link objects together (Only 1to1 relation is allowed)
     */
    public void link(Map<String, Object> model) throws Exception {

        for (String link : mapAnnotedLinkField.keySet()) {

            Field field = mapAnnotedLinkField.get(link);
            field.setAccessible(true);

            // Retrieve linked object
            String toClassName = field.getType().getName();
            Object to = model.get(toClassName);

            ObjectHelper.notNull(to, "No @link annotation has been defined for the oject to link");
            field.set(model.get(field.getDeclaringClass().getName()), to);

        }
    }

    /**
     * Factory method generating new instances of the model and adding them to a
     * HashMap
     * 
     * @return Map is a collection of the objects used to bind data from csv
     *         records
     * @throws Exception can be thrown
     */
    public Map<String, Object> factory() throws Exception {

        Map<String, Object> mapModel = new HashMap<String, Object>();

        for (Class<?> cl : models) {

            Object obj = ObjectHelper.newInstance(cl);

            // Add instance of the class to the Map Model
            mapModel.put(obj.getClass().getName(), obj);

        }

        return mapModel;
    }

    /**
     * Find the separator used to delimit the CSV fields
     */
    public String getSeparator() {
        return separator;
    }

    /**
     * Get the parameter skipFirstLine
     */
    public boolean getSkipFirstLine() {
        return skipFirstLine;
    }

    /**
     * Get paramaters defined in @Csvrecord annotation
     */
    private void initCsvRecordParameters() {

        if (separator == null) {

            for (Class<?> cl : models) {

                // Get annotation @CsvRecord from the class
                CsvRecord record = cl.getAnnotation(CsvRecord.class);

                if (record != null) {

                    // Get skipFirstLine parameter
                    skipFirstLine = record.skipFirstLine();
                    LOG.debug("Skip First Line parameter of the CSV : " + skipFirstLine);

                    // Get Separator parameter
                    ObjectHelper.notNull(record.separator(), "No separator has been defined in the @Record annotation !");
                    separator = record.separator();
                    LOG.debug("Separator defined for the CSV : " + separator);

                }

            }

        }

    }

}
