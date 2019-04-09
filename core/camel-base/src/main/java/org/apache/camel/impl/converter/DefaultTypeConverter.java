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

import org.apache.camel.CamelContext;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.PackageScanClassResolver;

/**
 * Default implementation of a type converter registry used for
 * <a href="http://camel.apache.org/type-converter.html">type converters</a> in Camel.
 * <p/>
 * This implementation will load type converters up-front on startup.
 */
public class DefaultTypeConverter extends BaseTypeConverterRegistry {

    private final boolean loadTypeConverters;

    public DefaultTypeConverter(PackageScanClassResolver resolver, Injector injector,
                                FactoryFinder factoryFinder, boolean loadTypeConverters) {
        this(null, resolver, injector, factoryFinder, loadTypeConverters);
    }

    public DefaultTypeConverter(CamelContext camelContext, PackageScanClassResolver resolver, Injector injector,
                                FactoryFinder factoryFinder, boolean loadTypeConverters) {
        super(camelContext, resolver, injector, factoryFinder);
        this.loadTypeConverters = loadTypeConverters;
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
        super.doInit();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // core type converters is always loaded which does not use any classpath scanning and therefore is fast
        loadCoreTypeConverters();

        // we are using backwards compatible legacy mode to detect additional converters
        if (loadTypeConverters) {
            int fast = typeMappings.size();
            // load type converters up front
            loadTypeConverters();
            int additional = typeMappings.size() - fast;

            // report how many type converters we have loaded
            if (additional > 0) {
                log.info("Type converters loaded (fast: {}, scanned: {})", fast, additional);
                log.warn("Annotation scanning mode loaded {} type converters. Its recommended to migrate to @Converter(loader = true) for fast type converter mode.", additional);
            }
        }
    }

    protected void initTypeConverterLoaders() {
        if (resolver != null) {
            typeConverterLoaders.add(new FastAnnotationTypeConverterLoader(resolver));
        }
    }

}
