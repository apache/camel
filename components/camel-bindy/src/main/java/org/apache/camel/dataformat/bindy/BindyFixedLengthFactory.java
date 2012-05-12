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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.annotation.FixedLengthRecord;
import org.apache.camel.dataformat.bindy.annotation.Link;
import org.apache.camel.dataformat.bindy.format.FormatException;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The BindyCsvFactory is the class who allows to : Generate a model associated
 * to a fixed length record, bind data from a record to the POJOs, export data of POJOs
 * to a fixed length record and format data into String, Date, Double, ... according to
 * the format/pattern defined
 */
public class BindyFixedLengthFactory extends BindyAbstractFactory implements BindyFactory {

    private static final transient Logger LOG = LoggerFactory.getLogger(BindyFixedLengthFactory.class);

    boolean isOneToMany;

    private Map<Integer, DataField> dataFields = new LinkedHashMap<Integer, DataField>();
    private Map<Integer, Field> annotatedFields = new LinkedHashMap<Integer, Field>();

    private int numberOptionalFields;
    private int numberMandatoryFields;
    private int totalFields;

    private boolean hasHeader;
    private boolean hasFooter;
    private char paddingChar;
    private int recordLength;

    public BindyFixedLengthFactory(PackageScanClassResolver resolver, String... packageNames) throws Exception {
        super(resolver, packageNames);

        // initialize specific parameters of the fixed length model
        initFixedLengthModel();
    }

    public BindyFixedLengthFactory(PackageScanClassResolver resolver, Class<?> type) throws Exception {
        super(resolver, type);

        // initialize specific parameters of the fixed length model
        initFixedLengthModel();
    }

    /**
     * method uses to initialize the model representing the classes who will
     * bind the data. This process will scan for classes according to the
     * package name provided, check the annotated classes and fields
     */
    public void initFixedLengthModel() throws Exception {

        // Find annotated fields declared in the Model classes
        initAnnotatedFields();

        // initialize Fixed length parameter(s)
        // from @FixedLengthrecord annotation
        initFixedLengthRecordParameters();
    }

    public void initAnnotatedFields() {

        for (Class<?> cl : models) {

            List<Field> linkFields = new ArrayList<Field>();

            if (LOG.isDebugEnabled()) {
                LOG.debug("Class retrieved: {}", cl.getName());
            }

            for (Field field : cl.getDeclaredFields()) {
                DataField dataField = field.getAnnotation(DataField.class);
                if (dataField != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Position defined in the class: {}, position: {}, Field: {}", new Object[]{cl.getName(), dataField.pos(), dataField});
                    }

                    if (dataField.required()) {
                        ++numberMandatoryFields;
                    } else {
                        ++numberOptionalFields;
                    }

                    dataFields.put(dataField.pos(), dataField);
                    annotatedFields.put(dataField.pos(), field);
                }

                Link linkField = field.getAnnotation(Link.class);

                if (linkField != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Class linked: {}, Field: {}", cl.getName(), field);
                    }
                    linkFields.add(field);
                }

            }

            if (!linkFields.isEmpty()) {
                annotatedLinkFields.put(cl.getName(), linkFields);
            }

            totalFields = numberMandatoryFields + numberOptionalFields;

