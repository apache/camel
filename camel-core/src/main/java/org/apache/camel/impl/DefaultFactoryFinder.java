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
package org.apache.camel.impl;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.Injector;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.IOHelper;

/**
 * Default factory finder.
 */
public class DefaultFactoryFinder implements FactoryFinder {

    private final ConcurrentMap<String, Class<?>> classMap = new ConcurrentHashMap<String, Class<?>>();
    private final ClassResolver classResolver;
    private final String path;

    public DefaultFactoryFinder(ClassResolver classResolver, String resourcePath) {
        this.classResolver = classResolver;
        this.path = resourcePath;
    }

    @Override
    public String getResourcePath() {
        return path;
    }

    @Override
    public Object newInstance(String key) throws NoFactoryAvailableException {
        try {
            return newInstance(key, null);
        } catch (Exception e) {
            throw new NoFactoryAvailableException(key, e);
        }
    }

    @Override
    public <T> List<T> newInstances(String key, Injector injector, Class<T> type) throws ClassNotFoundException, IOException {
        List<Class<T>> list = CastUtils.cast(findClasses(key));
        List<T> answer = new ArrayList<T>(list.size());
        answer.add(newInstance(key, injector, type));
        return answer;
    }

    @Override
    public Class<?> findClass(String key) throws ClassNotFoundException, IOException {
        return findClass(key, null);
    }

    @Override
    public Class<?> findClass(String key, String propertyPrefix) throws ClassNotFoundException, IOException {
        final String prefix = propertyPrefix != null ? propertyPrefix : "";
        final String classKey = prefix + key;

        return addToClassMap(classKey, () -> newInstance(doFindFactoryProperties(key), prefix));
    }

    @Override
    public Class<?> findClass(String key, String propertyPrefix, Class<?> clazz) throws ClassNotFoundException, IOException {
        // Just ignore clazz which is only useful for OSGiFactoryFinder
        return findClass(key, propertyPrefix);
    }

    private Object newInstance(String key, String propertyPrefix) throws IllegalAccessException,
        InstantiationException, IOException, ClassNotFoundException {
        Class<?> clazz = findClass(key, propertyPrefix);
        return clazz.newInstance();
    }

    private <T> T newInstance(String key, Injector injector, Class<T> expectedType) throws IOException,
        ClassNotFoundException {
        return newInstance(key, injector, null, expectedType);
    }

    private <T> T newInstance(String key, Injector injector, String propertyPrefix, Class<T> expectedType)
        throws IOException, ClassNotFoundException {
        Class<?> type = findClass(key, propertyPrefix);
        Object value = injector.newInstance(type);
        if (expectedType.isInstance(value)) {
            return expectedType.cast(value);
        } else {
            throw new ClassCastException("Not instanceof " + expectedType.getName() + " value: " + value);
        }
    }

    private List<Class<?>> findClasses(String key) throws ClassNotFoundException, IOException {
        return findClasses(key, null);
    }

    private List<Class<?>> findClasses(String key, String propertyPrefix) throws ClassNotFoundException, IOException {
        Class<?> type = findClass(key, propertyPrefix);
        return Collections.<Class<?>>singletonList(type);
    }

    private Class<?> newInstance(Properties properties, String propertyPrefix) throws ClassNotFoundException, IOException {
        String className = properties.getProperty(propertyPrefix + "class");
        if (className == null) {
            throw new IOException("Expected property is missing: " + propertyPrefix + "class");
        }

        Class<?> clazz = classResolver.resolveClass(className);
        if (clazz == null) {
            throw new ClassNotFoundException(className);
        }
        return clazz;
    }

    private Properties doFindFactoryProperties(String key) throws IOException {
        String uri = path + key;

        InputStream in = classResolver.loadResourceAsStream(uri);
        if (in == null) {
            throw new NoFactoryAvailableException(uri);
        }

        // lets load the file
        BufferedInputStream reader = null;
        try {
            reader = IOHelper.buffered(in);
            Properties properties = new Properties();
            properties.load(reader);
            return properties;
        } finally {
            IOHelper.close(reader, key, null);
            IOHelper.close(in, key, null);
        }
    }

    /*
     * This is a wrapper function to deal with exceptions in lambdas: the exception
     * is wrapped by a runtime exception (WrappedRuntimeException) which we catch
     * later on with the only purpose to re-throw the original exception.
     */
    protected Class<?> addToClassMap(String key, ClassSupplier mappingFunction) throws ClassNotFoundException, IOException {
        try {
            return classMap.computeIfAbsent(key, (String classKey) -> {
                try {
                    return mappingFunction.get();
                } catch (ClassNotFoundException e) {
                    throw new WrappedRuntimeException(e);
                } catch (NoFactoryAvailableException e) {
                    throw new WrappedRuntimeException(e);
                } catch (IOException e) {
                    throw new WrappedRuntimeException(e);
                }
            });
        } catch (WrappedRuntimeException e) {
            if (e.getCause() instanceof ClassNotFoundException) {
                throw (ClassNotFoundException)e.getCause();
            } else if (e.getCause() instanceof NoFactoryAvailableException) {
                throw (NoFactoryAvailableException)e.getCause();
            } else if (e.getCause() instanceof IOException) {
                throw (IOException)e.getCause();
            } else {
                throw new RuntimeException(e.getCause());
            }
        }
    }

    @FunctionalInterface
    protected interface ClassSupplier {
        Class<?> get() throws ClassNotFoundException, IOException;
    }

    private final class WrappedRuntimeException extends RuntimeException {
        WrappedRuntimeException(Exception e) {
            super(e);
        }
    }
}
