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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SplunkHECEndpointTest {
    @Test
    public void testInvalidEndpoint() {
        SplunkHECConfiguration configuration = new SplunkHECConfiguration();
        SplunkHECComponent component = new SplunkHECComponent();
        assertThrows(IllegalArgumentException.class, () -> new SplunkHECEndpoint("", component, configuration));
    }

    @Test
    public void testInvalidURL() {
        SplunkHECConfiguration configuration = new SplunkHECConfiguration();
        SplunkHECComponent component = new SplunkHECComponent();
        assertThrows(IllegalArgumentException.class, () -> new SplunkHECEndpoint(
                "splunk-hec:yo,lo:1234/11111111-1111-1111-1111-111111111111", component, configuration));
    }

    @Test
    public void testIpAddressValid() {
        SplunkHECConfiguration configuration = new SplunkHECConfiguration();
        SplunkHECComponent component = new SplunkHECComponent();
        SplunkHECEndpoint endpoint = new SplunkHECEndpoint(
                "splunk-hec:192.168.0.1:18808/11111111-1111-1111-1111-111111111111", component, configuration);
        assertEquals("192.168.0.1:18808", endpoint.getSplunkURL());
        assertEquals("11111111-1111-1111-1111-111111111111", endpoint.getToken());
    }

    @Test
    public void testLocalHostValid() {
        SplunkHECConfiguration configuration = new SplunkHECConfiguration();
        SplunkHECComponent component = new SplunkHECComponent();
        SplunkHECEndpoint endpoint = new SplunkHECEndpoint(
                "splunk-hec:localhost:18808/11111111-1111-1111-1111-111111111111", component, configuration);
        assertEquals("localhost:18808", endpoint.getSplunkURL());
        assertEquals("11111111-1111-1111-1111-111111111111", endpoint.getToken());
    }

    @Test
    public void testFQHNValid() {
        SplunkHECConfiguration configuration = new SplunkHECConfiguration();
        SplunkHECComponent component = new SplunkHECComponent();
        SplunkHECEndpoint endpoint = new SplunkHECEndpoint(
                "splunk-hec:http-input.splunkcloud.com:18808/11111111-1111-1111-1111-111111111111", component, configuration);
        assertEquals("http-input.splunkcloud.com:18808", endpoint.getSplunkURL());
        assertEquals("11111111-1111-1111-1111-111111111111", endpoint.getToken());
    }

    @Test
    public void testValidWithOptions() {
        SplunkHECConfiguration configuration = new SplunkHECConfiguration();
        SplunkHECComponent component = new SplunkHECComponent();
        SplunkHECEndpoint endpoint = new SplunkHECEndpoint(
                "splunk-hec:localhost:18808/11111111-1111-1111-1111-111111111111?index=foo", component, configuration);
        assertEquals("localhost:18808", endpoint.getSplunkURL());
        assertEquals("11111111-1111-1111-1111-111111111111", endpoint.getToken());
    }

    @Test
    public void testInvalidPort() {
        SplunkHECConfiguration configuration = new SplunkHECConfiguration();
        SplunkHECComponent component = new SplunkHECComponent();
        Exception e = assertThrows(IllegalArgumentException.class, () -> new SplunkHECEndpoint(
                "splunk-hec:yolo:188508/11111111-1111-1111-1111-111111111111", component, configuration));
        assertEquals("Invalid port: 188508", e.getMessage());
    }
}
