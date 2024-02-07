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
package org.apache.camel.component.splunkhec;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SplunkHECComponentTest {
    SplunkHECComponent component;

    @BeforeEach
    void setUp() {
        component = Mockito.spy(new SplunkHECComponent());
        component.setCamelContext(new DefaultCamelContext());
        Mockito.when(component.getEndpointPropertyConfigurer()).thenReturn(new SplunkHECEndpointConfigurer());
    }

    @Test
    public void testInvalidEndpoint() {
        assertThrows(Exception.class, () -> component.createEndpoint(""));
    }

    @Test
    public void testInvalidUriSyntax() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> component.createEndpoint(
                "splunk-hec:bad/path"));
        assertEquals("Invalid URI: splunk-hec:bad/path", e.getMessage());
    }

    @Test
    public void testInvalidHostname() throws Exception {
        Endpoint endpoint = component.createEndpoint(
                "splunk-hec:yo,lo:1234?token=11111111-1111-1111-1111-111111111111");
        Exception e = assertThrows(IllegalArgumentException.class, endpoint::init);
        assertEquals("Invalid hostname: yo,lo", e.getMessage());
    }

    @Test
    public void testIpAddressValid() throws Exception {
        SplunkHECEndpoint endpoint = (SplunkHECEndpoint) component.createEndpoint(
                "splunk-hec:192.168.0.1:18808?token=11111111-1111-1111-1111-111111111111");
        endpoint.init();
        assertEquals("192.168.0.1:18808", endpoint.getSplunkURL());
        assertEquals("11111111-1111-1111-1111-111111111111", endpoint.getConfiguration().getToken());
    }

    @Test
    public void testLocalHostValid() throws Exception {
        SplunkHECEndpoint endpoint = (SplunkHECEndpoint) component.createEndpoint(
                "splunk-hec:localhost:18808?token=11111111-1111-1111-1111-111111111111");
        endpoint.init();
        assertEquals("localhost:18808", endpoint.getSplunkURL());
        assertEquals("11111111-1111-1111-1111-111111111111", endpoint.getConfiguration().getToken());
    }

    @Test
    public void testFQHNValid() throws Exception {
        SplunkHECEndpoint endpoint = (SplunkHECEndpoint) component.createEndpoint(
                "splunk-hec:http-input.splunkcloud.com:18808?token=11111111-1111-1111-1111-111111111111");
        endpoint.init();
        assertEquals("http-input.splunkcloud.com:18808", endpoint.getSplunkURL());
        assertEquals("11111111-1111-1111-1111-111111111111", endpoint.getConfiguration().getToken());
    }

    @Test
    public void testValidWithOptions() throws Exception {
        SplunkHECEndpoint endpoint = (SplunkHECEndpoint) component.createEndpoint(
                "splunk-hec:localhost:18808?token=11111111-1111-1111-1111-111111111111&index=foo");
        endpoint.init();
        assertEquals("localhost:18808", endpoint.getSplunkURL());
        assertEquals("11111111-1111-1111-1111-111111111111", endpoint.getConfiguration().getToken());
        assertEquals("foo", endpoint.getConfiguration().getIndex());
    }

    @Test
    public void testInvalidPort() throws Exception {
        Endpoint endpoint = component.createEndpoint(
                "splunk-hec:yolo:188508?token=11111111-1111-1111-1111-111111111111");
        Exception e = assertThrows(IllegalArgumentException.class, endpoint::init);
        assertEquals("Invalid port: 188508", e.getMessage());
    }

    @Test
    public void testSplunkURLIsNotOverriddenByQuery() throws Exception {
        SplunkHECEndpoint endpoint = (SplunkHECEndpoint) component.createEndpoint(
                "splunk-hec:192.168.0.1:18808?token=11111111-1111-1111-1111-111111111111&splunkURL=ignored");
        assertEquals("192.168.0.1:18808", endpoint.getSplunkURL());
        endpoint.init();
        assertEquals("192.168.0.1:18808", endpoint.getSplunkURL());
    }

    @Test
    public void testMissingToken() throws Exception {
        Endpoint endpoint = component.createEndpoint("splunk-hec:localhost:18808");
        Exception e = assertThrows(IllegalArgumentException.class, endpoint::init);
        assertEquals("A token must be defined", e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "bad-token" })
    public void testInvalidToken(String token) throws Exception {
        Endpoint endpoint = component.createEndpoint(
                "splunk-hec:localhost:18808?token=" + token);
        Exception e = assertThrows(IllegalArgumentException.class, endpoint::init);
        assertEquals("Invalid Splunk HEC token provided", e.getMessage());
    }

    @Test
    public void testTokenValid() throws Exception {
        SplunkHECEndpoint endpoint = (SplunkHECEndpoint) component.createEndpoint(
                "splunk-hec:localhost:18808?token=11111111-1111-1111-1111-111111111111");
        endpoint.init();
        assertEquals("11111111-1111-1111-1111-111111111111", endpoint.getConfiguration().getToken());
    }

    @Test
    public void testSanitizedException() {
        String tokenValue = "token-value";
        Exception e = assertThrows(IllegalArgumentException.class, () -> component.createEndpoint(
                "splunk-hec:bad/path?token=" + tokenValue));
        assertTrue(e.getMessage().contains("token=xxxxxx"));
        assertFalse(e.getMessage().contains(tokenValue));
    }
}
