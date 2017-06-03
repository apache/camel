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
package org.apache.camel.impl.osgi;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Converter;
import org.apache.camel.TypeConverter;
import org.apache.camel.TypeConverterLoaderException;
import org.apache.camel.impl.converter.AnnotationTypeConverterLoader;
import org.apache.camel.impl.osgi.tracker.BundleTracker;
import org.apache.camel.impl.osgi.tracker.BundleTrackerCustomizer;
import org.apache.camel.impl.scan.AnnotatedWithPackageScanFilter;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.LanguageResolver;
import org.apache.camel.spi.PackageScanFilter;
import org.apache.camel.spi.TypeConverterLoader;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.osgi.framework.wiring.BundleRevision.PACKAGE_NAMESPACE;

public class Activator implements BundleActivator, BundleTrackerCustomizer {

    public static final String META_INF_COMPONENT = "META-INF/services/org/apache/camel/component/";
    public static final String META_INF_LANGUAGE = "META-INF/services/org/apache/camel/language/";
    public static final String META_INF_LANGUAGE_RESOLVER = "META-INF/services/org/apache/camel/language/resolver/";
    public static final String META_INF_DATAFORMAT = "META-INF/services/org/apache/camel/dataformat/";
    public static final String META_INF_TYPE_CONVERTER = "META-INF/services/org/apache/camel/TypeConverter";
    public static final String META_INF_FALLBACK_TYPE_CONVERTER = "META-INF/services/org/apache/camel/FallbackTypeConverter";
    public static final String EXTENDER_NAMESPACE = "osgi.extender";
    public static final String CAMEL_EXTENDER = "org.apache.camel";

    private static final Logger LOG = LoggerFactory.getLogger(Activator.class);

    private BundleTracker tracker;
    private final Map<Long, List<BaseService>> resolvers = new ConcurrentHashMap<Long, List<BaseService>>();
    private long bundleId;
    
    // Map from package name to the capability we export for this package
    private final Map<String, BundleCapability> packageCapabilities = new HashMap<String, BundleCapability>();

    public void start(BundleContext context) throws Exception {
        LOG.info("Camel activator starting");
        cachePackageCapabilities(context);
        bundleId = context.getBundle().getBundleId();
        BundleContext systemBundleContext = context.getBundle(0).getBundleContext();
        tracker = new BundleTracker(systemBundleContext, Bundle.ACTIVE, this);
        tracker.open();
        LOG.info("Camel activator started");
    }

    public void stop(BundleContext context) throws Exception {
        LOG.info("Camel activator stopping");
        tracker.close();
        packageCapabilities.clear();
        LOG.info("Camel activator stopped");
    }
    
    /**
     * Caches the package capabilities that are needed for a set of interface classes
     */
    private void cachePackageCapabilities(BundleContext context) {
        BundleWiring ourWiring = context.getBundle().adapt(BundleWiring.class);
        List<BundleCapability> ourExports = ourWiring.getCapabilities(PACKAGE_NAMESPACE);
        for (BundleCapability ourExport : ourExports) {
            String ourPkgName = (String) ourExport.getAttributes().get(PACKAGE_NAMESPACE);
            packageCapabilities.put(ourPkgName, ourExport);
        }
    }

    public Object addingBundle(Bundle bundle, BundleEvent event) {
        LOG.debug("Bundle started: {}", bundle.getSymbolicName());
        if (extenderCapabilityWired(bundle)) {
            List<BaseService> r = new ArrayList<BaseService>();
            registerComponents(bundle, r);
            registerLanguages(bundle, r);
            registerDataFormats(bundle, r);
            registerTypeConverterLoader(bundle, r);
            for (BaseService service : r) {
                service.register();
            }
            resolvers.put(bundle.getBundleId(), r);
        }

        return bundle;
    }

