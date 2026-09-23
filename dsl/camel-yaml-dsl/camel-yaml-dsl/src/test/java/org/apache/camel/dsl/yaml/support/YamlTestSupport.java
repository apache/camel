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
package org.apache.camel.dsl.yaml.support;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import org.apache.camel.CamelContext;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dsl.yaml.KameletRoutesBuilderLoader;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.spi.HasCamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class YamlTestSupport implements HasCamelContext {

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());
    private static final Schema SCHEMA;

    static {
        try {
            var schemaNode = MAPPER.readTree(YamlTestSupport.class.getResourceAsStream("/schema/camelYamlDsl.json"));
            var schemaRegistryConfig = SchemaRegistryConfig.builder().locale(Locale.ENGLISH).build();
            var schemaRegistry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_4,
                    builder -> builder.schemaRegistryConfig(schemaRegistryConfig));
            SCHEMA = schemaRegistry.getSchema(SchemaLocation.of("/schema/camelYamlDsl.json"), schemaNode);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected DefaultCamelContext context = new DefaultCamelContext();

    @BeforeEach
    void setup() throws Exception {
        context.disableJMX();
        context.setStreamCaching(true);
        doSetup();
    }

    @AfterEach
    void cleanup() throws Exception {
        if (context.isStarted()) {
            context.stop();
        }
        doCleanup();
    }

    public void doSetup() throws Exception {
    }

    public void doCleanup() throws Exception {
    }

    public void loadRoutes(Collection<Resource> resources, boolean validate) throws Exception {
        if (validate) {
            for (Resource resource : resources) {
                var target = MAPPER.readTree(resource.getInputStream());
                List<Error> report = SCHEMA.validate(target);
                if (!report.isEmpty()) {
                    throw new IllegalArgumentException(report.toString());
                }
            }
        }
        PluginHelper.getRoutesLoader(context).loadRoutes(resources);
    }

    public void loadRoutes(Resource... resources) throws Exception {
        loadRoutes(Arrays.asList(resources));
    }

    public void loadRoutes(Collection<Resource> resources) throws Exception {
        loadRoutes(resources, true);
    }

    public void loadRoutes(String... resources) throws Exception {
        loadRoutesExt("yaml", resources);
    }

    public void loadRoutesExt(String ext, String... resources) throws Exception {
        List<Resource> resourceList = new ArrayList<>();
        int[] index = { 0 };
        for (String content : resources) {
            resourceList.add(ResourceHelper.fromString("route-" + (index[0]++) + "." + ext, stripIndent(content)));
        }
        loadRoutes(resourceList);
    }

    public void loadRoutesNoValidate(String... resources) throws Exception {
        List<Resource> resourceList = new ArrayList<>();
        int[] index = { 0 };
        for (String content : resources) {
            resourceList.add(ResourceHelper.fromString("route-" + (index[0]++) + ".yaml", stripIndent(content)));
        }
        loadRoutes(resourceList, false);
    }

    public void loadKamelets(Resource... resources) throws Exception {
        loadKamelets(Arrays.asList(resources));
    }

    public void loadKamelets(Collection<Resource> resources) throws Exception {
        KameletRoutesBuilderLoader kl = new KameletRoutesBuilderLoader();
        kl.setCamelContext(context);
        kl.start();
        for (Resource r : resources) {
            kl.loadRoutesBuilder(r);
        }
    }

    public void loadKamelets(String... resources) throws Exception {
        List<Resource> resourceList = new ArrayList<>();
        int[] index = { 0 };
        for (String content : resources) {
            resourceList.add(ResourceHelper.fromString("route-" + (index[0]++) + ".kamelet.yaml", stripIndent(content)));
        }
        PluginHelper.getRoutesLoader(context).loadRoutes(resourceList);
    }

    public void loadBindings(String... resources) throws Exception {
        List<Resource> resourceList = new ArrayList<>();
        int[] index = { 0 };
        for (String content : resources) {
            resourceList.add(ResourceHelper.fromString("binding-" + (index[0]++) + ".yaml", stripIndent(content)));
        }
        PluginHelper.getRoutesLoader(context).loadRoutes(resourceList);
    }

    public void loadBindingsExt(String ext, String... resources) throws Exception {
        List<Resource> resourceList = new ArrayList<>();
        int[] index = { 0 };
        for (String content : resources) {
            resourceList.add(ResourceHelper.fromString("binding-" + (index[0]++) + "." + ext, stripIndent(content)));
        }
        PluginHelper.getRoutesLoader(context).loadRoutes(resourceList);
    }

    public void addTemplate(String name, Consumer<RouteTemplateDefinition> configurer) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                configurer.accept(routeTemplate(name));
            }
        });
    }

    public MockEndpoint withMock(String uri, Consumer<MockEndpoint> configurer) {
        MockEndpoint mock = context.getEndpoint(uri, MockEndpoint.class);
        configurer.accept(mock);
        return mock;
    }

    public void withTemplate(Consumer<FluentProducerTemplate> configurer) {
        try (FluentProducerTemplate template = context.createFluentProducerTemplate()) {
            configurer.accept(template);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Resource asResource(String location, String content) {
        return new Resource() {
            @Override
            public String getScheme() {
                return "mem";
            }

            @Override
            public String getLocation() {
                return location.endsWith(".yaml") ? location : location + ".yaml";
            }

            @Override
            public boolean exists() {
                return true;
            }

            @Override
            public java.io.InputStream getInputStream() throws java.io.IOException {
                return new java.io.ByteArrayInputStream(stripIndent(content).getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public String toString() {
                return location;
            }
        };
    }

    /**
     * Strips common leading whitespace from a multi-line string (equivalent to Groovy's stripIndent()).
     */
    private static String stripIndent(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String[] lines = text.split("\n", -1);
        // find minimum indentation (ignoring empty/blank lines)
        int minIndent = Integer.MAX_VALUE;
        for (String line : lines) {
            if (!line.isBlank()) {
                int indent = 0;
                for (char c : line.toCharArray()) {
                    if (c == ' ') {
                        indent++;
                    } else if (c == '\t') {
                        indent += 4;
                    } else {
                        break;
                    }
                }
                minIndent = Math.min(minIndent, indent);
            }
        }
        if (minIndent == Integer.MAX_VALUE) {
            minIndent = 0;
        }
        final int strip = minIndent;
        return Arrays.stream(lines)
                .map(line -> line.length() >= strip ? line.substring(strip) : line.stripLeading())
                .collect(Collectors.joining("\n"));
    }

    @Override
    public CamelContext getCamelContext() {
        return context;
    }
}
