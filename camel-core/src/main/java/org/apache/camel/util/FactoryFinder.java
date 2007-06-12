/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.util;

import org.apache.camel.spi.Injector;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class FactoryFinder {
    private final String path;
    private final ConcurrentHashMap classMap = new ConcurrentHashMap();

    public FactoryFinder() {
        this("META-INF/services/org/apache/camel/");
    }

    public FactoryFinder(String path) {
        this.path = path;
    }

    /**
     * Creates a new instance of the given key
     *
     * @param key is the key to add to the path to find a text file
     *            containing the factory name
     * @return a newly created instance
     */
    public Object newInstance(String key)
            throws IllegalAccessException, InstantiationException, IOException, ClassNotFoundException {
        return newInstance(key, (String) null);
    }

    public Object newInstance(String key, String propertyPrefix)
            throws IllegalAccessException, InstantiationException, IOException, ClassNotFoundException {
        Class clazz = findClass(key, propertyPrefix);
        return clazz.newInstance();
    }

    public Object newInstance(String key, Injector injector) throws IOException, ClassNotFoundException {
        return newInstance(key, injector, null);
    }

    public Object newInstance(String key, Injector injector, String propertyPrefix) throws IOException, ClassNotFoundException {
        Class type = findClass(key, propertyPrefix);
        return injector.newInstance(type);
    }

    public Class findClass(String key) throws ClassNotFoundException, IOException {
        return findClass(key, null);
    }

    public Class findClass(String key, String propertyPrefix) throws ClassNotFoundException, IOException {
        if (propertyPrefix == null) {
            propertyPrefix = "";
        }

        Class clazz = (Class) classMap.get(propertyPrefix + key);
        if (clazz == null) {
            clazz = newInstance(doFindFactoryProperies(key), propertyPrefix);
            classMap.put(propertyPrefix + key, clazz);
        }
        return clazz;
    }

    private Class newInstance(Properties properties, String propertyPrefix) throws ClassNotFoundException, IOException {

        String className = properties.getProperty(propertyPrefix + "class");
        if (className == null) {
            throw new IOException("Expected property is missing: " + propertyPrefix + "class");
        }
        Class clazz = null;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader != null) {
            try {
                clazz = loader.loadClass(className);
            }
            catch (ClassNotFoundException e) {
                // ignore
            }
        }
        if (clazz == null) {
            clazz = FactoryFinder.class.getClassLoader().loadClass(className);
        }
        return clazz;
    }

    private Properties doFindFactoryProperies(String key) throws IOException {
        String uri = path + key;

        // lets try the thread context class loader first
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = getClass().getClassLoader();
        }
        InputStream in = classLoader.getResourceAsStream(uri);
        if (in == null) {
            in = FactoryFinder.class.getClassLoader().getResourceAsStream(uri);
            if (in == null) {
                throw new NoFactoryAvailableException(uri);
            }
        }

        // lets load the file
        BufferedInputStream reader = null;
        try {
            reader = new BufferedInputStream(in);
            Properties properties = new Properties();
            properties.load(reader);
            return properties;
        }
        finally {
            try {
                reader.close();
            }
            catch (Exception e) {
            }
        }
    }
}