    private boolean extenderCapabilityWired(Bundle bundle) {
        BundleWiring wiring = bundle.adapt(BundleWiring.class);
        if (wiring == null) {
            return true;
        }
        List<BundleWire> requiredWires = wiring.getRequiredWires(EXTENDER_NAMESPACE);
        for (BundleWire requiredWire : requiredWires) {
            if (CAMEL_EXTENDER.equals(requiredWire.getCapability().getAttributes().get(EXTENDER_NAMESPACE))) {
                if (this.bundleId == requiredWire.getProviderWiring().getBundle().getBundleId()) {
                    LOG.debug("Camel extender requirement of bundle {} correctly wired to this implementation", bundle.getBundleId());
                    return true;
                } else {
                    LOG.info("Not processing bundle {} as it requires a camel extender but is not wired to the this implementation", bundle.getBundleId());
                    return false;
                }
            }
        }
        return true;
    }

    public void modifiedBundle(Bundle bundle, BundleEvent event, Object object) {
    }

    public void removedBundle(Bundle bundle, BundleEvent event, Object object) {
        LOG.debug("Bundle stopped: {}", bundle.getSymbolicName());
        List<BaseService> r = resolvers.remove(bundle.getBundleId());
        if (r != null) {
            for (BaseService service : r) {
                service.unregister();
            }
        }
    }

    protected void registerComponents(Bundle bundle, List<BaseService> resolvers) {
        if (canSee(bundle, Component.class)) {
            Map<String, String> components = new HashMap<String, String>();
            for (Enumeration<?> e = bundle.getEntryPaths(META_INF_COMPONENT); e != null && e.hasMoreElements();) {
                String path = (String) e.nextElement();
                LOG.debug("Found entry: {} in bundle {}", path, bundle.getSymbolicName());
                String name = path.substring(path.lastIndexOf("/") + 1);
                components.put(name, path);
            }
            if (!components.isEmpty()) {
                resolvers.add(new BundleComponentResolver(bundle, components));
            }
        }
    }

    protected void registerLanguages(Bundle bundle, List<BaseService> resolvers) {
        if (canSee(bundle, Language.class)) {
            Map<String, String> languages = new HashMap<String, String>();
            for (Enumeration<?> e = bundle.getEntryPaths(META_INF_LANGUAGE); e != null && e.hasMoreElements();) {
                String path = (String) e.nextElement();
                LOG.debug("Found entry: {} in bundle {}", path, bundle.getSymbolicName());
                String name = path.substring(path.lastIndexOf("/") + 1);
                languages.put(name, path);
            }
            if (!languages.isEmpty()) {
                resolvers.add(new BundleLanguageResolver(bundle, languages));
            }
            for (Enumeration<?> e = bundle.getEntryPaths(META_INF_LANGUAGE_RESOLVER); e != null && e.hasMoreElements();) {
                String path = (String) e.nextElement();
                LOG.debug("Found entry: {} in bundle {}", path, bundle.getSymbolicName());
                String name = path.substring(path.lastIndexOf("/") + 1);
                resolvers.add(new BundleMetaLanguageResolver(bundle, name, path));
            }
        }
    }

    protected void registerDataFormats(Bundle bundle, List<BaseService> resolvers) {
        if (canSee(bundle, DataFormat.class)) {
            Map<String, String> dataformats = new HashMap<String, String>();
            for (Enumeration<?> e = bundle.getEntryPaths(META_INF_DATAFORMAT); e != null && e.hasMoreElements();) {
                String path = (String) e.nextElement();
                LOG.debug("Found entry: {} in bundle {}", path, bundle.getSymbolicName());
                String name = path.substring(path.lastIndexOf("/") + 1);
                dataformats.put(name, path);
            }
            if (!dataformats.isEmpty()) {
                resolvers.add(new BundleDataFormatResolver(bundle, dataformats));
            }
        }
    }

    protected void registerTypeConverterLoader(Bundle bundle, List<BaseService> resolvers) {
        if (canSee(bundle, TypeConverter.class)) {
            URL url1 = bundle.getEntry(META_INF_TYPE_CONVERTER);
            URL url2 = bundle.getEntry(META_INF_FALLBACK_TYPE_CONVERTER);
            if (url1 != null || url2 != null) {
                LOG.debug("Found TypeConverter in bundle {}", bundle.getSymbolicName());
                resolvers.add(new BundleTypeConverterLoader(bundle, url2 != null));
            }
        }
    }
    
