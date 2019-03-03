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
package org.apache.camel.core.osgi;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.impl.DefaultFactoryFinder;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.util.IOHelper;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

public class OsgiFactoryFinder extends DefaultFactoryFinder {
    private BundleContext bundleContext;

    public OsgiFactoryFinder(BundleContext bundleContext, ClassResolver classResolver, String resourcePath) {
        super(classResolver, resourcePath);
        this.bundleContext = bundleContext;
    }

    private static class BundleEntry {
        URL url;
        Bundle bundle;
    }

    @Override
    public Class<?> findClass(String key, String propertyPrefix, Class<?> checkClass) throws ClassNotFoundException, IOException {
        final String prefix = propertyPrefix != null ? propertyPrefix : "";
        final String classKey = propertyPrefix + key;

        return addToClassMap(classKey, () -> {
            BundleEntry entry = getResource(key, checkClass);
            if (entry != null) {
                URL url = entry.url;
                InputStream in = url.openStream();
                // lets load the file
                BufferedInputStream reader = null;
                try {
                    reader = IOHelper.buffered(in);
                    Properties properties = new Properties();
                    properties.load(reader);
                    String className = properties.getProperty(prefix + "class");
                    if (className == null) {
                        throw new IOException("Expected property is missing: " + prefix + "class");
                    }
                    return entry.bundle.loadClass(className);
                } finally {
                    IOHelper.close(reader, key, null);
                    IOHelper.close(in, key, null);
                }
            } else {
                throw new NoFactoryAvailableException(classKey);
            }
        });
    }

    @Override
    public Class<?> findClass(String key, String propertyPrefix) throws ClassNotFoundException, IOException {
        return findClass(key, propertyPrefix, null);
    }

    // As the META-INF of the Factory could not be export,
    // we need to go through the bundles to look for it
    // NOTE, the first found factory will be return
    public BundleEntry getResource(String name) {
        return getResource(name, null);
    }

    // The clazz can make sure we get right version of class that we need
    public BundleEntry getResource(String name, Class<?> clazz) {
        BundleEntry entry = null;
        Bundle[] bundles = null; 
        
        bundles = bundleContext.getBundles();
        
        URL url;
        for (Bundle bundle : bundles) {
            url = bundle.getEntry(getResourcePath() + name);
            if (url != null && checkCompatibility(bundle, clazz)) {
                entry = new BundleEntry();
                entry.url = url;
                entry.bundle = bundle;
                break;
            }
        }

        return entry;
    }

    private boolean checkCompatibility(Bundle bundle, Class<?> clazz) {
        if (clazz == null) {
            return true;
        }
        // Check bundle compatibility
        try {
            if (bundle.loadClass(clazz.getName()) != clazz) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }
        return true;
    }

}