            if (LOG.isDebugEnabled()) {
                LOG.debug("Number of optional fields: {}", numberOptionalFields);
                LOG.debug("Number of mandatory fields: {}", numberMandatoryFields);
                LOG.debug("Total: {}", totalFields);
            }

        }
    }
    
    // Will not be used in the case of a Fixed Length record
    // as we provide the content of the record and 
    // we don't split it as this is the case for a CSV record
    @Override
    public void bind(List<String> data, Map<String, Object> model, int line) throws Exception {
        // noop
    }

    public void bind(String record, Map<String, Object> model, int line) throws Exception {

        int pos = 1;
        int counterMandatoryFields = 0;
        DataField dataField;
        String token;
        int offset;
        int length;
        Field field;
        String pattern;

        // Iterate through the list of positions
        // defined in the @DataField
        // and grab the data from the line
        Collection<DataField> c = dataFields.values();
        Iterator<DataField> itr = c.iterator();

        while (itr.hasNext()) {
            dataField = itr.next();
            offset = dataField.pos();
            length = dataField.length();

            ObjectHelper.notNull(offset, "Position/offset is not defined for the field: " + dataField.toString());
            ObjectHelper.notNull(offset, "Length is not defined for the field: " + dataField.toString());

            if (offset - 1 <= -1) {
                throw new IllegalArgumentException("Offset/Position of the field " + dataField.toString()
                                                   + " cannot be negative");
            }

            token = record.substring(offset - 1, offset + length - 1);

            if (dataField.trim()) {
                token = token.trim();
            }

            // Check mandatory field
            if (dataField.required()) {

                // Increment counter of mandatory fields
                ++counterMandatoryFields;

                // Check if content of the field is empty
                // This is not possible for mandatory fields
                if (token.equals("")) {
                    throw new IllegalArgumentException("The mandatory field defined at the position " + pos
                                                       + " is empty for the line: " + line);
                }
            }
            
            // Get Field to be setted
            field = annotatedFields.get(offset);
            field.setAccessible(true);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Pos/Offset: {}, Data: {}, Field type: {}", new Object[]{offset, token, field.getType()});
            }
            
            Format<?> format;

            // Get pattern defined for the field
            pattern = dataField.pattern();

            // Create format object to format the field
            format = FormatFactory.getFormat(field.getType(), pattern, getLocale(), dataField.precision());

            // field object to be set
            Object modelField = model.get(field.getDeclaringClass().getName());

            // format the data received
            Object value = null;

            if (!token.equals("")) {
                try {
                    value = format.parse(token);
                } catch (FormatException ie) {
                    throw new IllegalArgumentException(ie.getMessage() + ", position: " + offset + ", line: " + line, ie);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Parsing error detected for field defined at the position/offset: " + offset + ", line: " + line, e);
                }
            } else {
                value = getDefaultValueForPrimitive(field.getType());
            }

            field.set(modelField, value);

            ++pos;
        
        }

        LOG.debug("Counter mandatory fields: {}", counterMandatoryFields);

        if (pos < totalFields) {
            throw new IllegalArgumentException("Some fields are missing (optional or mandatory), line: " + line);
        }

        if (counterMandatoryFields < numberMandatoryFields) {
            throw new IllegalArgumentException("Some mandatory fields are missing, line: " + line);
        }  
        
    }

    public String unbind(Map<String, Object> model) throws Exception {

        StringBuilder buffer = new StringBuilder();
        Map<Integer, List<String>> results = new HashMap<Integer, List<String>>();

        for (Class<?> clazz : models) {

            if (model.containsKey(clazz.getName())) {

                Object obj = model.get(clazz.getName());

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Model object: {}, class: {}", obj, obj.getClass().getName());
                }

                if (obj != null) {

                    // Generate Fixed Length table
                    // containing the positions of the fields
                    generateFixedLengthPositionMap(clazz, obj, results);

                }
            }
        }

        // Convert Map<Integer, List> into List<List>
        TreeMap<Integer, List<String>> sortValues = new TreeMap<Integer, List<String>>(results);
        for (Entry<Integer, List<String>> entry : sortValues.entrySet()) {

            // Get list of values
            List<String> val = entry.getValue();
            String value = val.get(0);
            
            buffer.append(value);
        }
        
        return buffer.toString();
    }

    /**
     * 
     * Generate a table containing the data formatted and sorted with their position/offset
     * The result is placed in the Map<Integer, List> results
     */
    private void generateFixedLengthPositionMap(Class<?> clazz, Object obj, Map<Integer, List<String>> results) throws Exception {

        String result = "";

        for (Field field : clazz.getDeclaredFields()) {

            field.setAccessible(true);

            DataField datafield = field.getAnnotation(DataField.class);

            if (datafield != null) {

                if (obj != null) {

                    // Retrieve the format, pattern and precision associated to
                    // the type
                    Class<?> type = field.getType();
                    String pattern = datafield.pattern();
                    int precision = datafield.precision();



                    // Create format
                    Format<?> format = FormatFactory.getFormat(type, pattern, getLocale(), precision);

                    // Get field value
                    Object value = field.get(obj);


                    result = formatString(format, value);

                    // trim if enabled
                    if (datafield.trim()) {
                        result = result.trim();
                    }

                    // Get length of the field, alignment (LEFT or RIGHT), pad
                    int fieldLength = datafield.length();
                    String align = datafield.align();
                    char padCharField = datafield.paddingChar();
                    char padChar;
                    
                    if (fieldLength > 0) {
                       
                        StringBuilder temp = new StringBuilder();

                        // Check if we must pad
                        if (result.length() < fieldLength) {

                            // No padding defined for the field
                            if (padCharField == 0) {
                                // We use the padding defined for the Record
                                padChar = paddingChar;
                            } else {
                                padChar = padCharField;
                            }

                            if (align.contains("R")) {
                                temp.append(generatePaddingChars(padChar, fieldLength, result.length()));
                                temp.append(result);
                            } else if (align.contains("L")) {
                                temp.append(result);
                                temp.append(generatePaddingChars(padChar, fieldLength, result.length()));
                            } else {
                                throw new IllegalArgumentException("Alignment for the field: " + field.getName()
                                        + " must be equal to R for RIGHT or L for LEFT");
                            }

                            result = temp.toString();
                        } else if (result.length() > fieldLength) {
                            // we are bigger than allowed

                            // is clipped enabled? if so clip the field
                            if (datafield.clip()) {
                                result = result.substring(0, fieldLength);
                            } else {
                                throw new IllegalArgumentException("Length for the " + field.getName()
                                        + " must not be larger than allowed, was: " + result.length() + ", allowed: " + fieldLength);
                            }
                        }

                    } else {
                        throw new IllegalArgumentException("Length of the field: " + field.getName()
                                + " is a mandatory field and cannot be equal to zero or to be negative, was: " + fieldLength);
                    }

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Value to be formatted: {}, position: {}, and its formatted value: {}", new Object[]{value, datafield.pos(), result});
                    }

                } else {
                    result = "";
                }

                Integer key;
                key = datafield.pos();

                if (!results.containsKey(key)) {
                    List<String> list = new LinkedList<String>();
                    list.add(result);
                    results.put(key, list);
                } else {
                    List<String> list = results.get(key);
                    list.add(result);
                }

            }

        }

    }
    
    private String generatePaddingChars(char pad, int lengthField, int lengthString) {
        StringBuilder buffer = new StringBuilder();
        int size = lengthField - lengthString;

        for (int i = 0; i < size; i++) {
            buffer.append(Character.toString(pad));
        }
        return buffer.toString();
    }

    /**
     * Get parameters defined in @FixedLengthRecord annotation
     */
    private void initFixedLengthRecordParameters() {

        for (Class<?> cl : models) {

            // Get annotation @FixedLengthRecord from the class
            FixedLengthRecord record = cl.getAnnotation(FixedLengthRecord.class);

            if (record != null) {
                LOG.debug("Fixed length record: {}", record);

                // Get carriage return parameter
                crlf = record.crlf();
                LOG.debug("Carriage return defined for the CSV: {}", crlf);

                // Get hasHeader parameter
                hasHeader = record.hasHeader();
                LOG.debug("Has Header: {}", hasHeader);

                // Get hasFooter parameter
                hasFooter = record.hasFooter();
                LOG.debug("Has Footer: {}", hasFooter);

                // Get padding character
                paddingChar = record.paddingChar();
                LOG.debug("Padding char: {}", paddingChar);

                // Get length of the record
                recordLength = record.length();
                LOG.debug("Length of the record: {}", recordLength);

                // Get length of the record
                recordLength = record.length();
                LOG.debug("Length of the record: {}", recordLength);
            }
        }
    }

    /**
     * Flag indicating if we have a header
     */
    public boolean hasHeader() {
        return hasHeader;
    }
    
    /**
     * Flag indicating if we have a footer
     */
    public boolean hasFooter() {
        return hasFooter;
    }
    
    /**
     * Padding char used to fill the field
     */
    public char paddingchar() {
        return paddingChar;
    }

    public int recordLength() {
        return recordLength;
    }

}