    /**
     * Check if bundle can see the given class
     */
    protected boolean canSee(Bundle bundle, Class<?> clazz) {
        if (bundle.getBundleId() == bundleId) {
            // Need extra handling of camel core as it does not import the api
            return true;
        }
        BundleCapability packageCap = packageCapabilities.get(clazz.getPackage().getName());
        if (packageCap != null) {
            BundleWiring wiring = bundle.adapt(BundleWiring.class);
            List<BundleWire> imports = wiring.getRequiredWires(PACKAGE_NAMESPACE);
            for (BundleWire importWire : imports) {
                if (packageCap.equals(importWire.getCapability())) {
                    return true;
                }
            }
        }

        // it may be running outside real OSGi container such as when unit testing with camel-test-blueprint
        // then we need to use a different canSee algorithm that works outside real OSGi
        if (bundle.getBundleId() > 0) {
            Bundle root = bundle.getBundleContext().getBundle(0);
            if (root != null && "org.apache.felix.connect".equals(root.getSymbolicName())) {
                return checkCompat(bundle, clazz);
            }
        }

        return false;
    }

    /**
     * Check if bundle can see the given class used by camel-test-blueprint
     */
    protected static boolean checkCompat(Bundle bundle, Class<?> clazz) {
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

    protected static class BundleComponentResolver extends BaseResolver<Component> implements ComponentResolver {

        private final Map<String, String> components;

        public BundleComponentResolver(Bundle bundle, Map<String, String> components) {
            super(bundle, Component.class);
            this.components = components;
        }

        public Component resolveComponent(String name, CamelContext context) throws Exception {
            return createInstance(name, components.get(name), context);
        }

        public void register() {
            doRegister(ComponentResolver.class, "component", components.keySet());
        }
    }

    protected static class BundleLanguageResolver extends BaseResolver<Language> implements LanguageResolver {

        private final Map<String, String> languages;

        public BundleLanguageResolver(Bundle bundle, Map<String, String> languages) {
            super(bundle, Language.class);
            this.languages = languages;
        }

        public Language resolveLanguage(String name, CamelContext context) {
            return createInstance(name, languages.get(name), context);
        }

        public void register() {
            doRegister(LanguageResolver.class, "language", languages.keySet());
        }
    }

    protected static class BundleMetaLanguageResolver extends BaseResolver<LanguageResolver> implements LanguageResolver {

        private final String name;
        private final String path;

        public BundleMetaLanguageResolver(Bundle bundle, String name, String path) {
            super(bundle, LanguageResolver.class);
            this.name = name;
            this.path = path;
        }

        public Language resolveLanguage(String name, CamelContext context) {
            LanguageResolver resolver = createInstance(this.name, path, context);
            return resolver.resolveLanguage(name, context);
        }

        public void register() {
            doRegister(LanguageResolver.class, "resolver", name);
        }
    }

    protected static class BundleDataFormatResolver extends BaseResolver<DataFormat> implements DataFormatResolver {

        private final Map<String, String> dataformats;

        public BundleDataFormatResolver(Bundle bundle, Map<String, String> dataformats) {
            super(bundle, DataFormat.class);
            this.dataformats = dataformats;
        }

        @Override
        public DataFormat resolveDataFormat(String name, CamelContext context) {
            DataFormat dataFormat = createInstance(name, dataformats.get(name), context);
            if (dataFormat == null) {
                dataFormat = createDataFormat(name, context);
            }

            return dataFormat;
        }

        @Override
        public DataFormat createDataFormat(String name, CamelContext context) {
            return createInstance(name, dataformats.get(name), context);
        }

        public DataFormatDefinition resolveDataFormatDefinition(String name, CamelContext context) {
            return null;
        }

        @Override
        public void register() {
            doRegister(DataFormatResolver.class, "dataformat", dataformats.keySet());
        }
    }

    protected static class BundleTypeConverterLoader extends BaseResolver<TypeConverter> implements TypeConverterLoader {

        private final AnnotationTypeConverterLoader loader = new Loader();
        private final Bundle bundle;
        private final boolean hasFallbackTypeConverter;

        public BundleTypeConverterLoader(Bundle bundle, boolean hasFallbackTypeConverter) {
            super(bundle, TypeConverter.class);
            ObjectHelper.notNull(bundle, "bundle");
            this.bundle = bundle;
            this.hasFallbackTypeConverter = hasFallbackTypeConverter;
        }

        public synchronized void load(TypeConverterRegistry registry) throws TypeConverterLoaderException {
            // must be synchronized to ensure we don't load type converters concurrently
            // which cause Camel apps to fails in OSGi thereafter
            try {
                loader.load(registry);
            } catch (Exception e) {
                throw new TypeConverterLoaderException("Cannot load type converters using OSGi bundle: " + bundle.getBundleId(), e);
            }
        }

        public void register() {
            if (hasFallbackTypeConverter) {
                // The FallbackTypeConverter should have a higher ranking
                doRegister(TypeConverterLoader.class, Constants.SERVICE_RANKING, 100);
            } else {
                // The default service ranking is Integer(0);
                doRegister(TypeConverterLoader.class);
            }
        }

        class Loader extends AnnotationTypeConverterLoader {

            Loader() {
                super(null);
            }

            public void load(TypeConverterRegistry registry) throws TypeConverterLoaderException {
                PackageScanFilter test = new AnnotatedWithPackageScanFilter(Converter.class, true);
                Set<Class<?>> classes = new LinkedHashSet<Class<?>>();
                Set<String> packages = getConverterPackages(bundle.getEntry(META_INF_TYPE_CONVERTER));

                if (LOG.isTraceEnabled()) {
                    LOG.trace("Found {} {} packages: {}", new Object[]{packages.size(), META_INF_TYPE_CONVERTER, packages});
                }
                // if we only have camel-core on the classpath then we have already pre-loaded all its type converters
                // but we exposed the "org.apache.camel.core" package in camel-core. This ensures there is at least one
                // packageName to scan, which triggers the scanning process. That allows us to ensure that we look for
                // META-INF/services in all the JARs.
                if (packages.size() == 1 && "org.apache.camel.core".equals(packages.iterator().next())) {
                    LOG.debug("No additional package names found in classpath for annotated type converters.");
                    // no additional package names found to load type converters so break out
                    return;
                }

                // now filter out org.apache.camel.core as its not needed anymore (it was just a dummy)
                packages.remove("org.apache.camel.core");

                for (String pkg : packages) {

                    if (StringHelper.isClassName(pkg)) {
                        // its a FQN class name so load it directly
                        LOG.trace("Loading {} class", pkg);
                        try {
                            Class<?> clazz = bundle.loadClass(pkg);
                            classes.add(clazz);
                            // the class could be found and loaded so continue to next
                            continue;
                        } catch (Throwable t) {
                            // Ignore
                            LOG.trace("Failed to load " + pkg + " class due " + t.getMessage() + ". This exception will be ignored.", t);
                        }
                    }

                    // its not a FQN but a package name so scan for classes in the bundle
                    Enumeration<URL> e = bundle.findEntries("/" + pkg.replace('.', '/'), "*.class", true);
                    while (e != null && e.hasMoreElements()) {
                        String path = e.nextElement().getPath();
                        String externalName = path.substring(path.charAt(0) == '/' ? 1 : 0, path.indexOf('.')).replace('/', '.');
                        LOG.trace("Loading {} class", externalName);
                        try {
                            Class<?> clazz = bundle.loadClass(externalName);
                            if (test.matches(clazz)) {
                                classes.add(clazz);
                            }
                        } catch (Throwable t) {
                            // Ignore
                            LOG.trace("Failed to load " + externalName + " class due " + t.getMessage() + ". This exception will be ignored.", t);
                        }
                    }
                }

                // load the classes into type converter registry
                LOG.debug("Found {} @Converter classes to load", classes.size());
                for (Class<?> type : classes) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Loading converter class: {}", ObjectHelper.name(type));
                    }
                    loadConverterMethods(registry, type);
                }

                // register fallback converters
                URL fallbackUrl = bundle.getEntry(META_INF_FALLBACK_TYPE_CONVERTER);
                if (fallbackUrl != null) {
                    LOG.debug("Found {} to load the FallbackTypeConverter", META_INF_FALLBACK_TYPE_CONVERTER);
                    TypeConverter tc = createInstance("FallbackTypeConverter", fallbackUrl, registry.getInjector());
                    registry.addFallbackTypeConverter(tc, false);
                }

                // now clear the maps so we do not hold references
                visitedClasses.clear();
                visitedURIs.clear();
            }
        }

    }

    protected abstract static class BaseResolver<T> extends BaseService {

        private final Class<T> type;

        public BaseResolver(Bundle bundle, Class<T> type) {
            super(bundle);
            this.type = type;
        }

        protected T createInstance(String name, String path, CamelContext context) {
            if (path == null) {
                return null;
            }
            URL url = bundle.getEntry(path);
            LOG.trace("The entry {}'s url is {}", name, url);
            //Setup the TCCL with Camel context application class loader
            ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                ClassLoader newClassLoader = context.getApplicationContextClassLoader();
                if (newClassLoader != null) {
                    Thread.currentThread().setContextClassLoader(newClassLoader);
                }
                T answer = createInstance(name, url, context.getInjector());
                if (answer != null) {
                    initBundleContext(answer);
                }
                return answer;
            } finally {
                Thread.currentThread().setContextClassLoader(oldClassLoader);
            }
        }

        private void initBundleContext(T answer) {
            try {
                Method method = answer.getClass().getMethod("setBundleContext", BundleContext.class);
                if (method != null) {
                    method.invoke(answer, bundle.getBundleContext());
                }
            } catch (Exception e) {
                // ignore
            }
        }

        @SuppressWarnings("unchecked")
        protected T createInstance(String name, URL url, Injector injector) {
            try {
                Properties properties = loadProperties(url);
                String classname = (String) properties.get("class");
                Class<?> type = bundle.loadClass(classname);
                if (!this.type.isAssignableFrom(type)) {
                    throw new IllegalArgumentException("Type is not a " + this.type.getName() + " implementation. Found: " + type.getName());
                }
                return injector.newInstance((Class<T>) type);
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("Invalid URI, no " + this.type.getName() + " registered for scheme : " + name, e);
            }
        }

    }

    protected abstract static class BaseService {

        protected final Bundle bundle;
        private ServiceRegistration<?> reg;

        protected BaseService(Bundle bundle) {
            this.bundle = bundle;
        }

        public abstract void register();

        protected void doRegister(Class<?> type, String key, Collection<String> value) {
            doRegister(type, key, value.toArray(new String[value.size()]));
        }

        protected void doRegister(Class<?> type, String key, Object value) {
            Dictionary<String, Object> props = new Hashtable<String, Object>();
            props.put(key, value);
            doRegister(type, props);
        }

        protected void doRegister(Class<?> type) {
            doRegister(type, null);
        }

        protected void doRegister(Class<?> type, Dictionary<String, ?> props) {
            reg = bundle.getBundleContext().registerService(type.getName(), this, props);
        }

        public void unregister() {
            reg.unregister();
        }
    }

    protected static Properties loadProperties(URL url) {
        Properties properties = new Properties();
        BufferedInputStream reader = null;
        try {
            reader = IOHelper.buffered(url.openStream());
            properties.load(reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            IOHelper.close(reader, "properties", LOG);
        }
        return properties;
    }
    
    protected static Set<String> getConverterPackages(URL resource) {
        Set<String> packages = new LinkedHashSet<String>();
        if (resource != null) {
            BufferedReader reader = null;
            try {
                reader = IOHelper.buffered(new InputStreamReader(resource.openStream()));
                while (true) {
                    String line = reader.readLine();
                    if (line == null) {
                        break;
                    }
                    line = line.trim();
                    if (line.startsWith("#") || line.length() == 0) {
                        continue;
                    }
                    StringTokenizer iter = new StringTokenizer(line, ",");
                    while (iter.hasMoreTokens()) {
                        String name = iter.nextToken().trim();
                        if (name.length() > 0) {
                            packages.add(name);
                        }
                    }
                }
            } catch (Exception ignore) {
                // Do nothing here
            } finally {
                IOHelper.close(reader, null, LOG);
            }
        }
        return packages;
    }

}


