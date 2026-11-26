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
package org.apache.camel.support.component;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.component.file.FileEndpoint;
import org.apache.camel.component.timer.TimerEndpoint;
import org.apache.camel.spi.EndpointUriFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EndpointOptionEdgeCasesTest extends ContextTestSupport {

    // an uri factory for a syntax where the name of a path parameter is also a part of another name
    private static class MyEndpointUriFactory extends EndpointUriFactorySupport {

        @Override
        public boolean isEnabled(String scheme) {
            return "my".equals(scheme);
        }

        @Override
        public String buildUri(String scheme, Map<String, Object> properties, boolean encode) throws URISyntaxException {
            String syntax = "my:type/typeId";
            String uri = syntax;
            Map<String, Object> copy = new HashMap<>(properties);
            uri = buildPathParameter(syntax, uri, "type", null, true, copy);
            uri = buildPathParameter(syntax, uri, "typeId", null, false, copy);
            return buildQueryParameters(uri, copy, encode);
        }

        @Override
        public Set<String> propertyNames() {
            return Set.of("type", "typeId");
        }

        @Override
        public Set<String> secretPropertyNames() {
            return Set.of();
        }

        @Override
        public Map<String, String> multiValuePrefixes() {
            return Map.of();
        }

        @Override
        public boolean isLenientProperties() {
            return false;
        }
    }

    @Test
    public void testBuildPathParameterWhenNameIsPartOfAnotherName() throws Exception {
        MyEndpointUriFactory factory = new MyEndpointUriFactory();
        factory.setCamelContext(context);

        assertEquals("my:channel/foo", factory.buildUri("my", Map.of("type", "channel", "typeId", "foo")));
        assertEquals("my:channel", factory.buildUri("my", Map.of("type", "channel")));
        assertEquals("my:C:\\dir/p$1", factory.buildUri("my", Map.of("type", "C:\\dir", "typeId", "p$1")));
    }

    @Test
    public void testOptionalPlaceholderInPathParameter() throws Exception {
        EndpointUriFactory factory = context.getCamelContextExtension().getEndpointUriFactory("file");

        // the ? of an optional placeholder in the path is not the start of the query (CAMEL-27361)
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("directoryName", "target/data/{{?subDir}}");
        map.put("noop", "true");
        map.put("antInclude", "*.txt");
        String uri = factory.buildUri("file", map, false);
        assertEquals("file:target/data/{{?subDir}}?noop=true&antInclude=*.txt", uri);

        // and the options are not lost when the placeholder is resolved
        FileEndpoint endpoint = context.getEndpoint(uri, FileEndpoint.class);
        assertEquals("target/data", endpoint.getConfiguration().getDirectory());
        assertTrue(endpoint.isNoop());
        assertEquals("*.txt", endpoint.getAntInclude());
    }

    @Test
    public void testQuestionMarkInPathParameter() throws Exception {
        MyEndpointUriFactory factory = new MyEndpointUriFactory();
        factory.setCamelContext(context);

        // a ? in the path (not in a placeholder) still makes the query start with &
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "channel?x=1");
        map.put("foo", "bar");
        assertEquals("my:channel?x=1&foo=bar", factory.buildUri("my", map, false));

        map.put("type", "{{?channel}}");
        assertEquals("my:{{?channel}}?foo=bar", factory.buildUri("my", map, false));
    }

    @Test
    public void testTimePatternOption() {
        TimerEndpoint endpoint = context.getEndpoint("timer:a?period=5S", TimerEndpoint.class);
        assertEquals(5000, endpoint.getPeriod());

        endpoint = context.getEndpoint("timer:b?period=-5s", TimerEndpoint.class);
        assertEquals(-5000, endpoint.getPeriod());
    }

    @Test
    public void testInvalidTimePatternOption() {
        assertThrows(ResolveEndpointFailedException.class, () -> context.getEndpoint("timer:c?period=five"));
        assertThrows(ResolveEndpointFailedException.class, () -> context.getEndpoint("timer:d?period=1m30"));
    }

    @Test
    public void testTimePatternTooLargeForIntOption() {
        assertEquals(86400000, PropertyConfigurerSupport.property(context, int.class, "1d"));

        // 25 days is too large for an int, so it must not overflow to a negative value
        assertThrows(Exception.class, () -> PropertyConfigurerSupport.property(context, int.class, "25d"));
    }
}
