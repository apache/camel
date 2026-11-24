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
package org.apache.camel.component.cxf.platform.http;

import java.net.http.HttpResponse;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With camel.cxf.useXForwardedHeaders=true, behind a proxy, the WSDL gives the address of the proxy.
 */
public class CxfPlatformHttpForwardedTest extends CxfPlatformHttpTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().addInitialProperty(CxfPlatformHttpTransport.USE_X_FORWARDED_HEADERS_PROPERTY, "true");
        return context;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("cxf:/greeter?" + SERVICE_CLASS).process(CxfPlatformHttpTestSupport::greeting);
            }
        };
    }

    @Test
    void wsdlGivesTheAddressOfTheProxy() throws Exception {
        HttpResponse<String> response = get("/services/greeter?wsdl",
                "X-Forwarded-Proto", "https", "X-Forwarded-Host", "proxy.example.com");

        assertThat(response.body()).contains("location=\"https://proxy.example.com/services/greeter\"");
    }

    @Test
    void wsdlGivesThePortOfTheProxy() throws Exception {
        HttpResponse<String> response = get("/services/greeter?wsdl",
                "X-Forwarded-Proto", "https", "X-Forwarded-Host", "proxy.example.com, inner.example.com",
                "X-Forwarded-Port", "8443");

        assertThat(response.body()).contains("location=\"https://proxy.example.com:8443/services/greeter\"");
    }

    @Test
    void withoutTheHeadersTheAddressIsThatOfTheRequest() throws Exception {
        assertThat(get("/services/greeter?wsdl").body())
                .contains("location=\"" + baseUrl() + "/services/greeter\"");
        assertThat(greet("/services/greeter", "World", null)).isEqualTo("Hello World");
    }
}
