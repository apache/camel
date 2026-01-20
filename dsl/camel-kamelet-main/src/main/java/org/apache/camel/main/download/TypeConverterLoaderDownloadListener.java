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
package org.apache.camel.main.download;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.impl.converter.BaseTypeConverterRegistry;
import org.apache.camel.spi.TypeConverterLoader;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TypeConverterLoaderDownloadListener implements ArtifactDownloadListener, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(TypeConverterLoaderDownloadListener.class);

    private CamelContext camelContext;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void onDownloadedFile(File file) {
        try {
            loadTypeConverters(file);
        } catch (Exception e) {
            // ignore
        }
    }

    protected void loadTypeConverters(File file) throws Exception {
        // use isolated classloader to load the service file as we only want to check this file
        // (and not what is already in the existing classloader)
        try (DependencyDownloaderClassLoader cl = new DependencyDownloaderClassLoader(null)) {
            cl.addFile(file);

            // load names for custom type converters from the downloaded JAR
            Collection<String> loaders = new ArrayList<>();
            findTypeConverterLoaderClasses(loaders,
                    cl.getResourceAsStream(BaseTypeConverterRegistry.META_INF_SERVICES_TYPE_CONVERTER_LOADER));
            findTypeConverterLoaderClasses(loaders,
                    cl.getResourceAsStream(BaseTypeConverterRegistry.META_INF_SERVICES_FALLBACK_TYPE_CONVERTER));
            loadTypeConverters(loaders);
        }
    }

    protected void findTypeConverterLoaderClasses(Collection<String> loaders, InputStream is) throws IOException {
        if (is != null) {
            BufferedReader reader = IOHelper.buffered(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            do {
                line = reader.readLine();
                if (line != null && !line.startsWith("#") && !line.isEmpty()) {
                    loaders.add(line);
                }
            } while (line != null);
            IOHelper.close(reader);
        }
    }

    protected void loadTypeConverters(Collection<String> loaders) throws ClassNotFoundException {
        for (String name : loaders) {
            LOG.debug("Resolving TypeConverterLoader: {}", name);
            Class<?> clazz = getCamelContext().getClassResolver().resolveMandatoryClass(name);
            Object obj = getCamelContext().getInjector().newInstance(clazz, false);
            CamelContextAware.trySetCamelContext(obj, getCamelContext());
            if (obj instanceof TypeConverterLoader) {
                TypeConverterLoader loader = (TypeConverterLoader) obj;
                CamelContextAware.trySetCamelContext(loader, getCamelContext());
                LOG.debug("TypeConverterLoader: {} loading converters", name);
                loader.load(getCamelContext().getTypeConverterRegistry());
            }
        }
    }

}
