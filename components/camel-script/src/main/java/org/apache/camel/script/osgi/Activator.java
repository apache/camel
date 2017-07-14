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
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;

import org.apache.camel.impl.osgi.tracker.BundleTracker;
import org.apache.camel.impl.osgi.tracker.BundleTrackerCustomizer;
import org.apache.camel.spi.LanguageResolver;
import org.apache.camel.util.IOHelper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.wiring.BundleWiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Activator implements BundleActivator, BundleTrackerCustomizer, ServiceListener {
    public static final String META_INF_SERVICES_DIR = "META-INF/services";
    public static final String SCRIPT_ENGINE_SERVICE_FILE = "javax.script.ScriptEngineFactory";

    private static final Logger LOG = LoggerFactory.getLogger(Activator.class);
    private static BundleContext context;
    private BundleTracker tracker;
    private ServiceRegistration<LanguageResolver> registration;

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
        context.addServiceListener(this, "(&(resolver=default)(objectClass=org.apache.camel.spi.LanguageResolver))");
        LOG.info("Camel-Script activator started");
    }

    public void stop(BundleContext context) throws Exception {
        LOG.info("Camel-Script activator stopping");
        tracker.close();
        context.removeServiceListener(this);
        if (registration != null) {
            registration.unregister();
        }
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
        // Only update the script language engine when the resolver is changed
        if (r.size() > 0) {
            updateAvailableScriptLanguages();
        }
        return bundle;
    }

    public void modifiedBundle(Bundle bundle, BundleEvent event, Object object) {
    }

    public void removedBundle(Bundle bundle, BundleEvent event, Object object) {
        LOG.debug("Bundle stopped: {}", bundle.getSymbolicName());
        List<BundleScriptEngineResolver> r = resolvers.remove(bundle.getBundleId());
        if (r != null) {
            updateAvailableScriptLanguages();
            for (BundleScriptEngineResolver service : r) {
                service.unregister();
            }
        }
    }

    private String[] getAvailableScriptNames() {
        // use a set to avoid duplicate names
        Set<String> names = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        for (List<BundleScriptEngineResolver> list : resolvers.values()) {
            for (BundleScriptEngineResolver r : list) {
                names.addAll(r.getScriptNames());
            }
        }
        return names.toArray(new String[]{});
    }

    private void updateAvailableScriptLanguages() {
        ServiceReference<LanguageResolver> ref = null;
        try {
            Collection<ServiceReference<LanguageResolver>> references = context.getServiceReferences(LanguageResolver.class, "(resolver=default)");
            if (references.size() == 1) {
                // Unregistry the old language resolver first
                if (registration != null) {
                    registration.unregister();
                    registration = null;
                }
                ref = references.iterator().next();
                LanguageResolver resolver = context.getService(ref);
                Dictionary props = new Hashtable();
                // Just publish the language resolve with the language we found
                props.put("language", getAvailableScriptNames());
                registration = context.registerService(LanguageResolver.class, resolver, props);
            }
        } catch (InvalidSyntaxException e) {
            LOG.error("Invalid syntax for LanguageResolver service reference filter.");
        } finally {
            if (ref != null) {
                context.ungetService(ref);
            }
        }
    }

    public static ScriptEngine resolveScriptEngine(String scriptEngineName) throws InvalidSyntaxException {
        ServiceReference<?>[] refs = context.getServiceReferences(ScriptEngineResolver.class.getName(), null);
        if (refs == null) {
            LOG.info("No OSGi script engine resolvers available!");
            return null;
        }
        
        LOG.debug("Found " + refs.length + " OSGi ScriptEngineResolver services");
        
        for (ServiceReference<?> ref : refs) {
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
        try {
            for (Enumeration<?> e = bundle.adapt(BundleWiring.class).getClassLoader().getResources(META_INF_SERVICES_DIR + "/" + SCRIPT_ENGINE_SERVICE_FILE); e != null && e.hasMoreElements();) {
                URL configURL = (URL) e.nextElement();
                LOG.info("Found ScriptEngineFactory in bundle: {}", bundle.getSymbolicName());
                resolvers.add(new BundleScriptEngineResolver(bundle, configURL));
            }
        } catch (IOException e) {
            LOG.info("Error loading script engine factory", e);
        }
    }

    @Override
    public void serviceChanged(ServiceEvent event) {
        updateAvailableScriptLanguages();
    }

    public interface ScriptEngineResolver {
        ScriptEngine resolveScriptEngine(String name);
    }

    protected static class BundleScriptEngineResolver implements ScriptEngineResolver {
        protected final Bundle bundle;
        private ServiceRegistration<?> reg;
        private final URL configFile;

        public BundleScriptEngineResolver(Bundle bundle, URL configFile) {
            this.bundle = bundle;
            this.configFile = configFile;
        }

        public void register() {
            reg = bundle.getBundleContext().registerService(ScriptEngineResolver.class.getName(), this, null);
        }

        public void unregister() {
            reg.unregister();
        }

        private List<String> getScriptNames() {
            return getScriptNames(getFactory());
        }

        @SuppressWarnings("unchecked")
        private List<String> getScriptNames(ScriptEngineFactory factory) {
            List<String> names;
            if (factory != null) {
                names = factory.getNames();
            } else {
                // return an empty script name list
                names = Collections.EMPTY_LIST;
            }
            return names;
        }

        private ScriptEngineFactory getFactory() {
            try {
                BufferedReader in = IOHelper.buffered(new InputStreamReader(configFile.openStream()));
                String className;
                while ((className = in.readLine()) != null) {
                    if ("".equals(className.trim()) || className.trim().startsWith("#")) {
                        continue;
                    } else if (className.contains("#")) {
                        className = className.substring(0, className.indexOf('#')).trim();
                        break;
                    } else {
                        className = className.trim();
                        break;
                    }
                }
                in.close();
                Class<?> cls = bundle.loadClass(className);
                // OSGi classloading trouble (with jruby)
                if (!ScriptEngineFactory.class.isAssignableFrom(cls)) {
                    return null;
                }
                return (ScriptEngineFactory) cls.newInstance();
            } catch (Exception e) {
                LOG.warn("Cannot create the ScriptEngineFactory: " + e.getClass().getName(), e);
                return null;
            }
        }

        public ScriptEngine resolveScriptEngine(String name) {
            try {
                ScriptEngineFactory factory = getFactory();
                if (factory != null) {
                    List<String> names = getScriptNames(factory);
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
                }
            } catch (Exception e) {
                LOG.warn("Cannot create ScriptEngineFactory: " + e.getClass().getName(), e);
                return null;
            }

            return null;
        }

        @Override
        public String toString() {
            return "OSGi script engine resolver for " + bundle.getSymbolicName();
        }
    }

}
