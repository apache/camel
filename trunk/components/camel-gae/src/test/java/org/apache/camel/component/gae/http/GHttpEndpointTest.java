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
package org.apache.camel.component.gae.http;

import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.component.gae.http.GHttpTestUtils.createEndpoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GHttpEndpointTest {

    private static final String AMP = "&";
    
    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testGetEndpointUrl() throws Exception {
        GHttpEndpoint endpoint1 = createEndpoint("ghttp://somewhere.com/path?a=b");
        GHttpEndpoint endpoint2 = createEndpoint("ghttps://somewhere.com/path?a=b");
        GHttpEndpoint endpoint3 = createEndpoint("xhttps://somewhere.com/path?a=b");
        
        assertEquals("http://somewhere.com/path?a=b", endpoint1.getEndpointUrl().toString());
        assertEquals("https://somewhere.com/path?a=b", endpoint2.getEndpointUrl().toString());
        assertEquals("http://somewhere.com/path?a=b", endpoint3.getEndpointUrl().toString());
    }
    
    @Test
    public void testPropertiesDefault() throws Exception {
        GHttpEndpoint endpoint = createEndpoint("ghttp://somewhere.com/path?a=b");
        assertTrue(endpoint.getOutboundBinding().getClass().equals(GHttpBinding.class));
    }
    
    @Test
    public void testPropertiesCustom() throws Exception {
        StringBuilder buffer = new StringBuilder("ghttp://somewhere.com/path")
            .append("?").append("a=b")
            .append(AMP).append("bridgeEndpoint=false")
            .append(AMP).append("outboundBindingRef=#customBinding")
            .append(AMP).append("throwExceptionOnFailure=false");
        GHttpEndpoint endpoint = createEndpoint(buffer.toString());
        assertFalse(endpoint.getOutboundBinding().getClass().equals(GHttpBinding.class));
        assertTrue(endpoint.getOutboundBinding() instanceof GHttpBinding);
        assertEquals("ghttp://somewhere.com/path?a=b", endpoint.getEndpointUri());
    }
    
}
