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
package org.apache.camel.model.rest.springboot;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.seda.SedaEndpoint;
import org.apache.camel.impl.DefaultUuidGenerator;
import org.apache.camel.spi.RestApiConsumerFactory;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.util.CamelContextHelper;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

@DirtiesContext
@RunWith(SpringRunner.class)
@EnableAutoConfiguration
@SpringBootTest(
    classes = {
        CamelRestTest.class,
        CamelRestTest.TestConfiguration.class
    },
    properties = {
        "debug=false",
        "camel.springboot.xml-rests=false",
        "camel.springboot.xml-routes=false",
        "camel.rest.enabled=true",
        "camel.rest.component=dummy-rest",
        "camel.rest.host=localhost",
        "camel.rest.data-format-property.prettyPrint=true",
        "camel.rest.api-property.api.title=My cool API",
        "camel.rest.api-property.api.version=1.0.0",
        "camel.rest.api-property.cors=true",
        "camel.rest.cors-headers.foo=123",
        "camel.rest.cors-headers.bar=456"
    }
)
public class CamelRestTest {

    @Autowired
    private CamelContext context;

    @Test
    public void test() throws Exception {
        ProducerTemplate template = context.createProducerTemplate();
        String result = template.requestBody("seda:get-say-hello", "test", String.class);

        Assert.assertEquals("Hello World", result);

        Assert.assertEquals("true", context.getRestConfiguration().getDataFormatProperties().get("prettyPrint"));
        Assert.assertEquals("My cool API", context.getRestConfiguration().getApiProperties().get("api.title"));
        Assert.assertEquals("1.0.0", context.getRestConfiguration().getApiProperties().get("api.version"));
        Assert.assertEquals("true", context.getRestConfiguration().getApiProperties().get("cors"));
        Assert.assertEquals("123", context.getRestConfiguration().getCorsHeaders().get("foo"));
        Assert.assertEquals("456", context.getRestConfiguration().getCorsHeaders().get("bar"));
    }

    // ***********************************
    // Configuration
    // ***********************************

    @Configuration
    public static class TestConfiguration {
        @Bean(name = "dummy-rest")
        public RestConsumerFactory dummyRestConsumerFactory() {
            return new TestConsumerFactory();
        }

        @Bean
        public RouteBuilder routeBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    rest("/say/hello")
                        .get().to("direct:hello");
                    from("direct:hello")
                        .transform().constant("Hello World");
                }
            };
        }
    }

    // ***********************************
    // Rest Helpers
    // ***********************************

    private static final class TestConsumerFactory implements RestConsumerFactory, RestApiConsumerFactory {
        private Object dummy;

        public Object getDummy() {
            return dummy;
        }

        public void setDummy(Object dummy) {
            this.dummy = dummy;
        }

        @Override
        public Consumer createConsumer(
            CamelContext camelContext,
            Processor processor,
            String verb,
            String basePath,
            String uriTemplate,
            String consumes,
            String produces,
            RestConfiguration configuration,
            Map<String, Object> parameters) throws Exception {

            // just use a seda endpoint for testing purpose
            String id;
            if (uriTemplate != null) {
                id = DefaultUuidGenerator.generateSanitizedId(basePath + uriTemplate);
            } else {
                id = DefaultUuidGenerator.generateSanitizedId(basePath);
            }
            // remove leading dash as we add that ourselves
            if (id.startsWith("-")) {
                id = id.substring(1);
            }

            if (configuration.getConsumerProperties() != null) {
                String ref = (String) configuration.getConsumerProperties().get("dummy");
                if (ref != null) {
                    dummy = CamelContextHelper.mandatoryLookup(camelContext, ref.substring(1));
                }
            }

            SedaEndpoint seda = camelContext.getEndpoint("seda:" + verb + "-" + id, SedaEndpoint.class);
            return seda.createConsumer(processor);
        }

        @Override
        public Consumer createApiConsumer(
            CamelContext camelContext,
            Processor processor,
            String contextPath,
            RestConfiguration configuration,
            Map<String, Object> parameters) throws Exception {

            // just use a seda endpoint for testing purpose
            String id = DefaultUuidGenerator.generateSanitizedId(contextPath);
            // remove leading dash as we add that ourselves
            if (id.startsWith("-")) {
                id = id.substring(1);
            }

            SedaEndpoint seda = camelContext.getEndpoint("seda:api:" + "-" + id, SedaEndpoint.class);
            return seda.createConsumer(processor);
        }
    }
}
