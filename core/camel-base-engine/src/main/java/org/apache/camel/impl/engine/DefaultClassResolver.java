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
package org.apache.camel.impl.engine;

import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;

/**
 * Default class resolver that uses regular class loader to load classes.
 */
public class DefaultClassResolver implements ClassResolver, CamelContextAware {

    private Set<ClassLoader> classLoaders;
    private CamelContext camelContext;

    public DefaultClassResolver() {
    }

    public DefaultClassResolver(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void addClassLoader(ClassLoader classLoader) {
        if (classLoaders == null) {
            classLoaders = new LinkedHashSet<>();
        }
        classLoaders.add(classLoader);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<ClassLoader> getClassLoaders() {
        if (classLoaders == null) {
            return Collections.EMPTY_SET;
        }
        return Collections.unmodifiableSet(classLoaders);
    }

    @Override
    public ClassLoader getClassLoader(String name) {
        if (classLoaders != null) {
            for (ClassLoader cl : classLoaders) {
                if (name.equals(cl.getName())) {
                    return cl;
                }
            }
        }
        return null;
    }

    @Override
    public Class<?> resolveClass(String name) {
        Class<?> answer;
        if (classLoaders != null) {
            for (ClassLoader loader : classLoaders) {
                answer = loadClass(name, loader);
                if (answer != null) {
                    return answer;
                }
            }
        }

        answer = loadClass(name, DefaultClassResolver.class.getClassLoader());
        if (answer == null && getApplicationContextClassLoader() != null) {
            // fallback and use application context class loader
            answer = loadClass(name, getApplicationContextClassLoader());
        }
        return answer;
    }

    @Override
    public <T> Class<T> resolveClass(String name, Class<T> type) {
        return CastUtils.cast(resolveClass(name), type);
    }

    @Override
    public Class<?> resolveClass(String name, ClassLoader loader) {
        return loadClass(name, loader);
    }

    @Override
    public <T> Class<T> resolveClass(String name, Class<T> type, ClassLoader loader) {
        return CastUtils.cast(loadClass(name, loader));
    }

    @Override
    public Class<?> resolveMandatoryClass(String name) throws ClassNotFoundException {
        Class<?> answer = resolveClass(name);
        if (answer == null) {
            throw new ClassNotFoundException(name);
        }
        return answer;
    }

    @Override
    public <T> Class<T> resolveMandatoryClass(String name, Class<T> type) throws ClassNotFoundException {
        Class<T> answer = resolveClass(name, type);
        if (answer == null) {
            throw new ClassNotFoundException(name);
        }
        return answer;
    }

    @Override
    public Class<?> resolveMandatoryClass(String name, ClassLoader loader) throws ClassNotFoundException {
        Class<?> answer = resolveClass(name, loader);
        if (answer == null) {
            throw new ClassNotFoundException(name);
        }
        return answer;
    }

    @Override
    public <T> Class<T> resolveMandatoryClass(String name, Class<T> type, ClassLoader loader) throws ClassNotFoundException {
        Class<T> answer = resolveClass(name, type, loader);
        if (answer == null) {
            throw new ClassNotFoundException(name);
        }
        return answer;
    }

    @Override
    public InputStream loadResourceAsStream(String uri) {
        return ObjectHelper.loadResourceAsStream(uri, getApplicationContextClassLoader());
    }

    @Override
    public URL loadResourceAsURL(String uri) {
        return ObjectHelper.loadResourceAsURL(uri, getApplicationContextClassLoader());
    }

    @Override
    public Enumeration<URL> loadResourcesAsURL(String uri) {
        return loadAllResourcesAsURL(uri);
    }

    @Override
    public Enumeration<URL> loadAllResourcesAsURL(String uri) {
        return ObjectHelper.loadResourcesAsURL(uri);
    }

    protected Class<?> loadClass(String name, ClassLoader loader) {
        return ObjectHelper.loadClass(name, loader);
    }

    protected ClassLoader getApplicationContextClassLoader() {
        return camelContext != null ? camelContext.getApplicationContextClassLoader() : null;
    }

}
