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
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.cxf.binding.soap.SoapFault;
import org.apache.cxf.message.MessageContentsList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CxfPlatformHttpTest extends CxfPlatformHttpTestSupport {

    private static final String SOAP12 = "bindingId=http://www.w3.org/2003/05/soap/bindings/HTTP/";

    private final AtomicReference<String> mtomContentType = new AtomicReference<>();

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("cxf:/greeter?" + SERVICE_CLASS).process(CxfPlatformHttpTestSupport::greeting);

                from("cxf:/other?" + SERVICE_CLASS)
                        .process(e -> e.getMessage().setBody("Hi " + e.getIn().getBody(MessageContentsList.class).get(0)));

                from("cxf:/published?" + SERVICE_CLASS + "&publishedEndpointUrl=https://soap.example.com/ws/published")
                        .process(CxfPlatformHttpTestSupport::greeting);

                from("cxf:/soap12?" + SERVICE_CLASS + "&" + SOAP12).process(CxfPlatformHttpTestSupport::greeting);

                from("cxf:/mtom?" + SERVICE_CLASS + "&mtomEnabled=true")
                        .process(e -> {
                            mtomContentType.set(e.getIn().getHeader("Content-Type", String.class));
                            byte[] data = (byte[]) e.getIn().getBody(MessageContentsList.class).get(0);
                            e.getMessage().setBody(data);
                        });

                from("cxf:/secure?" + SERVICE_CLASS + "&wsSecurityUsername=scott&wsSecurityPassword=tiger")
                        .process(CxfPlatformHttpTestSupport::greeting);

                from("cxf:/stoppable?" + SERVICE_CLASS).routeId("stoppable").process(CxfPlatformHttpTestSupport::greeting);
            }
        };
    }

    @Test
    void soapRequestOnPlatformHttp() {
        assertThat(greet("/services/greeter", "World", null)).isEqualTo("Hello World");
    }

    @Test
    void twoConsumersOnTheSameServer() {
        assertThat(greet("/services/greeter", "World", null)).isEqualTo("Hello World");
        assertThat(greet("/services/other", "World", null)).isEqualTo("Hi World");
    }

    @Test
    void wsdlGivesTheAddressOfTheRequest() throws Exception {
        HttpResponse<String> response = get("/services/greeter?wsdl");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("wsdl:definitions")
                .contains("location=\"" + baseUrl() + "/services/greeter\"");
    }

    @Test
    void wsdlGivesThePublishedEndpointUrl() throws Exception {
        HttpResponse<String> response = get("/services/published?wsdl");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("location=\"https://soap.example.com/ws/published\"");
    }

    @Test
    void forwardedHeadersAreIgnoredByDefault() throws Exception {
        HttpResponse<String> response = get("/services/greeter?wsdl",
                "X-Forwarded-Proto", "https", "X-Forwarded-Host", "proxy.example.com");

        assertThat(response.body()).contains("location=\"" + baseUrl() + "/services/greeter\"")
                .doesNotContain("proxy.example.com");
    }

    @Test
    void soap12() throws Exception {
        assertThat(greet("/services/soap12", "World", SOAP12)).isEqualTo("Hello World");
        assertThat(get("/services/soap12?wsdl").body()).contains("http://schemas.xmlsoap.org/wsdl/soap12/");
    }

    @Test
    void mtomAttachmentBothWays() {
        byte[] data = new byte[3 * 1024 * 1024];
        new Random(42).nextBytes(data);
        String uri = "cxf:" + baseUrl() + "/services/mtom?" + SERVICE_CLASS + "&mtomEnabled=true";

        Object out = template.requestBodyAndHeader(uri, data, CxfConstants.OPERATION_NAME, "echoBinary");

        assertThat((byte[]) ((MessageContentsList) out).get(0)).isEqualTo(data);
        // the root part of the MTOM message (CXF gives the route the content type of the root part)
        assertThat(mtomContentType.get()).contains("application/xop+xml");
    }

    @Test
    void largeMessageIsStreamedBothWays() {
        String name = "x".repeat(16 * 1024 * 1024);

        assertThat(greet("/services/greeter", name, null)).hasSize(name.length() + 6).startsWith("Hello xxx");
    }

    @Test
    void wsSecurityUsernameToken() {
        assertThat(greet("/services/secure", "Scott", "wsSecurityUsername=scott&wsSecurityPassword=tiger"))
                .isEqualTo("Hello Scott");

        assertThatThrownBy(() -> greet("/services/secure", "Scott", "wsSecurityUsername=scott&wsSecurityPassword=wrong"))
                .isInstanceOf(CamelExecutionException.class)
                .hasRootCauseInstanceOf(SoapFault.class);
        assertThatThrownBy(() -> greet("/services/secure", "Scott", null))
                .isInstanceOf(CamelExecutionException.class)
                .hasRootCauseInstanceOf(SoapFault.class);
    }

    @Test
    void stoppingTheConsumerRemovesTheService() throws Exception {
        assertThat(get("/services/stoppable?wsdl").statusCode()).isEqualTo(200);

        context.getRouteController().stopRoute("stoppable");
        assertThat(get("/services/stoppable?wsdl").statusCode()).isEqualTo(404);

        context.getRouteController().startRoute("stoppable");
        assertThat(get("/services/stoppable?wsdl").statusCode()).isEqualTo(200);
        assertThat(greet("/services/stoppable", "again", null)).isEqualTo("Hello again");
    }

    @Test
    void unknownServiceIsNotFound() throws Exception {
        assertThat(get("/services/unknown?wsdl").statusCode()).isEqualTo(404);
    }
}
