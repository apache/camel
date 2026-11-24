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
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServerConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The services are served under camel.cxf.path, which is under the path of the platform-http server.
 */
public class CxfPlatformHttpPathTest extends CxfPlatformHttpTestSupport {

    @Override
    protected void configureServer(VertxPlatformHttpServerConfiguration conf) {
        conf.setPath("/api");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().addInitialProperty(CxfPlatformHttpTransport.PATH_PROPERTY, "soap/");
        return context;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("cxf:greeter?" + SERVICE_CLASS).process(CxfPlatformHttpTestSupport::greeting);
            }
        };
    }

    @Test
    void servedUnderTheServerPathAndTheCxfPath() throws Exception {
        assertThat(greet("/api/soap/greeter", "World", null)).isEqualTo("Hello World");

        HttpResponse<String> response = get("/api/soap/greeter?wsdl");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("location=\"" + baseUrl() + "/api/soap/greeter\"");

        assertThat(get("/api/services/greeter?wsdl").statusCode()).isEqualTo(404);
    }
}
