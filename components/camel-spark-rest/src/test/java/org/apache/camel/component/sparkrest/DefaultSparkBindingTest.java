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
package org.apache.camel.component.sparkrest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;

import javax.servlet.http.HttpServletRequest;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.apache.camel.Exchange;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import spark.Request;

import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.apache.camel.Exchange.HTTP_QUERY;
import static org.apache.camel.Exchange.HTTP_URI;
import static org.apache.camel.Exchange.HTTP_URL;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


@RunWith(Theories.class)
public class DefaultSparkBindingTest {

    private DefaultSparkBinding defaultSparkBinding; 

    private Request request = mock(Request.class);
    private SparkConfiguration sparkConfiguration = mock(SparkConfiguration.class);
    private HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
    private Exchange camelExchange = mock(Exchange.class);
    
    @Before
    public void setup() {
        defaultSparkBinding = new DefaultSparkBinding();

        when(request.raw()).thenReturn(httpServletRequest);
        when(request.headers()).thenReturn(Sets.newHashSet("Content-Type"));
        when(request.headers("Content-Type")).thenReturn("application/json");
        
        when(httpServletRequest.getMethod()).thenReturn("POST");
        when(httpServletRequest.getQueryString()).thenReturn("?query=value");
        when(httpServletRequest.getRequestURL()).thenReturn(new StringBuffer("http://localhost?query=value"));
        when(httpServletRequest.getRequestURI()).thenReturn("http://localhost?query=value");
        when(httpServletRequest.getContentType()).thenReturn("application/json");
        
        when(camelExchange.getFromEndpoint()).thenReturn(new SparkEndpoint("/", null));   
    }
    
    @DataPoints("exchangeHeaderScenarios")
    public static List<ExchangeHeaderScenario> exchangeHeaderScenarios() {
        return asList(
                new ExchangeHeaderScenario(emptyMap(), HTTP_METHOD, "POST"),
                new ExchangeHeaderScenario(emptyMap(), HTTP_QUERY, "?query=value"),
                new ExchangeHeaderScenario(emptyMap(), HTTP_URL, "http://localhost?query=value"),
                new ExchangeHeaderScenario(emptyMap(), HTTP_URI, "http://localhost?query=value"),
                new ExchangeHeaderScenario(emptyMap(), CONTENT_TYPE, "application/json"),
                new ExchangeHeaderScenario(ImmutableMap.of(HTTP_METHOD, "GET"), HTTP_METHOD, "GET"),
                new ExchangeHeaderScenario(ImmutableMap.of(HTTP_QUERY, "?originalQuery=value"), HTTP_QUERY, "?originalQuery=value"),
                new ExchangeHeaderScenario(ImmutableMap.of(HTTP_URL, "http://originalhost?query=value"), HTTP_URL, "http://originalhost?query=value"),
                new ExchangeHeaderScenario(ImmutableMap.of(HTTP_URI, "http://originalhost?query=value"), HTTP_URI, "http://originalhost?query=value"),
                new ExchangeHeaderScenario(ImmutableMap.of(CONTENT_TYPE, "text/plain"), CONTENT_TYPE, "text/plain")
        );
    }

    @Theory
    @Test
    public void shouldOnlyAddStandardExchangeHeaderGivenHeaderNotPresentInInput(
            @FromDataPoints("exchangeHeaderScenarios") ExchangeHeaderScenario scenario) throws Exception {
        //given
        Map<String, Object> headers = scenario.headers;
        
        //when
        defaultSparkBinding.populateCamelHeaders(request, headers, camelExchange, sparkConfiguration);

        //then
        String actualHeader = Objects.toString(headers.get(scenario.expectedHeaderName), null);
        assertEquals(scenario.expectedHeaderValue, actualHeader);
    }

    private static class ExchangeHeaderScenario {
        Map<String, Object> headers = new HashMap<>();
        String expectedHeaderName;
        String expectedHeaderValue;
        
        ExchangeHeaderScenario(Map<String, Object> headers, String expectedHeaderName, String expectedHeaderValue) {
            super();
            this.expectedHeaderName = expectedHeaderName;
            this.expectedHeaderValue = expectedHeaderValue;
            
            this.headers.putAll(headers);
        }
    }
}


