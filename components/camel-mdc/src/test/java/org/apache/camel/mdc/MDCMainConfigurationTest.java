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
package org.apache.camel.mdc;

import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.apache.camel.spi.CamelMDCService;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The camel.mdc.* options of Camel Main are bound on the {@link MDCService}.
 */
public class MDCMainConfigurationTest {

    private final AtomicReference<String> seen = new AtomicReference<>();

    private Main newMain() {
        Main main = new Main();
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("start")
                        .setHeader("foo", constant("bar"))
                        .setProperty("myProp", constant("baz"))
                        .process(e -> seen.set(MDC.get("foo") + "|" + MDC.get("myProp")));
            }
        });
        main.addInitialProperty("camel.mdc.enabled", "true");
        return main;
    }

    private void sendAndAssertMdc(Main main) throws Exception {
        try (ProducerTemplate template = main.getCamelContext().createProducerTemplate()) {
            template.sendBody("direct:start", "Hello");
        }
        assertEquals("bar|baz", seen.get());
    }

    @Test
    void testCustomExchangeHeadersAndProperties() throws Exception {
        Main main = newMain();
        main.addInitialProperty("camel.mdc.customExchangeHeaders", "foo");
        main.addInitialProperty("camel.mdc.customExchangeProperties", "myProp");
        main.start();
        try {
            MDCService mdc = assertInstanceOf(MDCService.class,
                    main.getCamelContext().hasService(CamelMDCService.class));
            assertEquals("foo", mdc.getCustomExchangeHeaders());
            assertEquals("myProp", mdc.getCustomExchangeProperties());
            assertEquals("foo", mdc.getCustomHeaders());
            assertEquals("myProp", mdc.getCustomProperties());
            sendAndAssertMdc(main);
        } finally {
            main.stop();
        }
    }

    @Test
    void testCustomExchangeHeadersAndPropertiesWithWildcard() throws Exception {
        Main main = newMain();
        main.addInitialProperty("camel.mdc.customExchangeHeaders", "f*");
        main.addInitialProperty("camel.mdc.customExchangeProperties", "*");
        main.start();
        try {
            sendAndAssertMdc(main);
        } finally {
            main.stop();
        }
    }

    @Test
    void testCustomHeadersAndProperties() throws Exception {
        Main main = newMain();
        main.addInitialProperty("camel.mdc.customHeaders", "foo");
        main.addInitialProperty("camel.mdc.customProperties", "myProp");
        main.start();
        try {
            sendAndAssertMdc(main);
        } finally {
            main.stop();
        }
    }
}
