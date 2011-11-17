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
package org.apache.camel.script.osgi;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;

import org.apache.camel.impl.osgi.tracker.BundleTracker;
import org.apache.camel.impl.osgi.tracker.BundleTrackerCustomizer;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Activator implements BundleActivator, BundleTrackerCustomizer {
    public static final String META_INF_SERVICES_DIR = "META-INF/services";
    public static final String SCRIPT_ENGINE_SERVICE_FILE = "javax.script.ScriptEngineFactory";

    private static final transient Logger LOG = LoggerFactory.getLogger(Activator.class);
    private static BundleContext context;
    private BundleTracker tracker;
    
    private Map<Long, List<BundleScriptEngineResolver>> resolvers 
        = new ConcurrentHashMap<Long, List<BundleScriptEngineResolver>>();

    public static BundleContext getBundleContext() {
        return context;
    }
    
    public void start(BundleContext context) throws Exception {
        Activator.context = context;
        LOG.info("Camel-Script activator starting");
        tracker = new BundleTracker(context, Bundle.ACTIVE, this);
        tracker.open();
        LOG.info("Camel-Script activator started");
    }

    public void stop(BundleContext context) throws Exception {
        LOG.info("Camel-Script activator stopping");
        tracker.close();
        LOG.info("Camel-Script activator stopped");
        Activator.context = null;
    }

    public Object addingBundle(Bundle bundle, BundleEvent event) {
        List<BundleScriptEngineResolver> r = new ArrayList<BundleScriptEngineResolver>();
        registerScriptEngines(bundle, r);
        for (BundleScriptEngineResolver service : r) {
            service.register();
        }
        resolvers.put(bundle.getBundleId(), r);
        return bundle;
    }

    public void modifiedBundle(Bundle bundle, BundleEvent event, Object object) {
    }

    public void removedBundle(Bundle bundle, BundleEvent event, Object object) {
        LOG.debug("Bundle stopped: {}", bundle.getSymbolicName());
        List<BundleScriptEngineResolver> r = resolvers.remove(bundle.getBundleId());
        if (r != null) {
            for (BundleScriptEngineResolver service : r) {
                service.unregister();
            }
        }
    }

    public static ScriptEngine resolveScriptEngine(String scriptEngineName) throws InvalidSyntaxException {
        ServiceReference[] refs = context.getServiceReferences(ScriptEngineResolver.class.getName(), null);
        if (refs == null) {
            LOG.info("No OSGi script engine resolvers available!");
            return null;
        }
        
        LOG.debug("Found " + refs.length + " OSGi ScriptEngineResolver services");
        
        for (ServiceReference ref : refs) {
            ScriptEngineResolver resolver = (ScriptEngineResolver) context.getService(ref);
            ScriptEngine engine = resolver.resolveScriptEngine(scriptEngineName);
            context.ungetService(ref);
            LOG.debug("OSGi resolver " + resolver + " produced " + scriptEngineName + " engine " + engine);
            if (engine != null) {
                return engine;
            }
        }
        return null;
    }


    protected void registerScriptEngines(Bundle bundle, List<BundleScriptEngineResolver> resolvers) {
        URL configURL = null;
        for (Enumeration e = bundle.findEntries(META_INF_SERVICES_DIR, SCRIPT_ENGINE_SERVICE_FILE, false); e != null && e.hasMoreElements();) {
            configURL = (URL) e.nextElement();
        }
        if (configURL != null) {
            LOG.info("Found ScriptEngineFactory in " + bundle.getSymbolicName());
            resolvers.add(new BundleScriptEngineResolver(bundle, configURL));
        }
    } 
    public static interface ScriptEngineResolver {
        ScriptEngine resolveScriptEngine(String name);
    }
    protected static class BundleScriptEngineResolver implements ScriptEngineResolver {
        protected final Bundle bundle;
        private ServiceRegistration reg;
        private final URL configFile;

        public BundleScriptEngineResolver(Bundle bundle, URL configFile) {
            this.bundle = bundle;
            this.configFile = configFile;
        }
        public void register() {
            reg = bundle.getBundleContext().registerService(ScriptEngineResolver.class.getName(), 
                                                            this, null);
        }
        public void unregister() {
            reg.unregister();
        }
        public ScriptEngine resolveScriptEngine(String name) {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(configFile.openStream()));
                String className = in.readLine();
                in.close();
                Class cls = bundle.loadClass(className);
                if (!ScriptEngineFactory.class.isAssignableFrom(cls)) {
                    throw new IllegalStateException("Invalid ScriptEngineFactory: " + cls.getName());
                }
                ScriptEngineFactory factory = (ScriptEngineFactory) cls.newInstance();
                List<String> names = factory.getNames();
                for (String test : names) {
                    if (test.equals(name)) {
                        ClassLoader old = Thread.currentThread().getContextClassLoader();
                        ScriptEngine engine;
                        try {
                            // JRuby seems to require the correct TCCL to call getScriptEngine
                            Thread.currentThread().setContextClassLoader(factory.getClass().getClassLoader());
                            engine = factory.getScriptEngine();
                        } finally {
                            Thread.currentThread().setContextClassLoader(old);
                        }
                        LOG.trace("Resolved ScriptEngineFactory: {} for expected name: {}", engine, name);
                        return engine;
                    }
                }
                LOG.debug("ScriptEngineFactory: {} does not match expected name: {}", factory.getEngineName(), name);
                return null;
            } catch (Exception e) {
                LOG.warn("Cannot create ScriptEngineFactory: " + e.getClass().getName(), e);
                return null;
            }
        }

        @Override
        public String toString() {
            return "OSGi script engine resolver for " + bundle.getSymbolicName();
        }
    }


}
