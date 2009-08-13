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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarException;

import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.annotation.Link;
import org.apache.camel.dataformat.bindy.annotation.Section;
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

    private Map<Integer, DataField> dataFields = new LinkedHashMap<Integer, DataField>();
    private Map<Integer, Field> annotedFields = new LinkedHashMap<Integer, Field>();
    private Map<String, Integer> sections = new HashMap<String, Integer>();
    private int numberOptionalFields;
    private int numberMandatoryFields;
    private int totalFields;

    private String separator;
    private boolean skipFirstLine;
    private boolean messageOrdered;

    public BindyCsvFactory(PackageScanClassResolver resolver, String... packageNames) throws Exception {
        super(resolver, packageNames);

        // initialize specific parameters of the csv model
        initCsvModel();
    }

    /**
     * method uses to initialize the model representing the classes who will
     * bind the data. This process will scan for classes according to the package
     * name provided, check the annotated classes and fields and retrieve the
     * separator of the CSV record
     * 
     * @throws Exception
     */
    public void initCsvModel() throws Exception {

        // Find annotated Datafields declared in the Model classes
        initAnnotedFields();

        // initialize Csv parameter(s)
        // separator and skip first line from @CSVrecord annotation
        initCsvRecordParameters();
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
                        LOG.debug("Position defined in the class : " + cl.getName() + ", position : "
                                  + dataField.pos() + ", Field : " + dataField.toString());
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

    public void bind(List<String> tokens, Map<String, Object> model) throws Exception {

        int pos = 0;
        int counterMandatoryFields = 0;
 
        for (String data : tokens) {
        
            // Get DataField from model
            DataField dataField = dataFields.get(pos);
            ObjectHelper.notNull(dataField, "No position " + pos + " defined for the field : " + data);
            
            if (dataField.required()) {
                // Increment counter of mandatory fields
                ++counterMandatoryFields;

                // Check if content of the field is empty
                // This is not possible for mandatory fields
                if (data.equals("")) {
                    throw new IllegalArgumentException("The mandatory field defined at the position " + pos
                                                       + " is empty !");
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
            format = FormatFactory.getFormat(field.getType(), pattern, dataField.precision());
            
            // field object to be set
            Object modelField = model.get(field.getDeclaringClass().getName());
            
            // format the data received
            Object value = null;
            
            if (!data.equals("")) {
                value = format.parse(data);
            } else {
                value = getDefaultValueforPrimitive(field.getType());
            }
            
            field.set(modelField, value);
            
            ++pos;            
            
        }
        
        if (LOG.isDebugEnabled()) {
            LOG.debug("Counter mandatory fields : " + counterMandatoryFields);
        }
        
     
        if (pos < totalFields) {
            throw new IllegalArgumentException("Some fields are missing (optional or mandatory) !!");
        }

        if (counterMandatoryFields < numberMandatoryFields) {
            throw new IllegalArgumentException("Some mandatory fields are missing !!");
        }
        
    }

    public String unbind(Map<String, Object> model) throws Exception {

        StringBuilder builder = new StringBuilder();

        Map<Integer, DataField> dataFieldsSorted = new TreeMap<Integer, DataField>(dataFields);
        Iterator<Integer> it = dataFieldsSorted.keySet().iterator();
        
        // Map containing the OUT position of the field
        // The key is double and is created using the position of the field and 
        // location of the class in the message (using section)
        Map<Integer, String> positions = new TreeMap<Integer, String>();

        // Check if separator exists
        ObjectHelper.notNull(this.separator, "The separator has not been instantiated or property not defined in the @CsvRecord annotation");
        
        char separator = Converter.getCharDelimitor(this.getSeparator());

        if (LOG.isDebugEnabled()) {
            LOG.debug("Separator converted : '0x" + Integer.toHexString(separator) + "', from : "
                    + this.getSeparator());
        }

        while (it.hasNext()) {

            DataField dataField = dataFieldsSorted.get(it.next());

            // Retrieve the field
            Field field = annotedFields.get(dataField.pos());
            // Change accessibility to allow to read protected/private fields
            field.setAccessible(true);

            // Retrieve the format, pattern and precision associated to the type
            Class type = field.getType();
            String pattern = dataField.pattern();
            int precision = dataField.precision();
            
            // Create format
            Format format = FormatFactory.getFormat(type, pattern, precision);
            
            // Get field from model
            Object modelField = model.get(field.getDeclaringClass().getName());
            
            if (modelField != null) {
                // Get field value
                Object value = field.get(modelField);
                String strValue = null;

                if (this.isMessageOrdered()) {

                    // Generate a key using the number of the section
                    // and the position of the field
                    Integer key1 = sections.get(modelField.getClass().getName());
                    Integer key2 = dataField.position();
                    Integer keyGenerated = generateKey(key1, key2);
                    
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Key generated : " + String.valueOf(keyGenerated) + ", for section : " + key1);
                    }                    
                    
                    // Get field value
                    //Object value = field.get(modelField);
                    
                    if (value != null) {
                        // Format field value
                        strValue = format.format(value);
                    } 
                    
                    // Add the content to the TreeMap according to the
                    // position defined
                    positions.put(keyGenerated, strValue);

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Positions size : " + positions.size());
                    }
                        
                } else {
                    // Get field value
                    //Object value = field.get(modelField);
                    //String strValue = null;

                    // Add value to the list if not null
                    if (value != null) {

                        // Format field value
                        strValue = format.format(value);
                        
                    }
                    
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Data : " + value + ", value : " + strValue);
                    }
                    
                    builder.append(strValue);

                    if (it.hasNext()) {
                        builder.append(separator);
                    }
                }
            }
        }
        
        // Iterate through the list to generate
        // the message according to the order/position
        if (this.isMessageOrdered()) {

            Iterator<Integer> posit = positions.keySet().iterator();
            
            while (posit.hasNext()) {
                String value = positions.get(posit.next());
                
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Value added at the position (" + posit + ") : " + value + separator);
                }
                
                builder.append(value);
                if (it.hasNext()) {
                    builder.append(separator);
                }
            }
        }
        
        return builder.toString();
    }

    /**
     * Find the separator used to delimit the CSV fields
     */
    public String getSeparator() {
        return separator;
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

    /**
     * 
     * Get paramaters defined in @Csvrecord annotation
     * 
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

                    // Get Separator parameter
                    ObjectHelper.notNull(record.separator(),
                            "No separator has been defined in the @Record annotation !");
                    separator = record.separator();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Separator defined for the CSV : " + separator);
                    }

                    // Get carriage return parameter
                    crlf = record.crlf();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Carriage return defined for the CSV : " + crlf);
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
}
