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

import org.apache.camel.CamelContext;
import org.apache.camel.dataformat.bindy.annotation.BindyConverter;
import org.apache.camel.dataformat.bindy.annotation.KeyValuePairField;
import org.apache.camel.dataformat.bindy.annotation.Link;
import org.apache.camel.dataformat.bindy.annotation.Message;
import org.apache.camel.dataformat.bindy.annotation.OneToMany;
import org.apache.camel.dataformat.bindy.annotation.Section;
import org.apache.camel.dataformat.bindy.util.ConverterUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The BindyKeyValuePairFactory is the class who allows to bind data of type key value pair. Such format exist in
 * financial messages FIX. This class allows to generate a model associated to message, bind data from a message to the
 * POJOs, export data of POJOs to a message and format data into String, Date, Double, ... according to the
 * format/pattern defined
 */
public class BindyKeyValuePairFactory extends BindyAbstractFactory implements BindyFactory {

    private static final Logger LOG = LoggerFactory.getLogger(BindyKeyValuePairFactory.class);

    private Map<Integer, KeyValuePairField> keyValuePairFields = new LinkedHashMap<>();
    private Map<Integer, Field> annotatedFields = new LinkedHashMap<>();
    private Map<String, Integer> sections = new HashMap<>();
    private String keyValuePairSeparator;
    private String pairSeparator;
    private boolean messageOrdered;

    public BindyKeyValuePairFactory(Class<?> type) throws Exception {
        super(type);

        // Initialize what is specific to Key Value Pair model
        initKeyValuePairModel();
    }

    /**
     * method uses to initialize the model representing the classes who will bind the data This process will scan for
     * classes according to the package name provided, check the annotated classes and fields. Next, we retrieve the
     * parameters required like : Pair Separator & key value pair separator
     *
     * @throws Exception
     */
    public void initKeyValuePairModel() {

        // Find annotated KeyValuePairfields declared in the Model classes
        initAnnotatedFields();

        // Initialize key value pair parameter(s)
        initMessageParameters();

    }

