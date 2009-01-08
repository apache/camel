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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.springframework.osgi.util.BundleDelegatingClassLoader;

public class Activator implements BundleActivator, SynchronousBundleListener {
    public static final String META_INF_TYPE_CONVERTER = "META-INF/services/org/apache/camel/TypeConverter";
    public static final String META_INF_COMPONENT = "META-INF/services/org/apache/camel/component/";
    public static final String META_INF_LANGUAGE = "META-INF/services/org/apache/camel/language/";
    private static final transient Log LOG = LogFactory.getLog(Activator.class);    
    private static final Map<String, ComponentEntry> COMPONENTS = new HashMap<String, ComponentEntry>();
    private static final Map<URL, TypeConverterEntry> TYPE_CONVERTERS = new HashMap<URL, TypeConverterEntry>();
    private static final Map<String, ComponentEntry> LANGUAGES = new HashMap<String, ComponentEntry>();
    private static Bundle bundle;
    
    private class ComponentEntry {
        Bundle bundle;
        String path;
        String name;
        Class type;
    }
    
    private class TypeConverterEntry {
        Bundle bundle;
        URL resource;
        Set<String> converterPackages;
    }
    
    public void bundleChanged(BundleEvent event) {
        try {
            Bundle bundle = event.getBundle();
            if (event.getType() == BundleEvent.RESOLVED) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Bundle resolved: " + bundle.getSymbolicName());
                }
                mayBeAddComponentAndLanguageFor(bundle);                
                mayBeAddTypeConverterFor(bundle);
            } else if (event.getType() == BundleEvent.UNRESOLVED) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Bundle unresolved: " + bundle.getSymbolicName());
                }
                mayBeRemoveComponentAndLanguageFor(bundle);                
                mayBeRemoveTypeConverterFor(bundle);
            }
        } catch (Throwable e) {
            LOG.fatal("Exception handing bundle changed event", e);
        }
        
    }

    protected synchronized void addComponentEntry(String entryPath, Bundle bundle, Map<String, ComponentEntry> entries) {
        Enumeration e = bundle.getEntryPaths(entryPath);
        if (e != null) {
            while (e.hasMoreElements()) {
                String path = (String)e.nextElement();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found entry: " + path + " in bundle " + bundle.getSymbolicName());
                }
                ComponentEntry entry = new ComponentEntry();
                entry.bundle = bundle;
                entry.path = path;
                entry.name = path.substring(path.lastIndexOf("/") + 1);
                entries.put(entry.name, entry);
            }
        }
        
    }

    protected void mayBeAddComponentAndLanguageFor(Bundle bundle) {        
        addComponentEntry(META_INF_COMPONENT, bundle, COMPONENTS);
        addComponentEntry(META_INF_LANGUAGE, bundle, LANGUAGES);
    }
    
    protected synchronized void mayBeAddTypeConverterFor(Bundle bundle) {
        try {
            Enumeration e = bundle.getResources(META_INF_TYPE_CONVERTER);
            if (e != null) {
                while (e.hasMoreElements()) {
                    URL resource = (URL)e.nextElement();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Found entry: " + resource + " in bundle " + bundle.getSymbolicName());
                    }
                    TypeConverterEntry entry = new TypeConverterEntry();
                    entry.bundle = bundle;                   
                    entry.resource = resource;
                    entry.converterPackages = getConverterPackages(resource);
                    TYPE_CONVERTERS.put(resource, entry);
                }
            }
        } catch (IOException ignore) {
            // can't find the resource
        }
    }

    protected void mayBeRemoveComponentAndLanguageFor(Bundle bundle) {
        removeComponentEntry(bundle, COMPONENTS);
        removeComponentEntry(bundle, LANGUAGES);        
    }
    
    protected void removeComponentEntry(Bundle bundle, Map<String, ComponentEntry> entries) {
        ComponentEntry[] entriesArray = entries.values().toArray(new ComponentEntry[0]);
        for (ComponentEntry entry : entriesArray) {        
            if (entry.bundle == bundle) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Removing entry: " + entry.path + " in bundle " + bundle.getSymbolicName());
                }
                entries.remove(entry.name);
            }
        }        
    }
    
    protected synchronized void mayBeRemoveTypeConverterFor(Bundle bundle) {
        TypeConverterEntry[] entriesArray = TYPE_CONVERTERS.values().toArray(new TypeConverterEntry[0]);
        for (TypeConverterEntry entry : entriesArray) {
            if (entry.bundle == bundle) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Removing entry: " + entry.resource + " in bundle " + bundle.getSymbolicName());
                }
                COMPONENTS.remove(entry.resource);
            }
        }
    }

    public void start(BundleContext context) throws Exception {
        bundle = context.getBundle();       
        context.addBundleListener(this);
        if (LOG.isDebugEnabled()) {
            LOG.debug("checking existing bundles");
        }
        for (Bundle bundle : context.getBundles()) {
            if (bundle.getState() == Bundle.RESOLVED || bundle.getState() == Bundle.STARTING
                || bundle.getState() == Bundle.ACTIVE || bundle.getState() == Bundle.STOPPING) {
                mayBeAddComponentAndLanguageFor(bundle);
                mayBeAddTypeConverterFor(bundle);
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("actived");
        }
        
    }    

    public void stop(BundleContext context) throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("removing the components of existing bundles");
        }
        for (Bundle bundle : context.getBundles()) {
            if (bundle.getState() == Bundle.RESOLVED || bundle.getState() == Bundle.STARTING 
                || bundle.getState() == Bundle.ACTIVE || bundle.getState() == Bundle.STOPPING) {
                mayBeRemoveComponentAndLanguageFor(bundle);
                mayBeRemoveTypeConverterFor(bundle);
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("stopped");
        }
    }
    
    protected Set<String> getConverterPackages(URL resource) {
        Set<String> packages = new HashSet<String>();
        if (resource != null) {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new InputStreamReader(resource.openStream()));
                while (true) {
                    String line = reader.readLine();
                    if (line == null) {
                        break;
                    }
                    line = line.trim();
                    if (line.startsWith("#") || line.length() == 0) {
                        continue;
                    }
                    tokenize(packages, line);
                }
            } catch (Exception ignore) {
                // Do nothing here
            } finally {
                if (reader != null) {
                    ObjectHelper.close(reader, null, LOG);
                }
            }
        }
        return packages;
    }
    
    protected void tokenize(Set<String> packages, String line) {
        StringTokenizer iter = new StringTokenizer(line, ",");
        while (iter.hasMoreTokens()) {
            String name = iter.nextToken().trim();
            if (name.length() > 0) {
                packages.add(name);
            }
        }
    }
    
    protected static Bundle getBundle() {
        return bundle;
    }
    
    protected static synchronized String[] findTypeConverterPackageNames() {
        Set<String> packages = new HashSet<String>();
        for (TypeConverterEntry entry : TYPE_CONVERTERS.values()) {
            for (String packageName : entry.converterPackages) {
                packages.add(packageName);
            }
        }
        return packages.toArray(new String[packages.size()]);
    }
        
    public static synchronized Class getComponent(String name) throws Exception {
        return getClassFromEntries(name, COMPONENTS);
    }
    
    public static synchronized Class getLanguage(String name) throws Exception {
        return getClassFromEntries(name, LANGUAGES);
    }
    
    protected static synchronized Class getClassFromEntries(String name, Map<String, ComponentEntry> entries) throws Exception {
        ComponentEntry entry = entries.get(name);
        if (entry == null) {
            return null;
        }
        if (entry.type == null) {
            URL url = entry.bundle.getEntry(entry.path);
            if (LOG.isDebugEnabled()) {
                LOG.debug("The entry " + name + "'s url is" + url);
            }
            // lets load the file
            Properties properties = new Properties();
            BufferedInputStream reader = null;
            try {
                reader = new BufferedInputStream(url.openStream());
                properties.load(reader);
            } finally {
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (Exception ignore) {
                }
            }
            String classname = (String)properties.get("class");
            ClassLoader loader = BundleDelegatingClassLoader.createBundleClassLoaderFor(entry.bundle);
            entry.type = loader.loadClass(classname);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Found entry: " + name + " via type: " + entry.type.getName());
        }
        return entry.type;
    }
    

}
