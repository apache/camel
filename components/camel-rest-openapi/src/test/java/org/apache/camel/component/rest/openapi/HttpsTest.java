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
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.HttpsSettings;
import com.google.common.io.Resources;
import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.rest.RestEndpoint;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.support.jsse.CipherSuitesParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.security.CertificateUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public abstract class HttpsTest extends CamelTestSupport {

    protected static WireMockServer petstore = new WireMockServer(
            wireMockConfig().httpServerFactory(new Jetty94ServerFactory()).containerThreads(13).dynamicPort()
                    .dynamicHttpsPort().keystorePath(Resources.getResource("localhost.p12").toString()).keystoreType("PKCS12")
                    .keystorePassword("changeit"));

    static final Object NO_BODY = null;

    public String componentName;

    @BeforeAll
    public static void startWireMockServer() {
        petstore.start();
    }

    @AfterAll
    public static void stopWireMockServer() {
        petstore.stop();
    }

    @Override
    public void setUp() throws Exception {
    }

    @BeforeEach
    public void resetWireMock() {
        petstore.resetRequests();
    }

    public void doSetUp(String componentName) throws Exception {
        this.componentName = componentName;
        super.setUp();
    }

    @ParameterizedTest
    @MethodSource("knownProducers")
    public void shouldBeConfiguredForHttps(String componentName) throws Exception {
        doSetUp(componentName);

        final Pet pet = template.requestBodyAndHeader("direct:getPetById", NO_BODY, "petId", 14, Pet.class);

        assertNotNull(pet);

        assertEquals(Integer.valueOf(14), pet.id);
        assertEquals("Olafur Eliason Arnalds", pet.name);

        petstore.verify(getRequestedFor(urlEqualTo("/v2/pet/14")).withHeader("Accept",
                equalTo("application/xml, application/json")));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext camelContext = super.createCamelContext();

        final RestOpenApiComponent component = new RestOpenApiComponent();
        component.setComponentName(componentName);
        component.setHost("https://localhost:" + petstore.httpsPort());

        camelContext.addComponent("petStore", component);

        return camelContext;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                final JAXBContext jaxbContext = JAXBContext.newInstance(Pet.class, Pets.class);

                final JaxbDataFormat jaxb = new JaxbDataFormat(jaxbContext);

                jaxb.setJaxbProviderProperties(Collections.singletonMap(Marshaller.JAXB_FORMATTED_OUTPUT, false));

                from("direct:getPetById").to("petStore:getPetById").unmarshal(jaxb);
            }
        };
    }

    public static Iterable<String> knownProducers() {
        final List<String> producers = new ArrayList<>(Arrays.asList(RestEndpoint.DEFAULT_REST_PRODUCER_COMPONENTS));

        // skip http due security certificate testing problems
        producers.remove("http");

        return producers;
    }

    @BeforeAll
    public static void setupStubs() throws IOException, URISyntaxException {
        petstore.stubFor(get(urlEqualTo("/openapi.json")).willReturn(aResponse().withBody(
                Files.readAllBytes(Paths.get(RestOpenApiGlobalHttpsTest.class.getResource("/openapi.json").toURI())))));

        petstore.stubFor(
                get(urlEqualTo("/v2/pet/14")).willReturn(aResponse().withStatus(HttpURLConnection.HTTP_OK).withBody(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Pet><id>14</id><name>Olafur Eliason Arnalds</name></Pet>")));
    }

    static SSLContextParameters createHttpsParameters(final CamelContext camelContext) throws Exception {
        final TrustManagersParameters trustManagerParameters = new TrustManagersParameters();
        trustManagerParameters.setCamelContext(camelContext);
        final TrustManagerFactory trustManagerFactory
                = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        final HttpsSettings httpsSettings = petstore.getOptions().httpsSettings();
        final KeyStore trustStore = CertificateUtils.getKeyStore(Resource.newResource(httpsSettings.keyStorePath()),
                httpsSettings.keyStoreType(), null, httpsSettings.keyStorePassword());
        trustManagerFactory.init(trustStore);
        final TrustManager trustManager = trustManagerFactory.getTrustManagers()[0];
        trustManagerParameters.setTrustManager(trustManager);

        final SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setCamelContext(camelContext);
        sslContextParameters.setTrustManagers(trustManagerParameters);
        final CipherSuitesParameters cipherSuites = new CipherSuitesParameters();
        cipherSuites.setCipherSuite(Collections.singletonList("TLS_DHE_RSA_WITH_AES_128_GCM_SHA256"));
        sslContextParameters.setCipherSuites(cipherSuites);
        sslContextParameters.setSecureSocketProtocol("TLSv1.2");
        return sslContextParameters;
    }

}