    @Override
    public void initAnnotatedFields() {

        for (Class<?> cl : models) {

            List<Field> linkFields = new ArrayList<>();

            for (Field field : cl.getDeclaredFields()) {
                KeyValuePairField keyValuePairField = field.getAnnotation(KeyValuePairField.class);
                if (keyValuePairField != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Key declared in the class : {}, key : {}, Field : {}", cl.getName(), keyValuePairField.tag(),
                                keyValuePairField);
                    }
                    keyValuePairFields.put(keyValuePairField.tag(), keyValuePairField);
                    annotatedFields.put(keyValuePairField.tag(), field);
                }

                Link linkField = field.getAnnotation(Link.class);

                if (linkField != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Class linked  : {}, Field {}", cl.getName(), field);
                    }
                    linkFields.add(field);
                }
            }

            if (!linkFields.isEmpty()) {
                annotatedLinkFields.put(cl.getName(), linkFields);
            }

        }
    }

    @Override
    public void bind(CamelContext camelContext, List<String> data, Map<String, Object> model, int line) throws Exception {

        // Map to hold the model @OneToMany classes while binding
        Map<String, List<Object>> lists = new HashMap<>();

        bind(data, model, line, lists);
    }

    public void bind(
            List<String> data, Map<String, Object> model, int line, Map<String, List<Object>> lists)
            throws Exception {

        Map<Integer, List<String>> results = new HashMap<>();

        LOG.debug("Key value pairs data : {}", data);

        // Separate the key from its value
        // e.g 8=FIX 4.1 --> key = 8 and Value = FIX 4.1
        ObjectHelper.notNull(keyValuePairSeparator, "Key Value Pair not defined in the @Message annotation");

        // Generate map of key value
        // We use a Map of List as we can have the same key several times
        // (relation one to many)
        for (String s : data) {

            // Get KeyValuePair
            String[] keyValuePair = s.split(getKeyValuePairSeparator());

            // Extract only if value is populated in key:value pair in incoming message.
            if (keyValuePair.length > 1) {
                // Extract Key
                int key = Integer.parseInt(keyValuePair[0]);

                // Extract key value
                String value = keyValuePair[1];

                LOG.debug("Key: {}, value: {}", key, value);

                // Add value to the Map using key value as key
                if (!results.containsKey(key)) {
                    List<String> list = new LinkedList<>();
                    list.add(value);
                    results.put(key, list);
                } else {
                    List<String> list = results.get(key);
                    list.add(value);
                }
            }

        }

        // Iterate over the model
        for (Class<?> clazz : models) {

            Object obj = model.get(clazz.getName());

            if (obj != null) {

                // Generate model from key value map
                generateModelFromKeyValueMap(clazz, obj, results, line, lists);

            }
        }

    }

    private void generateModelFromKeyValueMap(
            Class<?> clazz, Object obj, Map<Integer, List<String>> results, int line, Map<String, List<Object>> lists)
            throws Exception {

        for (Field field : clazz.getDeclaredFields()) {

            field.setAccessible(true);

            KeyValuePairField keyValuePairField = field.getAnnotation(KeyValuePairField.class);

            if (keyValuePairField != null) {

                // Key
                int key = keyValuePairField.tag();

                // Get Value
                List<String> values = results.get(key);
                String value = null;

                // we don't received data
                if (values == null) {

                    /*
                     * The relation is one to one So we check if we are in a
                     * target class and if the field is mandatory
                     */
                    if (obj != null) {

                        // Check mandatory field
                        if (keyValuePairField.required()) {
                            throw new IllegalArgumentException("The mandatory key/tag : " + key + " has not been defined !");
                        }

                        Object result = getDefaultValueForPrimitive(field.getType());

                        try {
                            field.set(obj, result);
                        } catch (Exception e) {
                            throw new IllegalArgumentException(
                                    "Setting of field " + field + " failed for object : " + obj + " and result : " + result);
                        }

                    } else {

                        /*
                         * The relation is one to many So, we create an object
                         * with empty fields and we don't check if the fields
                         * are mandatory
                         */

                        // Get List from Map
                        List<Object> l = lists.get(clazz.getName());

                        if (l != null) {

                            // BigIntegerFormatFactory if object exist
                            if (!l.isEmpty()) {
                                obj = l.get(0);
                            } else {
                                obj = clazz.newInstance();
                            }

                            Object result = getDefaultValueForPrimitive(field.getType());
                            try {
                                field.set(obj, result);
                            } catch (Exception e) {
                                throw new IllegalArgumentException(
                                        "Setting of field " + field + " failed for object : " + obj + " and result : "
                                                                   + result);
                            }

                            // Add object created to the list
                            if (!l.isEmpty()) {
                                l.set(0, obj);
                            } else {
                                l.add(0, obj);
                            }

                            // and to the Map
                            lists.put(clazz.getName(), l);

                            // Reset obj to null
                            obj = null;

                        } else {
                            throw new IllegalArgumentException(
                                    "The list of values is empty for the following key : " + key + " defined in the class : "
                                                               + clazz.getName());
                        }

                    } // end of test if obj != null

                } else {

                    // Data have been retrieved from message
                    if (!values.isEmpty()) {

                        if (obj != null) {

                            // Relation OneToOne
                            value = values.get(0);
                            Object result = null;

                            if (value != null) {

                                // Create format object to format the field
                                FormattingOptions formattingOptions = ConverterUtils.convert(keyValuePairField,
                                        field.getType(),
                                        field.getAnnotation(BindyConverter.class),
                                        getLocale());
                                Format<?> format = formatFactory.getFormat(formattingOptions);

                                // format the value of the key received
                                result = formatField(format, value, key, line);

                                LOG.debug("Value formated : {}", result);

                            } else {
                                result = getDefaultValueForPrimitive(field.getType());
                            }
                            try {
                                field.set(obj, result);
                            } catch (Exception e) {
                                throw new IllegalArgumentException(
                                        "Setting of field " + field + " failed for object : " + obj + " and result : "
                                                                   + result);
                            }

                        } else {

                            // Get List from Map
                            List<Object> l = lists.get(clazz.getName());

                            if (l != null) {

                                // Relation OneToMany
                                for (int i = 0; i < values.size(); i++) {

                                    // BigIntegerFormatFactory if object exist
                                    if (!l.isEmpty() && l.size() > i) {
                                        obj = l.get(i);
                                    } else {
                                        obj = clazz.newInstance();
                                    }

                                    value = values.get(i);

                                    // Create format object to format the field
                                    FormattingOptions formattingOptions = ConverterUtils.convert(keyValuePairField,
                                            field.getType(),
                                            field.getAnnotation(BindyConverter.class),
                                            getLocale());
                                    Format<?> format = formatFactory.getFormat(formattingOptions);

                                    // format the value of the key received
                                    Object result = formatField(format, value, key, line);

                                    LOG.debug("Value formated : {}", result);

                                    try {
                                        if (value != null) {
                                            field.set(obj, result);
                                        } else {
                                            field.set(obj, getDefaultValueForPrimitive(field.getType()));
                                        }
                                    } catch (Exception e) {
                                        throw new IllegalArgumentException(
                                                "Setting of field " + field + " failed for object: " + obj + " and result: "
                                                                           + result);
                                    }

                                    // Add object created to the list
                                    if (!l.isEmpty() && l.size() > i) {
                                        l.set(i, obj);
                                    } else {
                                        l.add(i, obj);
                                    }
                                    // and to the Map
                                    lists.put(clazz.getName(), l);

                                    // Reset obj to null
                                    obj = null;

                                }

                            } else {
                                throw new IllegalArgumentException(
                                        "The list of values is empty for the following key: " + key + " defined in the class: "
                                                                   + clazz.getName());
                            }
                        }

                    } else {

                        // No values found from message
                        Object result = getDefaultValueForPrimitive(field.getType());

                        try {
                            field.set(obj, result);
                        } catch (Exception e) {
                            throw new IllegalArgumentException(
                                    "Setting of field " + field + " failed for object: " + obj + " and result: " + result);
                        }
                    }
                }
            }

            OneToMany oneToMany = field.getAnnotation(OneToMany.class);
            if (oneToMany != null) {

                String targetClass = oneToMany.mappedTo();

                if (!targetClass.isEmpty()) {
                    // Class cl = Class.forName(targetClass); Does not work in
                    // OSGI when class is defined in another bundle
                    Class<?> cl = null;

                    try {
                        cl = Thread.currentThread().getContextClassLoader().loadClass(targetClass);
                    } catch (ClassNotFoundException e) {
                        cl = getClass().getClassLoader().loadClass(targetClass);
                    }

                    if (!lists.containsKey(cl.getName())) {
                        lists.put(cl.getName(), new ArrayList<>());
                    }

                    generateModelFromKeyValueMap(cl, null, results, line, lists);

                    // Add list of objects
                    field.set(obj, lists.get(cl.getName()));

                } else {
                    throw new IllegalArgumentException("No target class has been defined in @OneToMany annotation");
                }

            }

        }

    }

    /**
     *
     */
    @Override
    public String unbind(CamelContext camelContext, Map<String, Object> model) throws Exception {

        StringBuilder builder = new StringBuilder();

        Map<Integer, KeyValuePairField> keyValuePairFieldsSorted = new TreeMap<>(keyValuePairFields);
        Iterator<Integer> it = keyValuePairFieldsSorted.keySet().iterator();

        // Map containing the OUT position of the field
        // The key is double and is created using the position of the field and
        // location of the class in the message (using section)
        Map<Integer, String> positions = new TreeMap<>();

        // Check if separator exists
        ObjectHelper.notNull(this.pairSeparator,
                "The pair separator has not been instantiated or property not defined in the @Message annotation");

        char separator = ConverterUtils.getCharDelimiter(this.getPairSeparator());

        if (LOG.isDebugEnabled()) {
            LOG.debug("Separator converted: '0x{}', from: {}", Integer.toHexString(separator), this.getPairSeparator());
        }

        while (it.hasNext()) {

            KeyValuePairField keyValuePairField = keyValuePairFieldsSorted.get(it.next());
            ObjectHelper.notNull(keyValuePairField, "KeyValuePair");

            // Retrieve the field
            Field field = annotatedFields.get(keyValuePairField.tag());
            // Change accessibility to allow to read protected/private fields
            field.setAccessible(true);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Tag: {}, Field type: {}, class: {}", keyValuePairField.tag(), field.getType(),
                        field.getDeclaringClass().getName());
            }

            // Create format
            FormattingOptions formattingOptions = ConverterUtils.convert(keyValuePairField,
                    field.getType(),
                    field.getAnnotation(BindyConverter.class),
                    getLocale());
            Format<Object> format = (Format<Object>) formatFactory.getFormat(formattingOptions);

            // Get object to be formatted
            Object obj = model.get(field.getDeclaringClass().getName());

            if (obj != null) {

                // Get field value
                Object keyValue = field.get(obj);

                if (this.isMessageOrdered()) {
                    // Generate a key using the number of the section
                    // and the position of the field
                    Integer key1 = sections.get(obj.getClass().getName());
                    Integer key2 = keyValuePairField.position();

                    LOG.debug("Key of the section: {}, and the field: {}", key1, key2);

                    Integer keyGenerated = generateKey(key1, key2);

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Key generated: {}, for section: {}", String.valueOf(keyGenerated), key1);
                    }

                    // Add value to the list if not null
                    if (keyValue != null) {

                        // Format field value
                        String valueFormatted;

                        try {
                            valueFormatted = format.format(keyValue);
                        } catch (Exception e) {
                            throw new IllegalArgumentException(
                                    "Formatting error detected for the tag: " + keyValuePairField.tag(), e);
                        }

                        // Create the key value string
                        String value = keyValuePairField.tag() + this.getKeyValuePairSeparator() + valueFormatted;

                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Value to be formatted: {}, for the tag: {}, and its formatted value: {}", keyValue,
                                    keyValuePairField.tag(), valueFormatted);
                        }

                        // Add the content to the TreeMap according to the
                        // position defined
                        positions.put(keyGenerated, value);

                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Positions size: {}", positions.size());
                        }
                    }
                } else {

                    // Add value to the list if not null
                    if (keyValue != null) {

                        // Format field value
                        String valueFormatted;

                        try {
                            valueFormatted = format.format(keyValue);
                        } catch (Exception e) {
                            throw new IllegalArgumentException(
                                    "Formatting error detected for the tag: " + keyValuePairField.tag(), e);
                        }

                        // Create the key value string
                        String value = keyValuePairField.tag() + this.getKeyValuePairSeparator() + valueFormatted + separator;

                        // Add content to the stringBuilder
                        builder.append(value);

                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Value added: {}{}{}{}", keyValuePairField.tag(), this.getKeyValuePairSeparator(),
                                    valueFormatted, separator);
                        }
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
                    LOG.debug("Value added at the position ({}) : {}{}", posit, value, separator);
                }

                builder.append(value).append(separator);
            }
        }

        return builder.toString();
    }

    private Object formatField(Format<?> format, String value, int tag, int line) throws Exception {

        Object obj = null;

        if (value != null) {

            // Format field value
            try {
                obj = format.parse(value);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Parsing error detected for field defined at the tag: " + tag + ", line: " + line, e);
            }

        }

        return obj;

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
     * Flag indicating if the message must be ordered
     *
     * @return boolean
     */
    public boolean isMessageOrdered() {
        return messageOrdered;
    }

    /**
     * Get parameters defined in @Message annotation
     */
    private void initMessageParameters() {
        if (pairSeparator == null || keyValuePairSeparator == null) {
            for (Class<?> cl : models) {
                // Get annotation @Message from the class
                Message message = cl.getAnnotation(Message.class);

                // Get annotation @Section from the class
                Section section = cl.getAnnotation(Section.class);

                if (message != null) {
                    // Get Pair Separator parameter
                    ObjectHelper.notNull(message.pairSeparator(),
                            "No Pair Separator has been defined in the @Message annotation");
                    pairSeparator = message.pairSeparator();
                    LOG.debug("Pair Separator defined for the message: {}", pairSeparator);

                    // Get KeyValuePair Separator parameter
                    ObjectHelper.notNull(message.keyValuePairSeparator(),
                            "No Key Value Pair Separator has been defined in the @Message annotation");
                    keyValuePairSeparator = message.keyValuePairSeparator();
                    LOG.debug("Key Value Pair Separator defined for the message: {}", keyValuePairSeparator);

                    // Get carriage return parameter
                    crlf = message.crlf();
                    LOG.debug("Carriage return defined for the message: {}", crlf);

                    // Get isOrdered parameter
                    messageOrdered = message.isOrdered();
                    LOG.debug("Is the message ordered in output: {}", messageOrdered);
                }

                if (section != null) {
                    // BigIntegerFormatFactory if section number is not null
                    ObjectHelper.notNull(section.number(), "No number has been defined for the section");

                    // Get section number and add it to the sections
                    sections.put(cl.getName(), section.number());
                }
            }
        }
    }

}
