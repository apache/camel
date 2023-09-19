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
package org.apache.camel.component.rest.openapi;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.camel.CamelContext;
import org.junit.jupiter.api.BeforeAll;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

public class RestOpenApiGlobalHttpsV31Test extends HttpsV3Test {

    @BeforeAll
    public static void setupStubForSpec() throws IOException, URISyntaxException {
        petstore.stubFor(get(urlEqualTo("/petstore-3.1-ssl.yaml")).willReturn(aResponse().withBody(
                Files.readAllBytes(
                        Paths.get(RestOpenApiGlobalHttpsTest.class.getResource("/petstore-3.1-ssl.yaml").toURI())))));
    }

    @Override
    protected String getSpecName() {
        return "/petstore-3.1-ssl.yaml";
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.setSSLContextParameters(createHttpsParameters(camelContext));

        RestOpenApiComponent component = camelContext.getComponent("petStore", RestOpenApiComponent.class);
        component.setUseGlobalSslContextParameters(true);

        return camelContext;
    }
}
