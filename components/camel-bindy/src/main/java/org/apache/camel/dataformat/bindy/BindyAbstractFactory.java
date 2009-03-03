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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.dataformat.bindy.util.AnnotationModelLoader;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The BindyAbstractFactory implements what its common to all the 
 * formats supported by camel bindy
 */
public abstract class BindyAbstractFactory implements BindyFactory {
    private static final transient Log LOG = LogFactory.getLog(BindyAbstractFactory.class);
    protected Set<Class> models;
    protected Map<String, Field> mapAnnotedLinkField = new LinkedHashMap<String, Field>();    

    private AnnotationModelLoader modelsLoader;
    
    private String packageName;

    public BindyAbstractFactory(PackageScanClassResolver resolver, String packageName) throws Exception {
        this.modelsLoader = new AnnotationModelLoader(resolver);
        this.packageName = packageName;
        initModel();
    }

    /**
     * method uses to initialize the model representing the classes who will
     * bind the data. This process will scan for classes according to the package
     * name provided, check the classes and fields annoted.
     * 
     * @throws Exception
     */
    public void initModel() throws Exception {

        // Find classes defined as Model
        initModelClasses(packageName);

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
    public abstract void initAnnotedFields() throws Exception;

    public abstract void bind(List<String> data, Map<String, Object> model) throws Exception;

    public abstract String unbind(Map<String, Object> model) throws Exception;

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
     * @return Map is a collection of the objects used to bind data from records, messages
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

}
