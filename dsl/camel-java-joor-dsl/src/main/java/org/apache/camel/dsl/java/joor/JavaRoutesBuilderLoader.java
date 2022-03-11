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
package org.apache.camel.dsl.java.joor;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelConfiguration;
import org.apache.camel.CamelContext;
import org.apache.camel.Configuration;
import org.apache.camel.Converter;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.TypeConverterExists;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dsl.support.AnnotationPreProcessor;
import org.apache.camel.dsl.support.RouteBuilderLoaderSupport;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.spi.annotations.RoutesLoader;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.joor.Reflect;

@ManagedResource(description = "Managed JavaRoutesBuilderLoader")
@RoutesLoader(JavaRoutesBuilderLoader.EXTENSION)
public class JavaRoutesBuilderLoader extends RouteBuilderLoaderSupport {
    public static final String EXTENSION = "java";
    public static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "^\\s*package\\s+([a-zA-Z][\\.\\w]*)\\s*;.*$", Pattern.MULTILINE);

    public JavaRoutesBuilderLoader() {
        super(EXTENSION);

        addAnnotationPreProcessor(new ConverterAnnotationPreProcessor());
        addAnnotationPreProcessor(new BindToRegistryAnnotationPreProcessor());
    }

    @Override
    public RouteBuilder doLoadRouteBuilder(Resource resource) throws Exception {
        try (InputStream is = resource.getInputStream()) {
            if (is == null) {
                throw new FileNotFoundException(resource.getLocation());
            }
            String content = IOHelper.loadText(is);
            String name = determineName(resource, content);

            Reflect ref = Reflect.compile(name, content).create();
            Class<?> clazz = ref.type();

            Object obj = ref.get();
            if (obj instanceof RouteBuilder) {
                return (RouteBuilder) obj;
            }

            // not a route builder but we support annotation scan to register custom beans, type converters, etc.
            for (AnnotationPreProcessor pre : getAnnotationPreProcessors()) {
                pre.handleAnnotation(getCamelContext(), name, clazz, obj);
            }

            return null;
        }
    }

    private static String determineName(Resource resource, String content) {
        String loc = resource.getLocation();
        // strip scheme to compute the name
        String scheme = ResourceHelper.getScheme(loc);
        if (scheme != null) {
            loc = loc.substring(scheme.length());
        }
        final String name = FileUtil.onlyName(loc, true);
        final Matcher matcher = PACKAGE_PATTERN.matcher(content);

        return matcher.find()
                ? matcher.group(1) + "." + name
                : name;
    }

    private static class ConverterAnnotationPreProcessor implements AnnotationPreProcessor {

        @Override
        public void handleAnnotation(CamelContext camelContext, String name, Class<?> clazz, Object instance) {
            if (clazz.getAnnotation(Converter.class) != null) {
                TypeConverterRegistry tcr = camelContext.getTypeConverterRegistry();
                TypeConverterExists exists = tcr.getTypeConverterExists();
                LoggingLevel level = tcr.getTypeConverterExistsLoggingLevel();
                // force type converter to override as we could be re-loading
                tcr.setTypeConverterExists(TypeConverterExists.Override);
                tcr.setTypeConverterExistsLoggingLevel(LoggingLevel.OFF);
                try {
                    tcr.addTypeConverters(clazz);
                } finally {
                    tcr.setTypeConverterExists(exists);
                    tcr.setTypeConverterExistsLoggingLevel(level);
                }
            }
        }
    }

    private static class BindToRegistryAnnotationPreProcessor implements AnnotationPreProcessor {

        @Override
        public void handleAnnotation(CamelContext camelContext, String name, Class<?> clazz, Object instance)
                throws Exception {
            BindToRegistry bir = instance.getClass().getAnnotation(BindToRegistry.class);
            Configuration cfg = instance.getClass().getAnnotation(Configuration.class);
            if (bir != null || cfg != null || instance instanceof CamelConfiguration) {
                CamelBeanPostProcessor bpp = camelContext.adapt(ExtendedCamelContext.class).getBeanPostProcessor();
                if (bir != null && ObjectHelper.isNotEmpty(bir.value())) {
                    name = bir.value();
                } else if (cfg != null && ObjectHelper.isNotEmpty(cfg.value())) {
                    name = cfg.value();
                }
                // to support hot reloading of beans then we need to enable unbind mode in bean post processor
                bpp.setUnbindEnabled(true);
                try {
                    // this class is a bean service which needs to be post processed and registered which happens
                    // automatic by the bean post processor
                    bpp.postProcessBeforeInitialization(instance, name);
                    bpp.postProcessAfterInitialization(instance, name);
                } finally {
                    bpp.setUnbindEnabled(false);
                }
                if (instance instanceof CamelConfiguration) {
                    ((CamelConfiguration) instance).configure(camelContext);
                }
            }
        }

    }

}
