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

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.util.NoFactoryAvailableException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.springframework.osgi.context.support.BundleDelegatingClassLoader;

import java.io.BufferedInputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Created by IntelliJ IDEA.
 * User: gnodet
 * Date: Sep 20, 2007
 * Time: 10:37:31 AM
 * To change this template use File | Settings | File Templates.
 */
public class OsgiComponentResolver implements ComponentResolver {

    private static final transient Log LOG = LogFactory.getLog(OsgiComponentResolver.class);

    private BundleContext bundleContext;
    private Map<String, ComponentEntry> components;

    private class BundleListener implements SynchronousBundleListener {
        public void bundleChanged(BundleEvent event) {
            try {
                Bundle bundle = event.getBundle();
                if (event.getType() == BundleEvent.RESOLVED) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Bundle resolved: " + bundle.getSymbolicName());
                    }
                    mayBeAddComponentFor(bundle);
                } else if (event.getType() == BundleEvent.UNRESOLVED) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Bundle unresolved: " + bundle.getSymbolicName());
                    }
                    mayBeRemoveComponentFor(bundle);
                }
            } catch (Throwable e) {
                LOG.fatal("Exception handing bundle changed event", e);
            }
        }
    }

    private class ComponentEntry {
        Bundle bundle;
        String path;
        String name;
        Class  type;
    }

    public OsgiComponentResolver(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    protected void init() {
        if (components != null) {
            return;
        }
        LOG.debug("Initializing OsgiComponentResolver");
        components = new HashMap<String, ComponentEntry>();
        bundleContext.addBundleListener(new BundleListener());
        Bundle[] previousBundles = bundleContext.getBundles();
        for (int i = 0; i < previousBundles.length; i++) {
            int state = previousBundles[i].getState();
            if (state == Bundle.RESOLVED || state == Bundle.ACTIVE) {
                mayBeAddComponentFor(previousBundles[i]);
            }
        }
    }

    protected synchronized void mayBeAddComponentFor(Bundle bundle) {
        Enumeration e = bundle.getEntryPaths("/META-INF/services/org/apache/camel/component/");
        if (e != null) {
            while (e.hasMoreElements()) {
                String path = (String) e.nextElement();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found entry: " + path + " in bundle " + bundle.getSymbolicName());
                }
                ComponentEntry entry = new ComponentEntry();
                entry.bundle = bundle;
                entry.path = path;
                entry.name = path.substring(path.lastIndexOf("/") + 1);
                components.put(entry.name, entry);
            }
        }
    }

    protected synchronized void mayBeRemoveComponentFor(Bundle bundle) {
        for (ComponentEntry entry : components.values()) {
            if (entry.bundle == bundle) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Removing entry: " + entry.path + " in bundle " + bundle.getSymbolicName());
                }
                components.remove(entry.name);
            }
        }
    }

    protected synchronized Class getComponent(String name) throws Exception {
        ComponentEntry entry = components.get(name);
        if (entry == null) {
            return null;
        }
        if (entry.type == null) {
            URL url = entry.bundle.getEntry(entry.path);
            // lets load the file
            Properties properties = new Properties();
            BufferedInputStream reader = null;
            try {
                reader = new BufferedInputStream(url.openStream());
                properties.load(reader);
            } finally {
                try {
                    reader.close();
                } catch (Exception ignore) {
                }
            }
            String classname = (String) properties.get("class");
            ClassLoader loader = BundleDelegatingClassLoader.createBundleClassLoaderFor(entry.bundle);
            entry.type = loader.loadClass(classname);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Found component: " + name + " via type: " + entry.type.getName());
        }
        return entry.type;
    }

    public Component resolveComponent(String name, CamelContext context) throws Exception {
        Object bean = null;
        try {
            bean = context.getRegistry().lookup(name);
            if (bean != null && LOG.isDebugEnabled()) {
                LOG.debug("Found component: " + name + " in registry: " + bean);
            }
        }
        catch (Exception e) {
            LOG.debug("Ignored error looking up bean: " + name + ". Error: " + e);
        }
        if (bean != null) {
            if (bean instanceof Component) {
                return (Component) bean;
            }
            else {
                throw new IllegalArgumentException("Bean with name: " + name + " in registry is not a Component: " + bean);
            }
        }
        // Check in OSGi bundles
        init();
        Class type = null;
        try {
            type = getComponent(name);
        }
        catch (Throwable e) {
            throw new IllegalArgumentException("Invalid URI, no Component registered for scheme : " + name, e);
        }
        if (type == null) {
            return null;
        }
        if (Component.class.isAssignableFrom(type)) {
            return (Component) context.getInjector().newInstance(type);
        } else {
            throw new IllegalArgumentException("Type is not a Component implementation. Found: " + type.getName());
        }
    }

}
