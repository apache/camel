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
package org.apache.camel.http.common.cookie;

import java.io.IOException;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.http.base.cookie.CookieHandler;
import org.apache.camel.http.base.cookie.ExchangeCookieHandler;
import org.apache.camel.http.base.cookie.InstanceCookieHandler;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CookieHandlerTest extends CamelTestSupport {
    private CookieHandler cookieHandler;
    private CookiePolicy cookiePolicy;
    private int expectedNumberOfCookieValues;
    private String uriStr;
    private Exchange exchange;

    public CookieHandlerTest(CookieHandler cookieHandler, CookiePolicy cookiePolicy, String uri, int expectedNumberOfCookieValues, String description) {
        this.cookieHandler = cookieHandler;
        this.cookiePolicy = cookiePolicy;
        this.uriStr = uri;
        this.expectedNumberOfCookieValues = expectedNumberOfCookieValues;
    }
    
    /*
     * This test tries to set a cookie for domain .example.com from host
     * www.example.com or www.sub.example.com According to RFC 2965 section
     * 3.3.1 the latter cookie has to be rejected, however if we set the cookie
     * policy to ACCEPT_ALL the cookie will be accepted again. If a cookie is
     * set, the resulting Cookie header has two lines, one containing the
     * version and one the (single) cookie.
     */
    @Parameters(name = "{index}: {4} policy for {2} returns {3} Cookie header lines")
    public static Iterable<Object[]> data() {
        return Arrays
            .asList(new Object[][] {{new InstanceCookieHandler(), CookiePolicy.ACCEPT_ORIGINAL_SERVER, "http://www.example.com/acme/foo", 2,
                                     "InstanceCookieHandler with ACCEPT_ORIGINAL_SERVER"},
                                    {new InstanceCookieHandler(), CookiePolicy.ACCEPT_ORIGINAL_SERVER, "http://www.sub.example.com/acme/foo", 0,
                                     "InstanceCookieHandler with ACCEPT_ORIGINAL_SERVER"},
                                    {new InstanceCookieHandler(), CookiePolicy.ACCEPT_ALL, "http://www.sub.example.com/acme/foo", 2, "InstanceCookieHandler with ACCEPT_ALL"},
                                    {new ExchangeCookieHandler(), CookiePolicy.ACCEPT_ORIGINAL_SERVER, "http://www.example.com/acme/foo", 2,
                                     "ExchangeCookieHandler with ACCEPT_ORIGINAL_SERVER"},
                                    {new ExchangeCookieHandler(), CookiePolicy.ACCEPT_ORIGINAL_SERVER, "http://www.sub.example.com/acme/foo", 0,
                                     "ExchangeCookieHandler with ACCEPT_ORIGINAL_SERVER"},
                                    {new ExchangeCookieHandler(), CookiePolicy.ACCEPT_ALL, "http://www.sub.example.com/acme/foo", 2, "ExchangeCookieHandler with ACCEPT_ALL"}});
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        exchange = createExchangeWithBody(null);
    }

    @Test
    public void setReceiveAndTestCookie() throws IOException, URISyntaxException {
        URI uri = new URI(uriStr);
        cookieHandler.setCookiePolicy(cookiePolicy);
        Map<String, List<String>> headerMap = new HashMap<>();
        headerMap.put("Set-Cookie", Collections.singletonList("Customer=\"WILE_E_COYOTE\";Version=1;Path=\"/acme\";Domain=\".example.com\""));
        cookieHandler.storeCookies(exchange, uri, headerMap);

        Map<String, List<String>> cookieHeaders = cookieHandler.loadCookies(exchange, uri);
        assertNotNull(cookieHeaders);
        assertNotNull(cookieHeaders.get("Cookie"));
        assertEquals(expectedNumberOfCookieValues, cookieHeaders.get("Cookie").size());
    }
}
