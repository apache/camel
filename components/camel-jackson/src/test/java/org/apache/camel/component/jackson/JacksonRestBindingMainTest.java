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
package org.apache.camel.component.jackson;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.main.Main;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The camel.dataformat.jackson.* options of Camel Main apply to the data formats of the REST DSL binding.
 */
public class JacksonRestBindingMainTest {

    private Main main;

    @AfterEach
    void stopMain() {
        if (main != null) {
            main.stop();
        }
    }

    @Test
    void restBindingUsesTheJacksonOptions() throws Exception {
        CamelContext context = startMain(Map.of(
                "camel.dataformat.jackson.include", "NON_NULL",
                "camel.dataformat.jackson.disable-features", "FAIL_ON_UNKNOWN_PROPERTIES"));

        try (ProducerTemplate template = context.createProducerTemplate()) {
            assertEquals("{\"name\":\"Camel\"}", template.requestBody("direct:get-pojo", null, String.class));
            assertEquals("{\"name\":\"Camel\"}",
                    template.requestBody("direct:post-pojo", "{\"name\":\"Camel\",\"age\":20}", String.class));
        }
    }

    @Test
    void restDataFormatPropertiesComeFirst() throws Exception {
        CamelContext context = startMain(Map.of(
                "camel.dataformat.jackson.include", "NON_NULL",
                "camel.rest.data-format-properties[json.out.include]", "ALWAYS"));

        try (ProducerTemplate template = context.createProducerTemplate()) {
            assertEquals("{\"name\":\"Camel\",\"country\":null}",
                    template.requestBody("direct:get-pojo", null, String.class));
        }
    }

    @Test
    void restBindingDoesNotTakeTheUnmarshalTypeHeader() throws Exception {
        CamelContext context = startMain(Map.of("camel.dataformat.jackson.allow-unmarshall-type", "true"));

        MockEndpoint posted = context.getEndpoint("mock:posted", MockEndpoint.class);
        posted.expectedMessageCount(1);
        try (ProducerTemplate template = context.createProducerTemplate()) {
            template.requestBodyAndHeader("direct:post-pojo", "{\"name\":\"Camel\"}", JacksonConstants.UNMARSHAL_TYPE,
                    Map.class.getName());
        }
        posted.assertIsSatisfied();
        assertInstanceOf(TestOtherPojo.class, posted.getReceivedExchanges().get(0).getMessage().getBody());
    }

    @Test
    void restBindingDoesNotTakeTheBodyTypeOptions() throws Exception {
        CamelContext context = startMain(Map.of(
                "camel.dataformat.jackson.unmarshal-type", TestOtherPojo.class.getName(),
                "camel.dataformat.jackson.use-list", "true",
                "camel.dataformat.jackson.collection-type", "java.util.ArrayList"));

        MockEndpoint put = context.getEndpoint("mock:put", MockEndpoint.class);
        put.expectedMessageCount(1);
        try (ProducerTemplate template = context.createProducerTemplate()) {
            template.requestBody("direct:put-pojo", "{\"name\":\"Camel\"}");
        }
        put.assertIsSatisfied();
        assertInstanceOf(Map.class, put.getReceivedExchanges().get(0).getMessage().getBody());
    }

    private CamelContext startMain(Map<String, String> properties) {
        main = new Main();
        main.bind("direct-rest", new DirectRestConsumerFactory());
        properties.forEach(main::addInitialProperty);
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().component("direct-rest").bindingMode(RestBindingMode.json);

                rest("/pojo")
                        .get().outType(TestOtherPojo.class).to("direct:pojo")
                        .post().type(TestOtherPojo.class).outType(TestOtherPojo.class).to("mock:posted")
                        .put().to("mock:put");

                from("direct:pojo").process(e -> {
                    TestOtherPojo pojo = new TestOtherPojo();
                    pojo.setName("Camel");
                    e.getMessage().setBody(pojo);
                });
            }
        });
        main.start();
        return main.getCamelContext();
    }

    /**
     * Runs the REST services on direct endpoints, named after the verb and the path.
     */
    private static final class DirectRestConsumerFactory implements RestConsumerFactory {

        @Override
        public Consumer createConsumer(
                CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate,
                String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters)
                throws Exception {
            String uri = "direct:" + verb + basePath.replace('/', '-');
            return camelContext.getEndpoint(uri).createConsumer(processor);
        }
    }
}
