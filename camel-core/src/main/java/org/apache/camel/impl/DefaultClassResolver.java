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

import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

import org.apache.camel.spi.ClassResolver;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;

/**
 * Default class resolver that uses regular class loader to load classes.
 */
public class DefaultClassResolver implements ClassResolver {

    public Class<?> resolveClass(String name) {
        return loadClass(name, DefaultClassResolver.class.getClassLoader());
    }

    public <T> Class<T> resolveClass(String name, Class<T> type) {
        Class<T> answer = CastUtils.cast(loadClass(name, DefaultClassResolver.class.getClassLoader()));
        return answer;
    }

    public Class<?> resolveClass(String name, ClassLoader loader) {
        return loadClass(name, loader);
    }

    public <T> Class<T> resolveClass(String name, Class<T> type, ClassLoader loader) {
        Class<T> answer = CastUtils.cast(loadClass(name, loader));
        return answer;
    }

    public Class<?> resolveMandatoryClass(String name) throws ClassNotFoundException {
        Class<?> answer = resolveClass(name);
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

    public Class<?> resolveMandatoryClass(String name, ClassLoader loader) throws ClassNotFoundException {
        Class<?> answer = resolveClass(name, loader);
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

    public InputStream loadResourceAsStream(String uri) {
        ObjectHelper.notEmpty(uri, "uri");
        return ObjectHelper.loadResourceAsStream(uri);
    }

    public URL loadResourceAsURL(String uri) {
        ObjectHelper.notEmpty(uri, "uri");
        return ObjectHelper.loadResourceAsURL(uri);
    }

    public Enumeration<URL> loadResourcesAsURL(String uri) {
        return loadAllResourcesAsURL(uri);
    }

    @Override
    public Enumeration<URL> loadAllResourcesAsURL(String uri) {
        ObjectHelper.notEmpty(uri, "uri");
        return ObjectHelper.loadResourcesAsURL(uri);
    }

    protected Class<?> loadClass(String name, ClassLoader loader) {
        ObjectHelper.notEmpty(name, "name");
        return ObjectHelper.loadClass(name, loader);
    }
}
