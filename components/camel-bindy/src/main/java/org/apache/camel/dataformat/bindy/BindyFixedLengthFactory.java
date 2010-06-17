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
import java.util.TreeMap;

import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.annotation.FixedLengthRecord;
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
 * to a fixed length record, bind data from a record to the POJOs, export data of POJOs
 * to a fixed length record and format data into String, Date, Double, ... according to
 * the format/pattern defined
 */
public class BindyFixedLengthFactory extends BindyAbstractFactory implements BindyFactory {

    private static final transient Log LOG = LogFactory.getLog(BindyFixedLengthFactory.class);

    boolean isOneToMany;

    private Map<Integer, DataField> dataFields = new LinkedHashMap<Integer, DataField>();
    private Map<Integer, Field> annotedFields = new LinkedHashMap<Integer, Field>();

    private Map<Integer, List> results;

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

    /**
     * method uses to initialize the model representing the classes who will
     * bind the data. This process will scan for classes according to the
     * package name provided, check the annotated classes and fields
     * 
     * @throws Exception
     */
    public void initFixedLengthModel() throws Exception {

        // Find annotated fields declared in the Model classes
        initAnnotedFields();

        // initialize Fixed length parameter(s)
        // from @FixedLengthrecord annotation
        initFixedLengthRecordParameters();
    }

    public void initAnnotedFields() {

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
                annotedLinkFields.put(cl.getName(), linkFields);
            }

            totalFields = numberMandatoryFields + numberOptionalFields;

            if (LOG.isDebugEnabled()) {
                LOG.debug("Number of optional fields : " + numberOptionalFields);
                LOG.debug("Number of mandatory fields : " + numberMandatoryFields);
                LOG.debug("Total : " + totalFields);
            }

        }
    }
    
    // Will not be used in the case of a Fixed Length record
    // as we provide the content of the record and 
    // we don't split it as this is the case for a CSV record
	@Override
	public void bind(List<String> data, Map<String, Object> model, int line)
			throws Exception {
		// TODO Auto-generated method stub
		
	}

    public void bind(String record, Map<String, Object> model, int line) throws Exception {

        int pos = 1;
        int counterMandatoryFields = 0;
        DataField dataField;
        StringBuilder result = new StringBuilder();
        String token;
        int offset;
        int length;
        Field field;
        String pattern;

        // Iterate through the list of positions
        // defined in the @DataFieldf
        // and grab the data from the line
        Collection c = dataFields.values();
        Iterator itr = c.iterator();

        while(itr.hasNext()) {
        	dataField = (DataField) itr.next();
            offset = dataField.pos();
            length = dataField.length();
            
            ObjectHelper.notNull(offset, "Position/offset is not defined for  the  field " + dataField.toString());
            ObjectHelper.notNull(offset, "Length is not defined for the  field " + dataField.toString());
            
            if (offset-1 <= -1 ) {
            	throw new IllegalArgumentException("Offset / Position of the field " + dataField.toString() + " cannot be negative !");
            }
            
        	token = record.substring(offset-1, offset+length-1);
        	
        	// Check mandatory field
            if (dataField.required()) {
            	
                // Increment counter of mandatory fields
                ++counterMandatoryFields;

                // Check if content of the field is empty
                // This is not possible for mandatory fields
                if (token.equals("")) {
                    throw new IllegalArgumentException("The mandatory field defined at the position " + pos + " is empty for the line : " + line);
                }
            }
            
            // Get Field to be setted
            field = annotedFields.get(offset);
            field.setAccessible(true);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Pos/Offset : " + offset + ", Data : " + token + ", Field type : " + field.getType());
            }
            
            Format<?> format;

            // Get pattern defined for the field
            pattern = dataField.pattern();

            // Create format object to format the field
            format = FormatFactory.getFormat(field.getType(), pattern, dataField.precision());

            // field object to be set
            Object modelField = model.get(field.getDeclaringClass().getName());

            // format the data received
            Object value = null;

            if (!token.equals("")) {
                try {
                    value = format.parse(token);
                } catch (FormatException ie) {
                    throw new IllegalArgumentException(ie.getMessage() + ", position : " + offset + ", line : " + line, ie);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Parsing error detected for field defined at the position/offset : " + offset + ", line : " + line, e);
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
                    Format format = FormatFactory.getFormat(type, pattern, precision);

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
    **/
    
    private String formatString(Format format, Object value) throws Exception {

        String strValue = "";

        if (value != null) {

            // Format field value
            try {
                strValue = format.format(value);
            } catch (Exception e) {
                throw new IllegalArgumentException("Formatting error detected for the value : " + value, e);
            }

        }

        return strValue;

    }

    /**
     * Get parameters defined in @FixedLengthRecord annotation
     */
    private void initFixedLengthRecordParameters() {

            for (Class<?> cl : models) {

                // Get annotation @FixedLengthRecord from the class
            	FixedLengthRecord record = cl.getAnnotation(FixedLengthRecord.class);

                if (record != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Fixed length record : " + record.toString());
                    }

                    // Get carriage return parameter
                    crlf = record.crlf();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Carriage return defined for the CSV : " + crlf);
                    }

                    // Get hasHeader parameter
                    hasHeader = record.hasHeader();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Has Header :  " + hasHeader);
                    }
                    
                    // Get hasFooter parameter
                    hasFooter = record.hasFooter();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Has Footer :  " + hasFooter);
                    }
                    
                    // Get padding character
                    paddingChar = record.paddingChar();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Padding char :  " + paddingChar);
                    }                    
                    
                    // Get length of the record
                    recordLength = record.length();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Length of the record :  " + recordLength);
                    } 
                    

                }
        }
    }

    /**
     * Flag indicating if we have a header
     * 
     * @return boolean
     */
    public boolean hasHeader() {
        return hasHeader;
    }
    
    /**
     * Flag indicating if we have a footer
     * 
     * @return boolean
     */
    public boolean hasFooter() {
        return hasFooter;
    }
    
    /**
     * Padding char used to fill the field
     * 
     * @return char
     */
    public char paddingchar() {
    	return paddingChar;
    }
    
    public int recordLength() {
    	return recordLength;
    }

}
