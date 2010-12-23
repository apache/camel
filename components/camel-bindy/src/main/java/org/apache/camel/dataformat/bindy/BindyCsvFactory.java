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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.annotation.Link;
import org.apache.camel.dataformat.bindy.annotation.OneToMany;
import org.apache.camel.dataformat.bindy.annotation.Section;
import org.apache.camel.dataformat.bindy.format.FormatException;
import org.apache.camel.dataformat.bindy.util.Converter;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The BindyCsvFactory is the class who allows to : Generate a model associated
 * to a CSV record, bind data from a record to the POJOs, export data of POJOs
 * to a CSV record and format data into String, Date, Double, ... according to
 * the format/pattern defined
 */
public class BindyCsvFactory extends BindyAbstractFactory implements BindyFactory {

    private static final transient Log LOG = LogFactory.getLog(BindyCsvFactory.class);

    boolean isOneToMany;

    private Map<Integer, DataField> dataFields = new LinkedHashMap<Integer, DataField>();
    private Map<Integer, Field> annotedFields = new LinkedHashMap<Integer, Field>();
    private Map<String, Integer> sections = new HashMap<String, Integer>();

    private Map<Integer, List> results;

    private int numberOptionalFields;
    private int numberMandatoryFields;
    private int totalFields;

    private String separator;
    private boolean skipFirstLine;
    private boolean generateHeaderColumnNames;
    private boolean messageOrdered;

    public BindyCsvFactory(PackageScanClassResolver resolver, String... packageNames) throws Exception {
        super(resolver, packageNames);

        // initialize specific parameters of the csv model
        initCsvModel();
    }

    /**
     * method uses to initialize the model representing the classes who will
     * bind the data. This process will scan for classes according to the
     * package name provided, check the annotated classes and fields and
     * retrieve the separator of the CSV record
     * 
     * @throws Exception
     */
    public void initCsvModel() throws Exception {

        // Find annotated Datafields declared in the Model classes
        initAnnotatedFields();

        // initialize Csv parameter(s)
        // separator and skip first line from @CSVrecord annotation
        initCsvRecordParameters();
    }

    public void initAnnotatedFields() {

        for (Class<?> cl : models) {

            List<Field> linkFields = new ArrayList<Field>();

            if (LOG.isDebugEnabled()) {
                LOG.debug("Class retrieved : " + cl.getName());
            }

            for (Field field : cl.getDeclaredFields()) {
                DataField dataField = field.getAnnotation(DataField.class);
                if (dataField != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Position defined in the class : " + cl.getName() + ", position : " + dataField.pos() + ", Field : " + dataField.toString());
                    }

                    if (dataField.required()) {
                        ++numberMandatoryFields;
                    } else {
                        ++numberOptionalFields;
                    }

                    dataFields.put(dataField.pos(), dataField);
                    annotedFields.put(dataField.pos(), field);
                }

                Link linkField = field.getAnnotation(Link.class);

                if (linkField != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Class linked  : " + cl.getName() + ", Field" + field.toString());
                    }
                    linkFields.add(field);
                }

            }

            if (!linkFields.isEmpty()) {
                annotatedLinkFields.put(cl.getName(), linkFields);
            }

            totalFields = numberMandatoryFields + numberOptionalFields;

