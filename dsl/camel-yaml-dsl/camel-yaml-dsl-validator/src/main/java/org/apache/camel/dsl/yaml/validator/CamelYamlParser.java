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
package org.apache.camel.dsl.yaml.validator;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

import com.networknt.schema.ValidationMessage;
import org.apache.camel.CamelContext;
import org.apache.camel.TypeConverterExists;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.component.stub.StubComponent;
import org.apache.camel.dsl.yaml.YamlRoutesBuilderLoader;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.main.stub.StubBeanRepository;
import org.apache.camel.main.stub.StubDataFormat;
import org.apache.camel.main.stub.StubEipReifier;
import org.apache.camel.main.stub.StubLanguage;
import org.apache.camel.main.stub.StubTransformer;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.spi.LanguageResolver;
import org.apache.camel.spi.TransformerResolver;
import org.apache.camel.support.DefaultRegistry;
import org.apache.camel.support.ResourceHelper;

/**
 * Camel YAML parser that parses YAML DSL routes and also checks the routes can be loaded by Camel. This parser does not
 * start any routes, and will stub every component, dataformat, language which would require to have all dependencies on
 * classpath and 3rd party JARs may trigger some initialization that can distort this parser.
 * <p>
 * This is a faster and lighter parser than the {@link YamlValidator} which uses a similar concept as in camel-jbang.
 */
public class CamelYamlParser {

    public List<ValidationMessage> parse(File file) throws Exception {
        CamelContext camelContext = null;
        try {
            DefaultRegistry registry = new DefaultRegistry();
            registry.addBeanRepository(new StubBeanRepository("*"));

            camelContext = new DefaultCamelContext(registry);
            camelContext.setAutoStartup(false);
            camelContext.getCamelContextExtension().addContextPlugin(ComponentResolver.class,
                    (name, context) -> new StubComponent());
            camelContext.getCamelContextExtension().addContextPlugin(DataFormatResolver.class,
                    (name, context) -> new StubDataFormat());
            camelContext.getCamelContextExtension().addContextPlugin(LanguageResolver.class,
                    (name, context) -> new StubLanguage());
            camelContext.getCamelContextExtension().addContextPlugin(TransformerResolver.class,
                    (name, context) -> new StubTransformer());

            // when exporting we should ignore some errors and keep attempting to export as far as we can
            PropertiesComponent pc = (PropertiesComponent) camelContext.getPropertiesComponent();
            pc.addInitialProperty("camel.component.properties.ignore-missing-property", "true");
            pc.addInitialProperty("camel.component.properties.ignore-missing-location", "true");
            pc.setPropertiesParser(new DummyPropertiesParser(camelContext));

            // override default type converters with our export converter that is more flexible during exporting
            DummyTypeConverter ec = new DummyTypeConverter();
            camelContext.getTypeConverterRegistry().setTypeConverterExists(TypeConverterExists.Override);
            camelContext.getTypeConverterRegistry().addTypeConverter(Integer.class, String.class, ec);
            camelContext.getTypeConverterRegistry().addTypeConverter(Long.class, String.class, ec);
            camelContext.getTypeConverterRegistry().addTypeConverter(Double.class, String.class, ec);
            camelContext.getTypeConverterRegistry().addTypeConverter(Float.class, String.class, ec);
            camelContext.getTypeConverterRegistry().addTypeConverter(Byte.class, String.class, ec);
            camelContext.getTypeConverterRegistry().addTypeConverter(Boolean.class, String.class, ec);
            camelContext.getTypeConverterRegistry().addFallbackTypeConverter(ec, false);

            // stub EIPs
            StubEipReifier.registerStubEipReifiers(camelContext);

            // start camel
            camelContext.start();

            // load yaml to validate
            try (YamlRoutesBuilderLoader loader = new YamlRoutesBuilderLoader()) {
                loader.setCamelContext(camelContext);
                loader.start();
                var rb = loader.doLoadRouteBuilder(ResourceHelper.fromString(file.getName(), Files.readString(file.toPath())));
                camelContext.addRoutes(rb);
                return Collections.emptyList();
            }
        } catch (Exception e) {
            ValidationMessage vm = ValidationMessage.builder().type("parser")
                    .messageSupplier(() -> e.getClass().getName() + ": " + e.getMessage()).build();
            return List.of(vm);
        } finally {
            if (camelContext != null) {
                camelContext.stop();
            }
        }
    }

}
