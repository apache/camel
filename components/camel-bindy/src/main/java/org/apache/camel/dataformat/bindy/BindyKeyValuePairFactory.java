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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.plaf.basic.BasicBorders.SplitPaneBorder;

import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.annotation.KeyValuePairField;
import org.apache.camel.dataformat.bindy.annotation.Link;
import org.apache.camel.dataformat.bindy.annotation.Message;
import org.apache.camel.dataformat.bindy.util.Converter;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The BindyKeyValuePairFactory is the class who allows to bind data of type
 * key value pair. Such format exist in financial messages FIX.
 * This class allows to generate a model associated to message, bind data from a message
 * to the POJOs, export data of POJOs to a message and format data
 * into String, Date, Double, ... according to the format/pattern defined
 */
public class BindyKeyValuePairFactory extends BindyAbstractFactory implements BindyFactory  {

    private static final transient Log LOG = LogFactory.getLog(BindyKeyValuePairFactory.class);

    private Map<Integer, KeyValuePairField> mapKeyValuePairField = new LinkedHashMap<Integer, KeyValuePairField>();
    private Map<Integer, Field> mapAnnotedField = new LinkedHashMap<Integer, Field>();

    private String keyValuePairSeparator;
    private String pairSeparator;

    public BindyKeyValuePairFactory(PackageScanClassResolver resolver, String packageName) throws Exception {
        
        super(resolver, packageName);
        
        // Initialize what is specific to Key Value Pair model
        initKeyValuePairModel();
    }

    /**
     * method uses to initialize the model representing the classes who will
     * bind the data This process will scan for classes according to the package
     * name provided, check the classes and fields annoted. Next, we retrieve the
     * parameters required like : Pair Separator & key value pair separator
     * 
     * @throws Exception
     */
    public void initKeyValuePairModel() throws Exception {
        
        // Find annotated KeyValuePairfields declared in the Model classes
        initAnnotedFields();
        
        // Initialize key value pair parameter(s) 
        initMessageParameters();

    }
    
    
    public void initAnnotedFields() {

        for (Class<?> cl : models) {

            for (Field field : cl.getDeclaredFields()) {
                KeyValuePairField keyValuePairField = field.getAnnotation(KeyValuePairField.class);
                if (keyValuePairField != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Key declared in the class : " + cl.getName() + ", key : "
                            + keyValuePairField.tag() + ", Field : " + keyValuePairField.toString());
                    }
                    mapKeyValuePairField.put(keyValuePairField.tag(), keyValuePairField);
                    mapAnnotedField.put(keyValuePairField.tag(), field);
                }

                Link linkField = field.getAnnotation(Link.class);

                if (linkField != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Class linked  : " + cl.getName() + ", Field" + field.toString());
                    }
                    mapAnnotedLinkField.put(cl.getName(), field);
                }
            }

        }
    }
    

    public void bind(List<String> data, Map<String, Object> model) throws Exception {

        int pos = 0;
        
        if (LOG.isDebugEnabled()) {
            LOG.debug("Data : " + data);
        }

        while (pos < data.size()) {
        
            if (!data.get(pos).equals("")) {
           
                // Separate the key from its value
                // e.g 8=FIX 4.1 --> key = 8 and Value = FIX 4.1
                ObjectHelper.notNull(this.keyValuePairSeparator, "Key Value Pair not defined in the @Message annotation");
                String[] keyValuePair = data.get(pos).split(this.getKeyValuePairSeparator());
                
                int tag = Integer.parseInt(keyValuePair[0]);
                String value = keyValuePair[1];
                
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Key : " + tag + ", value : " + value);
                }

                KeyValuePairField keyValuePairField = mapKeyValuePairField.get(tag);
                ObjectHelper.notNull(keyValuePairField, "No tag defined for the field : " + tag);

                Field field = mapAnnotedField.get(tag);
                field.setAccessible(true);

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Tag : " + tag + ", Data : " + value + ", Field type : " + field.getType());
                }

                Format<?> format;
                String pattern = keyValuePairField.pattern();

                format = FormatFactory.getFormat(field.getType(), pattern, keyValuePairField.precision());
                field.set(model.get(field.getDeclaringClass().getName()), format.parse(value));

            }

            pos++;
        }

    }

    public String unbind(Map<String, Object> model) throws Exception {

        StringBuilder builder = new StringBuilder();

        Map<Integer, KeyValuePairField> keyValuePairFields = new TreeMap<Integer, KeyValuePairField>(mapKeyValuePairField);
        Iterator<Integer> it = keyValuePairFields.keySet().iterator();

        // Check if separator exists
        ObjectHelper.notNull(this.pairSeparator,
             "The pair separator has not been instantiated or property not defined in the @Message annotation");

        char separator = Converter.getCharDelimitor(this.getPairSeparator());

        if (LOG.isDebugEnabled()) {
            LOG.debug("Separator converted : '0x" + Integer.toHexString(separator) + "', from : " + this.getPairSeparator());
        }

        while (it.hasNext()) {

            KeyValuePairField keyValuePairField = mapKeyValuePairField.get(it.next());
            ObjectHelper.notNull(keyValuePairField, "KeyValuePair is null !");

            // Retrieve the field
            Field field = mapAnnotedField.get(keyValuePairField.tag());
            // Change accessibility to allow to read protected/private fields
            field.setAccessible(true);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Tag : " + keyValuePairField.tag() + ", Field type : " + field.getType()
                    + ", class : " + field.getDeclaringClass().getName());
            }

            // Retrieve the format associated to the type
            Format format;

            String pattern = keyValuePairField.pattern();
            format = FormatFactory.getFormat(field.getType(), pattern, keyValuePairField.precision());

            Object obj = model.get(field.getDeclaringClass().getName());

            if (LOG.isDebugEnabled()) {
                LOG.debug("Model object : " + obj.toString());
            }

            // Convert the content to a String and append it to the builder
            // Add the tag followed by its key value pair separator
            // the data and finish by the pair separator
            builder.append(keyValuePairField.tag() + this.getKeyValuePairSeparator() + 
                format.format(field.get(obj)) + separator);
        }

        return builder.toString();
    }

    /**
     * Find the pair separator used to delimit the key value pair fields
     */
    public String getPairSeparator() {
        return pairSeparator;
    }
    
    /**
     * Find the key value pair separator used to link the key with its value
     */
    public String getKeyValuePairSeparator() {
        return keyValuePairSeparator;
    }

    /**
     * Get parameters defined in @Message annotation
     */
    private void initMessageParameters() {

        if ((pairSeparator == null) || (keyValuePairSeparator == null)) {

            for (Class<?> cl : models) {
                
                // Get annotation @Message from the class
                Message message = cl.getAnnotation(Message.class);

                if (message != null) {
                    
                    // Get Pair Separator parameter
                    ObjectHelper.notNull(message.pairSeparator(),
                        "No Pair Separator has been defined in the @Message annotation !");
                    pairSeparator = message.pairSeparator();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Pair Separator defined for the message : " + pairSeparator);
                    }

                    // Get KeyValuePair Separator parameter
                    ObjectHelper.notNull(message.keyValuePairSeparator(),
                        "No Key Value Pair Separator has been defined in the @Message annotation !");
                    keyValuePairSeparator = message.keyValuePairSeparator();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Key Value Pair Separator defined for the message : "
                            + keyValuePairSeparator);
                    }

                    // Get carriage return parameter
                    crlf = message.crlf();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Carriage return defined for the message : " + crlf);
                    }
                }
            }
        }
    }
}
