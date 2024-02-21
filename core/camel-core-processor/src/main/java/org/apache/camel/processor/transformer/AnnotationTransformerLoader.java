/*
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

package org.apache.camel.processor.transformer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Message;
import org.apache.camel.TypeConverterLoaderException;
import org.apache.camel.impl.engine.DefaultPackageScanClassResolver;
import org.apache.camel.impl.engine.TransformerKey;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerLoader;
import org.apache.camel.spi.TransformerRegistry;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transformer loader scans packages for {@link org.apache.camel.spi.Transformer} classes annotated with
 * {@link DataType} annotation.
 */
public class AnnotationTransformerLoader extends Transformer implements TransformerLoader, CamelContextAware {

    public static final String META_INF_SERVICES = "META-INF/services/org/apache/camel/Transformer";

    private static final Logger LOG = LoggerFactory.getLogger(AnnotationTransformerLoader.class);

    private CamelContext camelContext;

    private String packageName;

    private PackageScanClassResolver resolver;

    private final Set<Class<?>> visitedClasses = new HashSet<>();
    private final Set<String> visitedURIs = new HashSet<>();

    @Override
    public void load(TransformerRegistry registry) {
        ObjectHelper.notNull(camelContext, "camelContext");

        if (resolver == null) {
            if (camelContext instanceof ExtendedCamelContext) {
                resolver = PluginHelper.getPackageScanClassResolver(camelContext);
            } else {
                resolver = new DefaultPackageScanClassResolver();
            }
        }

        Set<String> packages = new HashSet<>();

        if (packageName == null || packageName.equals("*")) {
            LOG.trace("Searching for {} services", META_INF_SERVICES);
            try {
                ClassLoader ccl = Thread.currentThread().getContextClassLoader();
                if (ccl != null) {
                    findPackages(packages, ccl);
                }
                findPackages(packages, getClass().getClassLoader());
                if (packages.isEmpty()) {
                    LOG.debug("No package names found to be used for classpath scanning for annotated data types.");
                    return;
                }
            } catch (Exception e) {
                throw new TypeConverterLoaderException(
                        "Cannot find package names to be used for classpath scanning for annotated data types.", e);
            }
        } else {
            packages.add(packageName);
        }

        // scan packages and load annotated transformer classes
        if (LOG.isTraceEnabled()) {
            LOG.trace("Found data type packages to scan: {}", String.join(", ", packages));
        }
        Set<Class<?>> scannedClasses = resolver.findAnnotated(DataTypeTransformer.class, packages.toArray(new String[] {}));
        if (!scannedClasses.isEmpty()) {
            LOG.debug("Found {} packages with {} @DataType classes to load", packages.size(), scannedClasses.size());

            // load all the found classes into the type data type registry
            for (Class<?> type : scannedClasses) {
                if (acceptClass(type)) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Loading data type annotation: {}", ObjectHelper.name(type));
                    }
                    registerTransformer(registry, type);
                }
            }
        }

        // now clear the maps so we do not hold references
        visitedClasses.clear();
        visitedURIs.clear();
    }

    private void registerTransformer(TransformerRegistry registry, Class<?> type) {
        if (visitedClasses.contains(type)) {
            return;
        }
        visitedClasses.add(type);

        try {
            if (Transformer.class.isAssignableFrom(type) && type.isAnnotationPresent(DataTypeTransformer.class)) {
                DataTypeTransformer dt = type.getAnnotation(DataTypeTransformer.class);
                Transformer transformer = (Transformer) camelContext.getInjector().newInstance(type);
                if (!ObjectHelper.isEmpty(dt.name())) {
                    registry.put(new TransformerKey(dt.name()), transformer);
                }

                if (!DataType.isAnyType(new DataType(dt.fromType())) || !DataType.isAnyType(new DataType(dt.toType()))) {
                    registry.put(new TransformerKey(new DataType(dt.fromType()), new DataType(dt.toType())), transformer);
                }
            }
        } catch (NoClassDefFoundError e) {
            LOG.debug("Ignoring transformer type: {} as a dependent class could not be found: {}",
                    type.getCanonicalName(), e, e);
        }
    }

    protected boolean acceptClass(Class<?> type) {
        return Transformer.class.isAssignableFrom(type) && type.isAnnotationPresent(DataTypeTransformer.class);
    }

    protected void findPackages(Set<String> packages, ClassLoader classLoader) throws IOException {
        Enumeration<URL> resources = classLoader.getResources(META_INF_SERVICES);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            String path = url.getPath();
            if (!visitedURIs.contains(path)) {
                // remember we have visited this uri so we wont read it twice
                visitedURIs.add(path);
                LOG.debug("Loading file {} to retrieve list of packages, from url: {}", META_INF_SERVICES, url);
                try (BufferedReader reader
                        = IOHelper.buffered(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                    while (true) {
                        String line = reader.readLine();
                        if (line == null) {
                            break;
                        }
                        line = line.trim();
                        if (line.startsWith("#") || line.isEmpty()) {
                            continue;
                        }
                        packages.add(line);
                    }
                }
            }
        }
    }

    @Override
    public void transform(Message message, DataType from, DataType to) throws Exception {
        // noop
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }
}
