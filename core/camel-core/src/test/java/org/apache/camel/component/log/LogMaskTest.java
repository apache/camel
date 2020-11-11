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
package org.apache.camel.component.log;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.MaskingFormatter;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class LogMaskTest {

    protected Registry registry;

    protected CamelContext createCamelContext() throws Exception {
        registry = new DefaultRegistry();
        CamelContext context = new DefaultCamelContext(registry);
        return context;
    }

    @Test
    public void testLogMask() throws Exception {
        CamelContext context = createCamelContext();
        context.setLogMask(true);
        context.start();
        ProducerTemplate template = context.createProducerTemplate();
        template.sendBodyAndHeader("log:mask?showHeaders=true", "password=passw0rd@", "headerPassword", "#header-password$");
        template.sendBodyAndProperty("log:mask?showProperties=true", "password=passw0rd@", "propertyPassphrase",
                "#property-passphrase$");
        context.stop();
    }

    @Test
    public void testDisableLogMaskViaParam() throws Exception {
        CamelContext context = createCamelContext();
        context.setLogMask(true);
        context.start();
        ProducerTemplate template = context.createProducerTemplate();
        template.sendBodyAndHeader("log:mask?showHeaders=true", "password=passw0rd@", "headerPassword", "#header-password$");
        template.sendBodyAndProperty("log:no-mask?showProperties=true&logMask=false", "password=passw0rd@",
                "propertyPassphrase", "#property-passphrase$");
        context.stop();
    }

    @Test
    public void testCustomFormatter() throws Exception {
        CamelContext context = createCamelContext();
        MockMaskingFormatter customFormatter = new MockMaskingFormatter();
        registry.bind(MaskingFormatter.CUSTOM_LOG_MASK_REF, customFormatter);
        context.start();
        ProducerTemplate template = context.createProducerTemplate();
        template.sendBody("log:mock?logMask=true", "password=passw0rd@");
        context.stop();
        assertTrue(customFormatter.received.contains("password=passw0rd@"), customFormatter.received);
    }

    public static class MockMaskingFormatter implements MaskingFormatter {
        private String received;

        @Override
        public String format(String source) {
            received = source;
            return source;
        }
    }

}
