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
package org.apache.camel.component.bonita.api;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.core.Cookie;

import org.apache.camel.component.bonita.api.filter.BonitaAuthFilter;
import org.apache.camel.component.bonita.api.util.BonitaAPIConfig;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class BonitaAuthFilterAlreadyConnectedTest {

    @Mock
    private ClientRequestContext requestContext;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        Map<String, Cookie> resultCookies = new HashMap<>();
        resultCookies.put("JSESSIONID", new Cookie("JSESSIONID", "something"));
        Mockito.when(requestContext.getCookies()).thenReturn(resultCookies);

    }

    @Test
    public void testAlreadyConnected() throws Exception {
        BonitaAPIConfig bonitaApiConfig =
                new BonitaAPIConfig("hostname", "port", "username", "password");
        BonitaAuthFilter bonitaAuthFilter = new BonitaAuthFilter(bonitaApiConfig);
        bonitaAuthFilter.filter(requestContext);
    }
}
