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
package org.apache.camel.util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.spi.Injector;

/**
 * Finder to find factories from the resource classpath, usually <b>META-INF/services/org/apache/camel/</b>.
 */
public class FactoryFinder {
    protected final ConcurrentHashMap<String, Class> classMap = new ConcurrentHashMap<String, Class>();
    private final String path;    

    public FactoryFinder() {
        this("META-INF/services/org/apache/camel/");
    }

    public FactoryFinder(String path) {
        this.path = path;
    }

    /**
     * Creates a new instance of the given key
     *
     * @param key is the key to add to the path to find a text file containing
     *            the factory name
     * @return a newly created instance
     */
    public Object newInstance(String key) throws IllegalAccessException, InstantiationException, IOException,
        ClassNotFoundException {
        return newInstance(key, (String)null);
    }

    public Object newInstance(String key, String propertyPrefix) throws IllegalAccessException,
        InstantiationException, IOException, ClassNotFoundException {
        Class clazz = findClass(key, propertyPrefix);
        return clazz.newInstance();
    }

    public Object newInstance(String key, Injector injector) throws IOException, ClassNotFoundException {
        return newInstance(key, injector, (String)null);
    }

    public Object newInstance(String key, Injector injector, String propertyPrefix) throws IOException,
        ClassNotFoundException {
        Class<?> type = findClass(key, propertyPrefix);
        return injector.newInstance(type);
    }

    public <T> T newInstance(String key, Injector injector, Class<T> expectedType) throws IOException,
        ClassNotFoundException {
        return newInstance(key, injector, null, expectedType);
    }

    public <T> T newInstance(String key, Injector injector, String propertyPrefix, Class<T> expectedType)
        throws IOException, ClassNotFoundException {
        Class<?> type = findClass(key, propertyPrefix);
        Object value = injector.newInstance(type);
        if (expectedType.isInstance(value)) {
            return expectedType.cast(value);
        } else {
            throw new ClassCastException("Not instanceof " + expectedType.getName() + " value: " + value);
        }
    }

    public <T> List<T> newInstances(String key, Injector injector, Class<T> type) throws IOException,
        ClassNotFoundException {
        List<Class> list = findClasses(key);
        List<T> answer = new ArrayList<T>(list.size());
        answer.add(newInstance(key, injector, type));
        return answer;
    }

    public Class findClass(String key) throws ClassNotFoundException, IOException {
        return findClass(key, null);
    }

    public Class findClass(String key, String propertyPrefix) throws ClassNotFoundException, IOException {
        if (propertyPrefix == null) {
            propertyPrefix = "";
        }

        Class clazz = (Class)classMap.get(propertyPrefix + key);
        if (clazz == null) {
            clazz = newInstance(doFindFactoryProperties(key), propertyPrefix);
            if (clazz != null) {
                classMap.put(propertyPrefix + key, clazz);
            }
        }
        return clazz;
    }

    public List<Class> findClasses(String key) throws ClassNotFoundException, IOException {
        return findClasses(key, null);
    }

    public List<Class> findClasses(String key, String propertyPrefix) throws ClassNotFoundException,
        IOException {
        // TODO change to support finding multiple classes on the classpath!
        Class type = findClass(key, propertyPrefix);
        return Collections.singletonList(type);
    }

    public String getPath() {
        return path;
    }

    private Class newInstance(Properties properties, String propertyPrefix) throws ClassNotFoundException,
        IOException {

        String className = properties.getProperty(propertyPrefix + "class");
        if (className == null) {
            throw new IOException("Expected property is missing: " + propertyPrefix + "class");
        }

        return ObjectHelper.loadClass(className);
    }

    private Properties doFindFactoryProperties(String key) throws IOException {
        String uri = path + key;

        InputStream in = ObjectHelper.loadResourceAsStream(uri);
        if (in == null) {
            throw new NoFactoryAvailableException(uri);
        }

        // lets load the file
        BufferedInputStream reader = null;
        try {
            reader = new BufferedInputStream(in);
            Properties properties = new Properties();
            properties.load(reader);
            return properties;
        } finally {
            ObjectHelper.close(reader, key, null);
            ObjectHelper.close(in, key, null);
        }
    }
    
}
