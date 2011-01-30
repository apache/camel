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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.Bundle;

/**
 * A ClassLoader delegating to a given OSGi bundle.
 *
 * @version $Rev: 896324 $, $Date: 2010-01-06 07:05:04 +0100 (Wed, 06 Jan 2010) $
 */
public class BundleDelegatingClassLoader extends ClassLoader {
    private static final transient Log LOG = LogFactory.getLog(BundleDelegatingClassLoader.class);
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
        if (LOG.isTraceEnabled()) {
            LOG.trace("FindClass: " + name);
        }
        return bundle.loadClass(name);
    }

    protected URL findResource(String name) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("FindResource: " + name);
        }
        URL resource = bundle.getResource(name);
        if (classLoader != null && resource == null) {
            resource = classLoader.getResource(name);
        }
        return resource;
    }

    @SuppressWarnings("unchecked")
    protected Enumeration findResources(String name) throws IOException {
        if (LOG.isTraceEnabled()) {
            LOG.trace("FindResource: " + name);
        }
        return bundle.getResources(name);
    }

    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (LOG.isTraceEnabled()) {
            LOG.trace("LoadClass: " + name + ", resolve: " + resolve);
        }
        Class clazz;
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
}
