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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.camel.Exchange;
import org.apache.camel.http.base.cookie.CookieHandler;
import org.apache.camel.http.base.cookie.ExchangeCookieHandler;
import org.apache.camel.http.base.cookie.InstanceCookieHandler;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CookieHandlerTest extends CamelTestSupport {
    private Exchange exchange;

    /*
     * This test tries to set a cookie for domain .example.com from host
     * www.example.com or www.sub.example.com According to RFC 2965 section
     * 3.3.1 the latter cookie has to be rejected, however if we set the cookie
     * policy to ACCEPT_ALL the cookie will be accepted again. If a cookie is
     * set, the resulting Cookie header has two lines, one containing the
     * version and one the (single) cookie.
     */
    public static Stream<Arguments> data() {
        return Stream.of(
                Arguments.of(new InstanceCookieHandler(), CookiePolicy.ACCEPT_ORIGINAL_SERVER,
                        "http://www.example.com/acme/foo", 2, "InstanceCookieHandler with ACCEPT_ORIGINAL_SERVER"),
                Arguments.of(new InstanceCookieHandler(), CookiePolicy.ACCEPT_ORIGINAL_SERVER,
                        "http://www.sub.example.com/acme/foo", 0, "InstanceCookieHandler with ACCEPT_ORIGINAL_SERVER"),
                Arguments.of(new InstanceCookieHandler(), CookiePolicy.ACCEPT_ALL, "http://www.sub.example.com/acme/foo", 2,
                        "InstanceCookieHandler with ACCEPT_ALL"),
                Arguments.of(new ExchangeCookieHandler(), CookiePolicy.ACCEPT_ORIGINAL_SERVER,
                        "http://www.example.com/acme/foo", 2, "ExchangeCookieHandler with ACCEPT_ORIGINAL_SERVER"),
                Arguments.of(new ExchangeCookieHandler(), CookiePolicy.ACCEPT_ORIGINAL_SERVER,
                        "http://www.sub.example.com/acme/foo", 0, "ExchangeCookieHandler with ACCEPT_ORIGINAL_SERVER"),
                Arguments.of(new ExchangeCookieHandler(), CookiePolicy.ACCEPT_ALL, "http://www.sub.example.com/acme/foo", 2,
                        "ExchangeCookieHandler with ACCEPT_ALL"));
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        exchange = createExchangeWithBody(null);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void setReceiveAndTestCookie(
            CookieHandler cookieHandler, CookiePolicy cookiePolicy, String uriStr, int expectedNumberOfCookieValues,
            String description)
            throws IOException, URISyntaxException {
        URI uri = new URI(uriStr);
        cookieHandler.setCookiePolicy(cookiePolicy);
        Map<String, List<String>> headerMap = new HashMap<>();
        headerMap.put("Set-Cookie",
                Collections.singletonList("Customer=\"WILE_E_COYOTE\";Version=1;Path=\"/acme\";Domain=\".example.com\""));
        cookieHandler.storeCookies(exchange, uri, headerMap);

        Map<String, List<String>> cookieHeaders = cookieHandler.loadCookies(exchange, uri);
        assertNotNull(cookieHeaders);
        assertNotNull(cookieHeaders.get("Cookie"));
        assertEquals(expectedNumberOfCookieValues, cookieHeaders.get("Cookie").size());
    }
}
