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
package org.apache.camel.component.snakeyaml;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SnakeYAMLDoSTest extends CamelTestSupport {

    @Test
    public void testReadingDataFromFile() throws Exception {

        MockEndpoint mock = context.getEndpoint("mock:reverse", MockEndpoint.class);
        assertNotNull(mock);
        mock.expectedMessageCount(1);

        try (InputStream is = this.getClass().getClassLoader().getResourceAsStream("data.yaml")) {

            ProducerTemplate template = context.createProducerTemplate();
            String result = assertDoesNotThrow(() -> template.requestBody("direct:back", is, String.class));
            assertNotNull(result);
            assertEquals("{name=Colm, location=Dublin}", result.trim());

            mock.assertIsSatisfied();
        }
    }

    @Test
    public void testAliasExpansion() throws Exception {

        MockEndpoint mock = context.getEndpoint("mock:reverse", MockEndpoint.class);
        assertNotNull(mock);
        mock.expectedMessageCount(0);

        try (InputStream is = this.getClass().getClassLoader().getResourceAsStream("data-dos.yaml")) {

            ProducerTemplate template = context.createProducerTemplate();

            Exception ex = assertThrows(CamelExecutionException.class,
                    () -> template.requestBody("direct:back", is, String.class),
                    "Failure expected on an alias expansion attack");

            Throwable cause = ex.getCause();
            assertEquals("Number of aliases for non-scalar nodes exceeds the specified max=50", cause.getMessage());

            mock.assertIsSatisfied();
        }
    }

    @Test
    public void testReferencesWithRecursiveKeysNotAllowedByDefault() throws Exception {

        MockEndpoint mock = context.getEndpoint("mock:reverse2", MockEndpoint.class);
        assertNotNull(mock);
        mock.expectedMessageCount(0);

        ProducerTemplate template = context.createProducerTemplate();
        String dump = createDump(30);

        Exception ex = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:back2", dump, String.class),
                "Failure expected on an alias expansion attack");

        Throwable cause = ex.getCause();
        assertEquals("Recursive key for mapping is detected but it is not configured to be allowed.",
                cause.getMessage());

        mock.assertIsSatisfied();
    }

    // Taken from SnakeYaml test code
    private String createDump(int size) {
        Map<String, Object> root = new HashMap<>();
        Map<String, Object> s1;
        Map<String, Object> s2;
        Map<String, Object> t1;
        Map<String, Object> t2;
        s1 = root;
        s2 = new HashMap<>();
        /*
        the time to parse grows very quickly
        SIZE -> time to parse in seconds
        25 -> 1
        26 -> 2
        27 -> 3
        28 -> 8
        29 -> 13
        30 -> 28
        31 -> 52
        32 -> 113
        33 -> 245
        34 -> 500
         */
        for (int i = 0; i < size; i++) {

            t1 = new HashMap<>();
            t2 = new HashMap<>();
            t1.put("foo", "1");
            t2.put("bar", "2");

            s1.put("a", t1);
            s1.put("b", t2);
            s2.put("a", t1);
            s2.put("b", t2);

            s1 = t1;
            s2 = t2;
        }

        // this is VERY BAD code
        // the map has itself as a key (no idea why it may be used except of a DoS attack)
        Map<Object, Object> f = new HashMap<>();
        f.put(f, "a");
        f.put("g", root);

        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        return yaml.dump(f);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        SnakeYAMLDataFormat dataFormat = new SnakeYAMLDataFormat();
        dataFormat.setMaxAliasesForCollections(150);

        return new RouteBuilder() {

            @Override
            public void configure() {
                from("direct:back")
                        .unmarshal(new SnakeYAMLDataFormat())
                        .to("mock:reverse");

                from("direct:back2")
                        .unmarshal(dataFormat)
                        .to("mock:reverse2");
            }
        };
    }
}
