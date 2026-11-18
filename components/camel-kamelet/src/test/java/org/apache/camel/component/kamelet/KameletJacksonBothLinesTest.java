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
package org.apache.camel.component.kamelet;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Enumeration;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson3.JacksonDataFormat;
import org.apache.camel.component.jackson3.JacksonDataFormatConfigurer;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.spi.ConfigurerResolver;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.PropertyConfigurerHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * camel-jackson (Jackson 2.x) and camel-jackson3 (Jackson 3.x) register the same configurer name, jackson-dataformat.
 * With both on the classpath, as when a Kamelet adds camel-jackson to an application with camel-jackson3, Camel creates
 * the Jackson 3.x data format for the name jackson, while the configurer found by that name is the one of the JAR first
 * on the classpath (CAMEL-27164). This test puts camel-jackson first: the options of the data format must still be set
 * with the configurer of the Jackson 3.x data format.
 */
public class KameletJacksonBothLinesTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setClassResolver(new Jackson2ConfigurersFirstClassResolver(context));
        // a data format configured on the context (as with camel.dataformat.jackson.* in Camel Main), whose options
        // the data formats in the routes take
        JacksonDataFormat configured = (JacksonDataFormat) context.resolveDataFormat("jackson");
        configured.setPrettyPrint(true);
        return context;
    }

    @Test
    public void theConfigurerIsTheOneOfTheDataFormat() {
        assertThat(PluginHelper.getConfigurerResolver(context).resolvePropertyConfigurer("jackson-dataformat", context))
                .as("the configurer of camel-jackson is found first by name")
                .isInstanceOf(org.apache.camel.component.jackson.JacksonDataFormatConfigurer.class);

        JacksonDataFormat dataFormat = new JacksonDataFormat();
        assertThat(PropertyConfigurerHelper.resolvePropertyConfigurerByName(context, "jackson-dataformat", dataFormat))
                .isInstanceOf(JacksonDataFormatConfigurer.class);
        assertThat(PropertyConfigurerHelper.resolvePropertyConfigurer(context, dataFormat))
                .isInstanceOf(JacksonDataFormatConfigurer.class);
    }

    @Test
    public void unmarshalType() {
        Object body = template.requestBody("direct:unmarshal", "{\"name\":\"Camel\"}");

        assertThat(body).isInstanceOfSatisfying(Greeting.class, g -> assertThat(g.getName()).isEqualTo("Camel"));
    }

    @Test
    public void optionsOfTheDataFormatConfiguredOnTheContext() {
        Greeting greeting = new Greeting();
        greeting.setName("Camel");

        String json = template.requestBody("direct:marshal", greeting, String.class);

        assertThat(json).isEqualTo("{%s  \"name\" : \"Camel\"%s}", System.lineSeparator(), System.lineSeparator());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("json-to-greeting")
                        .from("kamelet:source")
                        .unmarshal().json(JsonLibrary.Jackson, Greeting.class);

                routeTemplate("greeting-to-json")
                        .from("kamelet:source")
                        .marshal().json(JsonLibrary.Jackson);

                from("direct:unmarshal").kamelet("json-to-greeting");
                from("direct:marshal").kamelet("greeting-to-json");
            }
        };
    }

    public static class Greeting {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    /**
     * Finds the configurers that camel-jackson registers before those of camel-jackson3, as when the JAR of
     * camel-jackson comes first on the classpath.
     */
    private static final class Jackson2ConfigurersFirstClassResolver extends DefaultClassResolver {

        private Jackson2ConfigurersFirstClassResolver(CamelContext camelContext) {
            super(camelContext);
        }

        @Override
        public InputStream loadResourceAsStream(String uri) {
            if (uri.startsWith(ConfigurerResolver.RESOURCE_PATH)) {
                URL url = findInJackson2(uri);
                if (url != null) {
                    try {
                        return url.openStream();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }
            return super.loadResourceAsStream(uri);
        }

        private static URL findInJackson2(String uri) {
            String jar = org.apache.camel.component.jackson.JacksonDataFormat.class.getProtectionDomain().getCodeSource()
                    .getLocation().toString();
            try {
                Enumeration<URL> urls = Jackson2ConfigurersFirstClassResolver.class.getClassLoader().getResources(uri);
                while (urls.hasMoreElements()) {
                    URL url = urls.nextElement();
                    if (url.toString().contains(jar)) {
                        return url;
                    }
                }
                return null;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
