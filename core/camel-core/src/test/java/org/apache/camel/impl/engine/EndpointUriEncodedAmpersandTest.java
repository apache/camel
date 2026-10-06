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
package org.apache.camel.impl.engine;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.log.LogEndpoint;
import org.apache.camel.support.NormalizedUri;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An encoded & (%26) in an endpoint uri is part of the option value, not a separator between options.
 */
public class EndpointUriEncodedAmpersandTest extends ContextTestSupport {

    @Test
    public void testEncodedAmpersandInValue() {
        LogEndpoint endpoint = context.getEndpoint("log:foo?marker=Tom%26Jerry", LogEndpoint.class);
        assertEquals("Tom&Jerry", endpoint.getMarker());

        // the same endpoint is found again, by the uri and by the endpoint uri
        assertSame(endpoint, context.getEndpoint("log:foo?marker=Tom%26Jerry"));
        assertSame(endpoint, context.getEndpoint(endpoint.getEndpointUri()));
    }

    @Test
    public void testEncodedAmpersandWithOtherOptions() {
        LogEndpoint endpoint = context.getEndpoint("log:bar?showAll=true&marker=Tom%26Jerry", LogEndpoint.class);
        assertEquals("Tom&Jerry", endpoint.getMarker());
        assertTrue(endpoint.isShowAll());
    }

    @Test
    public void testEncodedAmpersandInNormalizedUri() {
        // the Endpoint DSL encodes an option value with & as %26 and resolves the uri as already normalized
        LogEndpoint endpoint = (LogEndpoint) context.getCamelContextExtension()
                .getEndpoint(NormalizedUri.newNormalizedUri("log://baz?marker=Tom%26Jerry", true));
        assertEquals("Tom&Jerry", endpoint.getMarker());
    }
}
