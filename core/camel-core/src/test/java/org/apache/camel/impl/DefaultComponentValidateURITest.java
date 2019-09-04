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
package org.apache.camel.impl;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.ResolveEndpointFailedException;
import org.junit.Test;

/**
 * Unit test for URI validation when creating an endpoint
 */
public class DefaultComponentValidateURITest extends ContextTestSupport {

    @Test
    public void testNoParameters() throws Exception {
        Endpoint endpoint = context.getEndpoint("timer://foo");
        assertNotNull("Should have created an endpoint", endpoint);
    }

    @Test
    public void testUnknownParameter() throws Exception {
        try {
            context.getEndpoint("timer://foo?delay=250&unknown=1&period=500");
            fail("Should have thrown ResolveEndpointFailedException");
        } catch (ResolveEndpointFailedException e) {
            // ok
        }
    }

    @Test
    public void testDoubleAmpersand() throws Exception {
        try {
            context.getEndpoint("timer://foo?delay=250&&period=500");
            fail("Should have thrown ResolveEndpointFailedException");
        } catch (ResolveEndpointFailedException e) {
            // ok
        }
    }

    @Test
    public void testTrailingAmpersand() throws Exception {
        try {
            context.getEndpoint("timer://foo?delay=250&period=500&");
            fail("Should have thrown ResolveEndpointFailedException");
        } catch (ResolveEndpointFailedException e) {
            // ok
        }
    }

    @Test
    public void testScheduledPollConsumerOptions() throws Exception {
        // test that we support both notations of scheduled polling consumer options

        Endpoint endpint = context.getEndpoint("file://foo2?delay=1000");
        assertNotNull(endpint);

        endpint = context.getEndpoint("file://foo2?delay=1000&initialDelay=5000");
        assertNotNull(endpint);

        endpint = context.getEndpoint("file://foo2?delay=1000&initialDelay=5000&useFixedDelay=false");
        assertNotNull(endpint);
    }

}
