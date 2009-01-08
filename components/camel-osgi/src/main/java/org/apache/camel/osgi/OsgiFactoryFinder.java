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
package org.apache.camel.osgi;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import org.apache.camel.util.FactoryFinder;
import org.apache.camel.util.NoFactoryAvailableException;
import org.apache.camel.util.ObjectHelper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

public class OsgiFactoryFinder extends FactoryFinder {
    
    private class BundleEntry {
        URL url;
        Bundle bundle;
    }
    
    public OsgiFactoryFinder() {
        super();
    }
    
    public OsgiFactoryFinder(String path) {
        super(path);
    }
    
    public Class findClass(String key, String propertyPrefix) throws ClassNotFoundException, IOException {
        if (propertyPrefix == null) {
            propertyPrefix = "";
        }

        Class clazz = (Class)classMap.get(propertyPrefix + key);
        Properties properties = null;
        if (clazz == null) {
            BundleEntry entry = getResource(key);
            if (entry != null) {
                URL url = entry.url;
                InputStream in = url.openStream();
                // lets load the file
                BufferedInputStream reader = null;
                try {
                    reader = new BufferedInputStream(in);
                    properties = new Properties();
                    properties.load(reader);
                    String className = properties.getProperty(propertyPrefix + "class");
                    if (className == null) {
                        throw new IOException("Expected property is missing: " + propertyPrefix + "class");
                    }
                    clazz = entry.bundle.loadClass(className);
                    classMap.put(propertyPrefix + key, clazz);
                } finally {
                    ObjectHelper.close(reader, key, null);
                    ObjectHelper.close(in, key, null);
                }
            } else {
                throw new NoFactoryAvailableException(propertyPrefix + key);
            }           
        }
        return clazz;
    }
    
       
    public BundleEntry getResource(String name) {
        URL url = null;
        BundleEntry entry = null;
        BundleContext bundleContext =  Activator.getBundle().getBundleContext();
        for (Bundle bundle : bundleContext.getBundles()) {            
            url = bundle.getEntry(getPath() + name);
            if (url != null) {
                entry = new BundleEntry();
                entry.url = url;
                entry.bundle = bundle;
                break;
            }
        }
        return entry;
    }

}
