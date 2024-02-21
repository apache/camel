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
package org.apache.camel.language.groovy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import groovy.lang.GroovyShell;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.test.junit5.TestSupport;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GroovyShellFactoryTest extends CamelTestSupport {

    @Test
    public void testGroovyShellFactoryWithoutFilename() {
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("groovyShellFactory", (GroovyShellFactory) exchange -> {
            ImportCustomizer importCustomizer = new ImportCustomizer();
            importCustomizer.addStaticStars("org.apache.camel.language.groovy.GroovyShellFactoryTest.Utils");
            CompilerConfiguration configuration = new CompilerConfiguration();
            configuration.addCompilationCustomizers(importCustomizer);
            return new GroovyShell(configuration);
        });

        CamelContext camelContext = new DefaultCamelContext(registry);

        TestSupport.assertExpression(GroovyLanguage.groovy("dummy()"), new DefaultExchange(camelContext), "foo");
    }

    @Test
    public void testGroovyShellFactoryFilename() {
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("groovyShellFactory", new GroovyShellFactory() {
            @Override
            public GroovyShell createGroovyShell(Exchange exchange) {
                return new GroovyShell();
            }

            @Override
            public String getFileName(Exchange exchange) {
                return exchange.getIn().getHeader("fileName", String.class);
            }
        });

        CamelContext camelContext = new DefaultCamelContext(registry);

        final DefaultExchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader("fileName", "Test.groovy");

        final Exception e = GroovyLanguage.groovy("new Exception()").evaluate(exchange, Exception.class);
        assertTrue(Arrays.stream(e.getStackTrace())
                .anyMatch(stackTraceElement -> "Test.groovy".equals(stackTraceElement.getFileName())));
    }

    @Test
    public void testGroovyShellFactoryVariables() {
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("groovyShellFactory", new GroovyShellFactory() {
            @Override
            public GroovyShell createGroovyShell(Exchange exchange) {
                return new GroovyShell();
            }

            @Override
            public Map<String, Object> getVariables(Exchange exchange) {
                Map<String, Object> map = new HashMap<>();
                map.put("key", "testValue");
                return map;
            }
        });

        CamelContext camelContext = new DefaultCamelContext(registry);

        final DefaultExchange exchange = new DefaultExchange(camelContext);

        final String res = GroovyLanguage.groovy("key").evaluate(exchange, String.class);
        assertEquals("testValue", res);
    }

    public static class Utils {
        public static String dummy() {
            return "foo";
        }
    }

}
