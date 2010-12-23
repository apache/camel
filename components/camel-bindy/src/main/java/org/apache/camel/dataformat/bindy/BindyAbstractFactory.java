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
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.dataformat.bindy.util.AnnotationModelLoader;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The {@link BindyAbstractFactory} implements what its common to all the formats
 * supported by Camel Bindy
 */
public abstract class BindyAbstractFactory implements BindyFactory {
    private static final transient Log LOG = LogFactory.getLog(BindyAbstractFactory.class);
    protected final Map<String, List<Field>> annotatedLinkFields = new LinkedHashMap<String, List<Field>>();
    protected Set<Class<?>> models;
    protected String crlf;

    private AnnotationModelLoader modelsLoader;
    private String[] packageNames;
    private String locale;

    public BindyAbstractFactory(PackageScanClassResolver resolver, String... packageNames) throws Exception {
        this.modelsLoader = new AnnotationModelLoader(resolver);
        this.packageNames = packageNames;

        if (LOG.isDebugEnabled()) {
            for (String str : this.packageNames) {
                LOG.debug("Package name: " + str);
            }
        }

        initModel();
    }

    /**
     * method uses to initialize the model representing the classes who will
     * bind the data. This process will scan for classes according to the
     * package name provided, check the annotated classes and fields.
     *
     * @throws Exception
     */
    public void initModel() throws Exception {
        // Find classes defined as Model
        initModelClasses(this.packageNames);
    }

    /**
     * Find all the classes defined as model
     */
    private void initModelClasses(String... packageNames) throws Exception {
        models = modelsLoader.loadModels(packageNames);
    }

    /**
     * Find fields annotated in each class of the model
     */
    public abstract void initAnnotatedFields() throws Exception;

    public abstract void bind(List<String> data, Map<String, Object> model, int line) throws Exception;
    
    public abstract String unbind(Map<String, Object> model) throws Exception;

    /**
     * Link objects together
     */
    public void link(Map<String, Object> model) throws Exception {

        // Iterate class by class
        for (String link : annotatedLinkFields.keySet()) {
            List<Field> linkFields = annotatedLinkFields.get(link);

            // Iterate through Link fields list
            for (Field field : linkFields) {

                // Change protection for private field
                field.setAccessible(true);

                // Retrieve linked object
                String toClassName = field.getType().getName();
                Object to = model.get(toClassName);

                ObjectHelper.notNull(to, "No @link annotation has been defined for the oject to link");
                field.set(model.get(field.getDeclaringClass().getName()), to);
            }
        }
    }

    /**
     * Factory method generating new instances of the model and adding them to a
     * HashMap
     *
     * @return Map is a collection of the objects used to bind data from
     *         records, messages
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
     * Generate a unique key
     *
     * @param key1 The key of the section number
     * @param key2 The key of the position of the field
     * @return the key generated
     */
    protected static Integer generateKey(Integer key1, Integer key2) {
        String key2Formated;
        String keyGenerated;

        // Test added for ticket - camel-2773
        if ((key1 != null) && (key2 != null)) {
            key2Formated = getNumberFormat().format((long) key2);
            keyGenerated = String.valueOf(key1) + key2Formated;
        } else {
            throw new IllegalArgumentException("@Section and/or @KeyValuePairDataField have not been defined!");
        }

        return Integer.valueOf(keyGenerated);
    }

    private static NumberFormat getNumberFormat() {
        // Get instance of NumberFormat
        NumberFormat nf = NumberFormat.getInstance();

        // set max number of digits to 3 (thousands)
        nf.setMaximumIntegerDigits(3);
        nf.setMinimumIntegerDigits(3);

        return nf;
    }

    public static Object getDefaultValueForPrimitive(Class<?> clazz) throws Exception {
        if (clazz == byte.class) {
            return Byte.MIN_VALUE;
        } else if (clazz == short.class) {
            return Short.MIN_VALUE;
        } else if (clazz == int.class) {
            return Integer.MIN_VALUE;
        } else if (clazz == long.class) {
            return Long.MIN_VALUE;
        } else if (clazz == float.class) {
            return Float.MIN_VALUE;
        } else if (clazz == double.class) {
            return Double.MIN_VALUE;
        } else if (clazz == char.class) {
            return Character.MIN_VALUE;
        } else if (clazz == boolean.class) {
            return false;
        } else {
            return null;
        }

    }

    /**
     * Find the carriage return set
     */
    public String getCarriageReturn() {
        return crlf;
    }
    
    /**
     * Format the object into a string according to the format rue defined
     */
    @SuppressWarnings("unchecked")
    public String formatString(Format format, Object value) throws Exception {
        String strValue = "";

        if (value != null) {
            try {
                strValue = format.format(value);
            } catch (Exception e) {
                throw new IllegalArgumentException("Formatting error detected for the value: " + value, e);
            }
        }

        return strValue;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }
}
