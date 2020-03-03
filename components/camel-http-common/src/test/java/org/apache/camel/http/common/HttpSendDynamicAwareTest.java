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
package org.apache.camel.http.common;

import org.apache.camel.http.base.HttpSendDynamicAware;
import org.apache.camel.spi.SendDynamicAware.DynamicAwareEntry;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HttpSendDynamicAwareTest {

    private HttpSendDynamicAware httpSendDynamicAware;

    @Before
    public void setUp() throws Exception {
        this.httpSendDynamicAware = new HttpSendDynamicAware();
    }

    @Test
    public void testHttpUndefinedPortWithPathParseUri() {
        this.httpSendDynamicAware.setScheme("http");
        DynamicAwareEntry entry = new DynamicAwareEntry("http://localhost/test", null, null, null);
        String[] result = httpSendDynamicAware.parseUri(entry);
        assertEquals("Parse should not add port if http and not specified", "localhost", result[0]);
    }
    
    @Test
    public void testHttpsUndefinedPortParseUri() {
        this.httpSendDynamicAware.setScheme("https");
        DynamicAwareEntry entry = new DynamicAwareEntry("https://localhost/test", null, null, null);
        String[] result = httpSendDynamicAware.parseUri(entry);
        assertEquals("Parse should not add port if https and not specified", "localhost", result[0]);
    }
    
    @Test
    public void testHttpPort80ParseUri() {
        this.httpSendDynamicAware.setScheme("http");
        DynamicAwareEntry entry = new DynamicAwareEntry("http://localhost:80/test", null, null, null);
        String[] result = httpSendDynamicAware.parseUri(entry);
        assertEquals("Parse should not port if http and port 80 specified", "localhost", result[0]);
    }
    
    @Test
    public void testHttpsPort443ParseUri() {
        this.httpSendDynamicAware.setScheme("https");
        DynamicAwareEntry entry = new DynamicAwareEntry("https://localhost:443/test", null, null, null);
        String[] result = httpSendDynamicAware.parseUri(entry);
        assertEquals("Parse should not port if https and port 443 specified", "localhost", result[0]);
    }
    
    @Test
    public void testHttpPort8080ParseUri() {
        this.httpSendDynamicAware.setScheme("http");
        DynamicAwareEntry entry = new DynamicAwareEntry("http://localhost:8080/test", null, null, null);
        String[] result = httpSendDynamicAware.parseUri(entry);
        assertEquals("Parse should add port if http and port other than 80 specified", "localhost:8080", result[0]);
    }
    
    @Test
    public void testHttpsPort8443ParseUri() {
        this.httpSendDynamicAware.setScheme("https");
        DynamicAwareEntry entry = new DynamicAwareEntry("https://localhost:8443/test", null, null, null);
        String[] result = httpSendDynamicAware.parseUri(entry);
        assertEquals("Parse should add port if https and port other than 443 specified", "localhost:8443", result[0]);
    }

}
