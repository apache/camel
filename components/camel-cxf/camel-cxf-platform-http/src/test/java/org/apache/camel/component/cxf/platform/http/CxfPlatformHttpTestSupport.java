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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServer;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServerConfiguration;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.cxf.message.MessageContentsList;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Starts a CamelContext with a platform-http server (Vert.x) on a free port; camel-cxf-platform-http is switched on by
 * its context service plugin, as it is on the classpath.
 */
public abstract class CxfPlatformHttpTestSupport extends CamelTestSupport {

    static final String SERVICE_CLASS = "serviceClass=" + GreeterService.class.getName();

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();

    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        VertxPlatformHttpServerConfiguration conf = new VertxPlatformHttpServerConfiguration();
        conf.setBindPort(port.getPort());
        configureServer(conf);
        context.addService(new VertxPlatformHttpServer(conf));
        return context;
    }

    protected void configureServer(VertxPlatformHttpServerConfiguration conf) {
    }

    String baseUrl() {
        return "http://localhost:" + port.getPort();
    }

    /**
     * Calls the greet operation with a cxf producer (a SOAP client), which takes a full URL.
     */
    String greet(String address, String name, String options) {
        String uri = "cxf:" + baseUrl() + address + "?" + SERVICE_CLASS + (options != null ? "&" + options : "");
        Object out = template.requestBodyAndHeader(uri, name, CxfConstants.OPERATION_NAME, "greet");
        return out instanceof MessageContentsList list ? (String) list.get(0) : (String) out;
    }

    HttpResponse<String> get(String pathAndQuery, String... headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + pathAndQuery))
                .timeout(Duration.ofSeconds(20)).GET();
        for (int i = 0; i < headers.length; i += 2) {
            builder.header(headers[i], headers[i + 1]);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * The greeting of the consumer: the name of the request, in the response.
     */
    static void greeting(Exchange exchange) {
        MessageContentsList in = exchange.getIn().getBody(MessageContentsList.class);
        exchange.getMessage().setBody("Hello " + in.get(0));
    }
}
