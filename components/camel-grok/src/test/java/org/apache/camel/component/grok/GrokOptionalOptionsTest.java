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
package org.apache.camel.component.grok;

import java.util.List;
import java.util.Map;

import io.krakens.grok.api.exception.GrokException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GrokOptionalOptionsTest extends CamelTestSupport {
    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                DataFormat grokFlattenedTrue = new GrokDataFormat("%{INT:i} %{INT:i}")
                        .setFlattened(true);
                DataFormat grokFlattenedFalse = new GrokDataFormat("%{INT:i} %{INT:i}")
                        .setFlattened(false);

                DataFormat grokNamedOnlyTrue = new GrokDataFormat("%{URI:website}")
                        .setNamedOnly(true);
                DataFormat grokNamedOnlyFalse = new GrokDataFormat("%{URI:website}")
                        .setNamedOnly(false);

                DataFormat grokAllowMultipleMatchesPerLineTrue = new GrokDataFormat("%{INT:i}")
                        .setAllowMultipleMatchesPerLine(true);
                DataFormat grokAllowMultipleMatchesPerLineFalse = new GrokDataFormat("%{INT:i}")
                        .setAllowMultipleMatchesPerLine(false);

                from("direct:flattenedTrue").unmarshal(grokFlattenedTrue);
                from("direct:flattenedFalse").unmarshal(grokFlattenedFalse);
                from("direct:namedOnlyTrue").unmarshal(grokNamedOnlyTrue);
                from("direct:namedOnlyFalse").unmarshal(grokNamedOnlyFalse);
                from("direct:allowMultipleMatchesPerLineTrue").unmarshal(grokAllowMultipleMatchesPerLineTrue);
                from("direct:allowMultipleMatchesPerLineFalse").unmarshal(grokAllowMultipleMatchesPerLineFalse);

            }
        };
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFlattened() {
        Map<String, Object> flattenedFalse = template.requestBody("direct:flattenedFalse", "123 456", Map.class);
        assertNotNull(flattenedFalse);
        assertTrue(flattenedFalse.containsKey("i"));
        assertTrue(flattenedFalse.get("i") instanceof List);
        assertEquals("123", ((List) flattenedFalse.get("i")).get(0));
        assertEquals("456", ((List) flattenedFalse.get("i")).get(1));

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:flattenedTrue", "1 2"));
        assertIsInstanceOf(GrokException.class, e.getCause());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNamedOnly() {
        Map<String, Object> namedOnlyTrue
                = template.requestBody("direct:namedOnlyTrue", "https://github.com/apache/camel", Map.class);
        assertNotNull(namedOnlyTrue);
        assertEquals("https://github.com/apache/camel", namedOnlyTrue.get("website"));
        assertFalse(namedOnlyTrue.containsKey("URIPROTO"));
        assertFalse(namedOnlyTrue.containsKey("URIHOST"));
        assertFalse(namedOnlyTrue.containsKey("URIPATHPARAM"));

        Map<String, Object> namedOnlyFalse
                = template.requestBody("direct:namedOnlyFalse", "https://github.com/apache/camel", Map.class);
        assertNotNull(namedOnlyFalse);
        assertEquals("https://github.com/apache/camel", namedOnlyFalse.get("website"));
        assertEquals("https", namedOnlyFalse.get("URIPROTO"));
        assertEquals("github.com", namedOnlyFalse.get("URIHOST"));
        assertEquals("/apache/camel", namedOnlyFalse.get("URIPATHPARAM"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testAllowMultipleMatchesPerLine() {
        List<Map<String, Object>> allowMultipleMatchesPerLineTrue = template.requestBody(
                "direct:allowMultipleMatchesPerLineTrue",
                "1 2 \n 3",
                List.class);
        assertNotNull(allowMultipleMatchesPerLineTrue);
        assertEquals(3, allowMultipleMatchesPerLineTrue.size());
        assertEquals("1", allowMultipleMatchesPerLineTrue.get(0).get("i"));
        assertEquals("2", allowMultipleMatchesPerLineTrue.get(1).get("i"));
        assertEquals("3", allowMultipleMatchesPerLineTrue.get(2).get("i"));

        List<Map<String, Object>> allowMultipleMatchesPerLineFalse = template.requestBody(
                "direct:allowMultipleMatchesPerLineFalse",
                "1 2 \n 3",
                List.class);
        assertNotNull(allowMultipleMatchesPerLineFalse);
        assertEquals(2, allowMultipleMatchesPerLineFalse.size());
        assertEquals("1", allowMultipleMatchesPerLineFalse.get(0).get("i"));
        assertEquals("3", allowMultipleMatchesPerLineFalse.get(1).get("i"));

    }
}