            if (LOG.isDebugEnabled()) {
                LOG.debug("Number of optional fields : " + numberOptionalFields);
                LOG.debug("Number of mandatory fields : " + numberMandatoryFields);
                LOG.debug("Total : " + totalFields);
            }

        }
    }

    public void bind(List<String> tokens, Map<String, Object> model, int line) throws Exception {

        int pos = 1;
        int counterMandatoryFields = 0;

        for (String data : tokens) {

            // Get DataField from model
            DataField dataField = dataFields.get(pos);
            ObjectHelper.notNull(dataField, "No position " + pos + " defined for the field : " + data + ", line : " + line);

            if (dataField.trim()) {
                data = data.trim();
            }
            
            if (dataField.required()) {
                // Increment counter of mandatory fields
                ++counterMandatoryFields;

                // Check if content of the field is empty
                // This is not possible for mandatory fields
                if (data.equals("")) {
                    throw new IllegalArgumentException("The mandatory field defined at the position " + pos + " is empty for the line : " + line);
                }
            }

            // Get Field to be setted
            Field field = annotedFields.get(pos);
            field.setAccessible(true);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Pos : " + pos + ", Data : " + data + ", Field type : " + field.getType());
            }

            Format<?> format;

            // Get pattern defined for the field
            String pattern = dataField.pattern();

            // Create format object to format the field
            format = FormatFactory.getFormat(field.getType(), pattern, getLocale(), dataField.precision());

            // field object to be set
            Object modelField = model.get(field.getDeclaringClass().getName());

            // format the data received
            Object value = null;

            if (!data.equals("")) {
                try {
                    value = format.parse(data);
                } catch (FormatException ie) {
                    throw new IllegalArgumentException(ie.getMessage() + ", position : " + pos + ", line : " + line, ie);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Parsing error detected for field defined at the position : " + pos + ", line : " + line, e);
                }
            } else {
                value = getDefaultValueForPrimitive(field.getType());
            }

            field.set(modelField, value);

            ++pos;

        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Counter mandatory fields : " + counterMandatoryFields);
        }

        if (pos < totalFields) {
            throw new IllegalArgumentException("Some fields are missing (optional or mandatory), line : " + line);
        }

        if (counterMandatoryFields < numberMandatoryFields) {
            throw new IllegalArgumentException("Some mandatory fields are missing, line : " + line);
        }

    }

    public String unbind(Map<String, Object> model) throws Exception {

        StringBuilder buffer = new StringBuilder();
        results = new HashMap<Integer, List>();

        // Check if separator exists
        ObjectHelper.notNull(this.separator, "The separator has not been instantiated or property not defined in the @CsvRecord annotation");

        char separator = Converter.getCharDelimitor(this.getSeparator());

        if (LOG.isDebugEnabled()) {
            LOG.debug("Separator converted : '0x" + Integer.toHexString(separator) + "', from : " + this.getSeparator());
        }

        for (Class clazz : models) {

            if (model.containsKey(clazz.getName())) {

                Object obj = model.get(clazz.getName());

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Model object : " + obj + ", class : " + obj.getClass().getName());
                }

                if (obj != null) {

                    // Generate Csv table
                    generateCsvPositionMap(clazz, obj);

                }
            }
        }

        // Transpose result
        List<List> l = new ArrayList<List>();

        if (isOneToMany) {

            l = product(results);

        } else {

            // Convert Map<Integer, List> into List<List>
            TreeMap<Integer, List> sortValues = new TreeMap<Integer, List>(results);
            List<String> temp = new ArrayList<String>();

            for (Integer key : sortValues.keySet()) {

                // Get list of values
                List<String> val = sortValues.get(key);

                // For one to one relation
                // There is only one item in the list
                String value = (String)val.get(0);

                // Add the value to the temp array
                if (value != null) {
                    temp.add(value);
                } else {
                    temp.add("");
                }
            }

            l.add(temp);
        }

        if (l != null) {

            Iterator it = l.iterator();
            while (it.hasNext()) {

                List<String> tokens = (ArrayList<String>)it.next();
                Iterator itx = tokens.iterator();

                while (itx.hasNext()) {

                    String res = (String)itx.next();

                    if (res != null) {
                        buffer.append(res);
                    } else {
                        buffer.append("");
                    }

                    if (itx.hasNext()) {
                        buffer.append(separator);
                    }

                }

                if (it.hasNext()) {
                    buffer.append(Converter.getStringCarriageReturn(getCarriageReturn()));
                }

            }

        }

        return buffer.toString();

    }

    private List<List> product(Map<Integer, List> values) {

        TreeMap<Integer, List> sortValues = new TreeMap<Integer, List>(values);

        List<List> product = new ArrayList<List>();
        Map<Integer, Integer> index = new HashMap<Integer, Integer>();

        boolean cont = true;
        int idx = 0;
        int idxSize;

        do {

            idxSize = 0;
            List v = new ArrayList();

            for (int ii = 1; ii <= sortValues.lastKey(); ii++) {

                List l = values.get(ii);

                if (l == null) {
                    v.add("");
                    ++idxSize;
                    continue;
                }

                if (l.size() >= idx + 1) {
                    v.add(l.get(idx));
                    index.put(ii, idx);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Value : " + l.get(idx) + ", pos : " + ii + ", at :" + idx);
                    }

                } else {
                    v.add(l.get(0));
                    index.put(ii, 0);
                    ++idxSize;
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Value : " + l.get(0) + ", pos : " + ii + ", at index : " + 0);
                    }
                }

            }

            if (idxSize != sortValues.lastKey()) {
                product.add(v);
            }
            ++idx;

        } while (idxSize != sortValues.lastKey());

        return product;
    }

    /**
     * 
     * Generate a table containing the data formated and sorted with their position/offset
     * If the model is Ordered than a key is created combining the annotation @Section and Position of the field
     * If a relation @OneToMany is defined, than we iterate recursivelu through this function
     * The result is placed in the Map<Integer, List> results
     * 
     * @param clazz
     * @param obj
     * @throws Exception
     */
    private void generateCsvPositionMap(Class clazz, Object obj) throws Exception {

        String result = "";

        for (Field field : clazz.getDeclaredFields()) {

            field.setAccessible(true);

            DataField datafield = field.getAnnotation(DataField.class);

            if (datafield != null) {

                if (obj != null) {

                    // Retrieve the format, pattern and precision associated to
                    // the type
                    Class type = field.getType();
                    String pattern = datafield.pattern();
                    int precision = datafield.precision();

                    // Create format
                    Format format = FormatFactory.getFormat(type, pattern, getLocale(), precision);

                    // Get field value
                    Object value = field.get(obj);

                    result = formatString(format, value);

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Value to be formatted : " + value + ", position : " + datafield.pos() + ", and its formated value : " + result);
                    }

                } else {
                    result = "";
                }

                Integer key;

                if (isMessageOrdered()) {

                    // Generate a key using the number of the section
                    // and the position of the field
                    Integer key1 = sections.get(obj.getClass().getName());
                    Integer key2 = datafield.position();
                    Integer keyGenerated = generateKey(key1, key2);

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Key generated : " + String.valueOf(keyGenerated) + ", for section : " + key1);
                    }

                    key = keyGenerated;

                } else {

                    key = datafield.pos();
                }

                if (!results.containsKey(key)) {

                    List list = new LinkedList();
                    list.add(result);
                    results.put(key, list);

                } else {

                    List list = (LinkedList)results.get(key);
                    list.add(result);
                }

            }

            OneToMany oneToMany = field.getAnnotation(OneToMany.class);
            if (oneToMany != null) {

                // Set global variable
                // Will be used during generation of CSV
                isOneToMany = true;

                ArrayList list = (ArrayList)field.get(obj);

                if (list != null) {

                    Iterator it = list.iterator();

                    while (it.hasNext()) {

                        Object target = it.next();
                        generateCsvPositionMap(target.getClass(), target);

                    }

                } else {

                    // Call this function to add empty value
                    // in the table
                    generateCsvPositionMap(field.getClass(), null);
                }

            }
        }

    }
    
    /**
     * Generate for the first line the headers of the columns
     * 
     * @return the headers columns
     */
    public String generateHeader() {

        Map<Integer, DataField> dataFieldsSorted = new TreeMap<Integer, DataField>(dataFields);
        Iterator<Integer> it = dataFieldsSorted.keySet().iterator();

        StringBuilder builderHeader = new StringBuilder();

        while (it.hasNext()) {

            DataField dataField = dataFieldsSorted.get(it.next());

            // Retrieve the field
            Field field = annotedFields.get(dataField.pos());
            // Change accessibility to allow to read protected/private fields
            field.setAccessible(true);

            // Get dataField
            if (!dataField.columnName().equals("")) {
                builderHeader.append(dataField.columnName());
            } else {
                builderHeader.append(field.getName());
            }

            if (it.hasNext()) {
                builderHeader.append(separator);
            }

        }

        return builderHeader.toString();
    }

    /**
     * Get parameters defined in @Csvrecord annotation
     */
    private void initCsvRecordParameters() {
        if (separator == null) {
            for (Class<?> cl : models) {

                // Get annotation @CsvRecord from the class
                CsvRecord record = cl.getAnnotation(CsvRecord.class);

                // Get annotation @Section from the class
                Section section = cl.getAnnotation(Section.class);

                if (record != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Csv record : " + record.toString());
                    }

                    // Get skipFirstLine parameter
                    skipFirstLine = record.skipFirstLine();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Skip First Line parameter of the CSV : " + skipFirstLine);
                    }

                    // Get generateHeaderColumnNames parameter
                    generateHeaderColumnNames = record.generateHeaderColumns();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Generate header column names parameter of the CSV : " + generateHeaderColumnNames);
                    }

                    // Get Separator parameter
                    ObjectHelper.notNull(record.separator(), "No separator has been defined in the @Record annotation !");
                    separator = record.separator();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Separator defined for the CSV : " + separator);
                    }

                    // Get carriage return parameter
                    crlf = record.crlf();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Carriage return defined for the CSV : " + crlf);
                    }

                    // Get isOrdered parameter
                    messageOrdered = record.isOrdered();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Must CSV record be ordered ? " + messageOrdered);
                    }

                }

                if (section != null) {
                    // Test if section number is not null
                    ObjectHelper.notNull(section.number(), "No number has been defined for the section !");

                    // Get section number and add it to the sections
                    sections.put(cl.getName(), section.number());
                }
            }
        }
    }

    /**
     * Find the separator used to delimit the CSV fields
     */
    public String getSeparator() {
        return separator;
    }

    /**
     * Flag indicating if the first line of the CSV must be skipped
     */
    public boolean getGenerateHeaderColumnNames() {
        return generateHeaderColumnNames;
    }

    /**
     * Find the separator used to delimit the CSV fields
     */
    public boolean getSkipFirstLine() {
        return skipFirstLine;
    }

    /**
     * Flag indicating if the message must be ordered
     * 
     * @return boolean
     */
    public boolean isMessageOrdered() {
        return messageOrdered;
    }
}
