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
package org.apache.camel.impl.converter;

import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.AnnotationScanTypeConverters;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.TypeConverterLoader;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of a type converter registry used for
 * <a href="http://camel.apache.org/type-converter.html">type converters</a> in Camel.
 * <p>
 * This implementation will load type converters up-front on startup.
 */
public class DefaultTypeConverter extends BaseTypeConverterRegistry implements AnnotationScanTypeConverters {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultTypeConverter.class);

    private volatile boolean loadTypeConvertersDone;
    private final boolean loadTypeConverters;
    private Set<String> typeConverterFiles;
    private boolean typeConverterFilesWarned;

    public DefaultTypeConverter(PackageScanClassResolver resolver, Injector injector,
                                boolean loadTypeConverters, boolean statisticsEnabled) {
        this(null, resolver, injector, loadTypeConverters, statisticsEnabled);
    }

    public DefaultTypeConverter(CamelContext camelContext, PackageScanClassResolver resolver, Injector injector,
                                boolean loadTypeConverters, boolean statisticsEnabled) {
        super(camelContext, resolver, injector, statisticsEnabled);
        this.loadTypeConverters = loadTypeConverters;
    }

    @Override
    public boolean isRunAllowed() {
        // as type converter is used during initialization then allow it to always run
        return true;
    }

    @Override
    protected void doInit() throws Exception {
        StopWatch watch = new StopWatch();

        super.doInit();

        // core type converters is always loaded which does not use any classpath scanning and therefore is fast
        loadCoreAndFastTypeConverters();

        String time = TimeUtils.printDuration(watch.taken(), true);
        LOG.debug("Loaded {} type converters in {}", size(), time);

        if (!loadTypeConvertersDone && isLoadTypeConverters()) {
            scanTypeConverters();
        }
    }

    @Override
    public void loadCoreAndFastTypeConverters() throws Exception {
        super.loadCoreAndFastTypeConverters();

        // the META-INF/services/org/apache/camel/TypeConverter files are only read when scanning for type converters,
        // remember them to warn on startup if scanning is turned off (runtimes that discover the converters at build
        // time override this method, and do not get the warning)
        Set<String> files = new LinkedHashSet<>();
        try {
            for (URL url : getLoaderUrls(AnnotationTypeConverterLoader.META_INF_SERVICES)) {
                files.add(url.toString());
            }
        } catch (IOException e) {
            LOG.debug("Cannot find {} files due to: {}", AnnotationTypeConverterLoader.META_INF_SERVICES, e.getMessage(), e);
        }
        typeConverterFiles = files;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // loadTypeConverters can be set after this type converter is initialized (such as by camel-main),
        // so check on startup
        if (!typeConverterFilesWarned && !loadTypeConvertersDone && typeConverterFiles != null
                && !typeConverterFiles.isEmpty() && !isLoadTypeConverters() && getBasePackageScan() == null) {
            typeConverterFilesWarned = true;
            LOG.warn("Type converter file {} found at {} is not read, because scanning the classpath for type converters"
                     + " is turned off, so the @Converter classes it lists are not loaded."
                     + " Set camel.main.loadTypeConverters=true (or setLoadTypeConverters(true) on the CamelContext),"
                     + " or annotate the converter classes with @Converter(generateLoader = true) and build them with the"
                     + " camel-component-maven-plugin, or add them with addTypeConverters on the TypeConverterRegistry.",
                    AnnotationTypeConverterLoader.META_INF_SERVICES, String.join(", ", typeConverterFiles));
        }
    }

    private String getBasePackageScan() {
        return camelContext != null ? camelContext.getCamelContextExtension().getBasePackageScan() : null;
    }

    private boolean isLoadTypeConverters() {
        boolean load = loadTypeConverters;
        if (camelContext != null) {
            // camel context can override
            load = camelContext.isLoadTypeConverters();
        }
        return load;
    }

    @Override
    public void scanTypeConverters() throws Exception {
        StopWatch watch = new StopWatch();

        // we are using backwards compatible legacy mode to detect additional converters
        if (!loadTypeConvertersDone) {
            loadTypeConvertersDone = true;

            if (resolver != null) {
                typeConverterLoaders.add(createScanTypeConverterLoader());
            }

            // load type converters up front
            loadTypeConverters();

            // lets clear the cache from the resolver as its often only used during startup
            if (resolver != null) {
                resolver.clearCache();
            }
        }

        String time = TimeUtils.printDuration(watch.taken(), true);
        LOG.debug("Loaded {} type converters in {}", size(), time);
    }

    /**
     * Creates the {@link TypeConverterLoader} to use for scanning for type converters such as from the classpath.
     */
    protected TypeConverterLoader createScanTypeConverterLoader() {
        return new AnnotationTypeConverterLoader(resolver, getBasePackageScan());
    }
}
