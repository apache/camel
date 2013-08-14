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
package org.apache.camel.core.osgi.utils;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A ClassLoader delegating to a given OSGi bundle.
 *
 */
public class BundleDelegatingClassLoader extends ClassLoader {
    private static final Logger LOG = LoggerFactory.getLogger(BundleDelegatingClassLoader.class);
    private final Bundle bundle;
    private final ClassLoader classLoader;

    public BundleDelegatingClassLoader(Bundle bundle) {
        this(bundle, null);
    }

    public BundleDelegatingClassLoader(Bundle bundle, ClassLoader classLoader) {
        this.bundle = bundle;
        this.classLoader = classLoader;
    }

    protected Class<?> findClass(String name) throws ClassNotFoundException {
        LOG.trace("FindClass: {}", name);
        return bundle.loadClass(name);
    }

    protected URL findResource(String name) {
        LOG.trace("FindResource: {}", name);
        URL resource = bundle.getResource(name);
        if (classLoader != null && resource == null) {
            resource = classLoader.getResource(name);
        }
        return resource;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Enumeration findResources(String name) throws IOException {
        LOG.trace("FindResource: {}", name);
        return bundle.getResources(name);
    }

    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        LOG.trace("LoadClass: {}, resolve: {}", name, resolve);
        Class<?> clazz;
        try {
            clazz = findClass(name);
        } catch (ClassNotFoundException cnfe) {
            if (classLoader != null) {
                try {
                    clazz = classLoader.loadClass(name);
                } catch (ClassNotFoundException e) {
                    throw new ClassNotFoundException(name + " from bundle " + bundle.getBundleId() + " (" + bundle.getSymbolicName() + ")", cnfe);
                }
            } else {
                throw new ClassNotFoundException(name + " from bundle " + bundle.getBundleId() + " (" + bundle.getSymbolicName() + ")", cnfe);
            }
        }
        if (resolve) {
            resolveClass(clazz);
        }
        return clazz;
    }

    public Bundle getBundle() {
        return bundle;
    }

    @Override
    public String toString() {
        return String.format("BundleDelegatingClassLoader(%s)", bundle);
    }
}
