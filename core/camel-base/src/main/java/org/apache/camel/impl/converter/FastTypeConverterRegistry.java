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
package org.apache.camel.impl.converter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.TypeConverterLoader;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An optimized {@link org.apache.camel.spi.TypeConverterRegistry} which loads
 * the type converters up-front on startup in a faster way by leveraging
 * source generated type converter loaders (<tt>@Converter(loader = true)</tt>,
 * as well as invoking the type converters without reflection,
 * and will not perform classpath scanning.
 */
public class FastTypeConverterRegistry extends BaseTypeConverterRegistry {

    public static final String META_INF_SERVICES = "META-INF/services/org/apache/camel/TypeConverterLoader";

    private static final Logger LOG = LoggerFactory.getLogger(FastTypeConverterRegistry.class);
    private static final Charset UTF8 = Charset.forName("UTF-8");

    public FastTypeConverterRegistry() {
        super(null, null, null); // pass in null to base class as we load all type converters without package scanning
        setAnnotationScanning(false);
    }

    @Override
    protected void initAnnotationTypeConverterLoader(PackageScanClassResolver resolver) {
        // noop
    }

    @Override
    public boolean allowNull() {
        return false;
    }

    @Override
    public boolean isRunAllowed() {
        // as type converter is used during initialization then allow it to always run
        return true;
    }

    @Override
    protected void doInit() {
        try {
            // core type converters is always loaded which does not use any classpath scanning and therefore is fast
            loadCoreTypeConverters();
            int core = typeMappings.size();
            // load type converters up front
            log.debug("Initializing FastTypeConverterRegistry - requires converters to be annotated with @Converter(loader = true)");
            loadTypeConverters();
            int additional = typeMappings.size() - core;
            // report how many type converters we have loaded
            log.info("Type converters loaded (core: {}, classpath: {})", core, additional);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    @Override
    protected void doStart() throws Exception {
        // we are using backwards compatible legacy mode to detect additional converters
        if (camelContext.isLoadTypeConverters()) {
            try {
                // we need an injector for annotation scanning
                setInjector(camelContext.getInjector());

                int fast = typeMappings.size();
                // load type converters up front
                TypeConverterLoader loader = new FastAnnotationTypeConverterLoader(camelContext.getPackageScanClassResolver());
                loader.load(this);
                int additional = typeMappings.size() - fast;
                // report how many type converters we have loaded
                if (additional > 0) {
                    log.info("Type converters loaded (fast: {}, scanned: {})", fast, additional);
                    log.warn("Annotation scanning mode loaded {} type converters. Its recommended to migrate to @Converter(loader = true) for fast type converter mode.", additional);
                }
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
    }

    @Override
    public void loadTypeConverters() throws Exception {
        String[] lines = findTypeConverterLoaderClasses();
        for (String line : lines) {
            String name = StringHelper.after(line, "class=");
            if (name != null) {
                LOG.debug("Resolving TypeConverterLoader: {}", name);
                Class clazz = getCamelContext().getClassResolver().resolveMandatoryClass(name);
                Object obj = getCamelContext().getInjector().newInstance(clazz);
                if (obj instanceof TypeConverterLoader) {
                    TypeConverterLoader loader = (TypeConverterLoader) obj;
                    LOG.debug("TypeConverterLoader: {} loading converters", name);
                    loader.load(this);
                }
            }
        }
    }

    /**
     * Finds the type converter loader classes from the classpath looking
     * for text files on the classpath at the {@link #META_INF_SERVICES} location.
     */
    protected String[] findTypeConverterLoaderClasses() throws IOException {
        Set<String> classes = new LinkedHashSet<>();
        findLoaders(classes, getClass().getClassLoader());
        return classes.toArray(new String[classes.size()]);
    }

    protected void findLoaders(Set<String> packages, ClassLoader classLoader) throws IOException {
        Enumeration<URL> resources = classLoader.getResources(META_INF_SERVICES);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            LOG.debug("Loading file {} to retrieve list of type converters, from url: {}", META_INF_SERVICES, url);
            BufferedReader reader = IOHelper.buffered(new InputStreamReader(url.openStream(), UTF8));
            try {
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
            } finally {
                IOHelper.close(reader, null, LOG);
            }
        }
    }

    /**
     * Tokenizes the line from the META-IN/services file using commas and
     * ignoring whitespace between packages
     */
    private void tokenize(Set<String> packages, String line) {
        StringTokenizer iter = new StringTokenizer(line, ",");
        while (iter.hasMoreTokens()) {
            String name = iter.nextToken().trim();
            if (name.length() > 0) {
                packages.add(name);
            }
        }
    }

}
