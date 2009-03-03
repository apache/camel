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

import org.apache.camel.spi.ClassResolver;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Default class resolver that uses regular classloaders, and does NOT work in OSGi platforms.
 */
public class DefaultClassResolver implements ClassResolver {
    private static final transient Log LOG = LogFactory.getLog(DefaultClassResolver.class);

    public Class resolveClass(String name) {
        return loadClass(name, DefaultClassResolver.class.getClassLoader());
    }

    @SuppressWarnings("unchecked")
    public <T> Class<T> resolveClass(String name, Class<T> type) {
        Class answer = loadClass(name, DefaultClassResolver.class.getClassLoader());
        return (Class<T>) answer;
    }

    public Class resolveClass(String name, ClassLoader loader) {
        return loadClass(name, loader);
    }

    @SuppressWarnings("unchecked")
    public <T> Class<T> resolveClass(String name, Class<T> type, ClassLoader loader) {
        Class answer = loadClass(name, loader);
        return (Class<T>) answer;
    }

    public Class resolveMandatoryClass(String name) throws ClassNotFoundException {
        Class answer = resolveClass(name);
        if (answer == null) {
            throw new ClassNotFoundException(name);
        }
        return answer;
    }

    public <T> Class<T> resolveMandatoryClass(String name, Class<T> type) throws ClassNotFoundException {
        Class<T> answer = resolveClass(name, type);
        if (answer == null) {
            throw new ClassNotFoundException(name);
        }
        return answer;
    }

    public Class resolveMandatoryClass(String name, ClassLoader loader) throws ClassNotFoundException {
        Class answer = resolveClass(name, loader);
        if (answer == null) {
            throw new ClassNotFoundException(name);
        }
        return answer;
    }

    public <T> Class<T> resolveMandatoryClass(String name, Class<T> type, ClassLoader loader) throws ClassNotFoundException {
        Class<T> answer = resolveClass(name, type, loader);
        if (answer == null) {
            throw new ClassNotFoundException(name);
        }
        return answer;
    }

    protected Class loadClass(String name, ClassLoader loader) {
        // try context class loader first
        Class clazz = doLoadClass(name, Thread.currentThread().getContextClassLoader());
        if (clazz == null) {
            // then the provided loader
            clazz = doLoadClass(name, loader);
        }
        if (clazz == null) {
            // and fallback to the loader the loaded the ObjectHelper class
            clazz = doLoadClass(name, ObjectHelper.class.getClassLoader());
        }

        return clazz;
    }

    private static Class<?> doLoadClass(String name, ClassLoader loader) {
        ObjectHelper.notEmpty(name, "name");
        if (loader == null) {
            return null;
        }
        try {
            return loader.loadClass(name);
        } catch (ClassNotFoundException e) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Cannot load class: " + name + " using classloader: " + loader, e);
            }

        }
        return null;
    }


}
