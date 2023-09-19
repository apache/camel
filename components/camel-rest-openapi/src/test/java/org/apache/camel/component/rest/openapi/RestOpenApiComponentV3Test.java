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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.rest.RestEndpoint;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RestOpenApiComponentV3Test extends CamelTestSupport {

    public static WireMockServer petstore = new WireMockServer(wireMockConfig().dynamicPort());

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
    public void setUp() {
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
    public void shouldBeAddingPets(String componentName) throws Exception {
        doSetUp(componentName);

        final Pet pet = new Pet();
        pet.setName("Jean-Luc Picard");

        final Pet created = template.requestBody("direct:addPet", pet, Pet.class);

        assertNotNull(created);

        assertEquals(14, created.getId());

        petstore.verify(
                postRequestedFor(urlEqualTo("/api/v3/pet")).withHeader("Accept", equalTo("application/xml, application/json"))
                        .withHeader("Content-Type", equalTo("application/xml")));
    }

    @ParameterizedTest
    @MethodSource("knownProducers")
    public void shouldBeGettingPetsById(String componentName) throws Exception {
        doSetUp(componentName);

        final Pet pet = template.requestBodyAndHeader("direct:getPetById", NO_BODY, "petId", 14, Pet.class);

        assertNotNull(pet);

        assertEquals(14, pet.getId());
        assertEquals("Olafur Eliason Arnalds", pet.getName());

        petstore.verify(getRequestedFor(urlEqualTo("/api/v3/pet/14")).withHeader("Accept",
                equalTo("application/xml, application/json")));
    }

    @ParameterizedTest
    @MethodSource("knownProducers")
    public void shouldBeGettingPetsByIdSpecifiedInEndpointParameters(String componentName) throws Exception {
        doSetUp(componentName);

        final Pet pet = template.requestBody("direct:getPetByIdWithEndpointParams", NO_BODY, Pet.class);

        assertNotNull(pet);

        assertEquals(14, pet.getId());
        assertEquals("Olafur Eliason Arnalds", pet.getName());

        petstore.verify(getRequestedFor(urlEqualTo("/api/v3/pet/14")).withHeader("Accept",
                equalTo("application/xml, application/json")));
    }

    @ParameterizedTest
    @MethodSource("knownProducers")
    public void shouldBeGettingPetsByIdWithApiKeysInHeader(String componentName) throws Exception {
        doSetUp(componentName);

        final Map<String, Object> headers = new HashMap<>();
        headers.put("petId", 14);
        headers.put("api_key", "dolphins");
        final Pet pet = template.requestBodyAndHeaders("direct:getPetById", NO_BODY, headers, Pet.class);

        assertNotNull(pet);

        assertEquals(14, pet.getId());
        assertEquals("Olafur Eliason Arnalds", pet.getName());

        petstore.verify(
                getRequestedFor(urlEqualTo("/api/v3/pet/14")).withHeader("Accept", equalTo("application/xml, application/json"))
                        .withHeader("api_key", equalTo("dolphins")));
    }

    @ParameterizedTest
    @MethodSource("knownProducers")
    public void shouldBeGettingPetsByIdWithApiKeysInQueryParameter(String componentName) throws Exception {
        doSetUp(componentName);

        final Map<String, Object> headers = new HashMap<>();
        headers.put("petId", 14);
        headers.put("api_key", "dolphins");
        final Pet pet = template.requestBodyAndHeaders("altPetStore:getPetById", NO_BODY, headers, Pet.class);

        assertNotNull(pet);

        assertEquals(14, pet.getId());
        assertEquals("Olafur Eliason Arnalds", pet.getName());

        petstore.verify(getRequestedFor(urlEqualTo("/api/v3/pet/14?api_key=dolphins")).withHeader("Accept",
                equalTo("application/xml, application/json")));
    }

    @ParameterizedTest
    @MethodSource("knownProducers")
    public void shouldBeGettingPetsByStatus(String componentName) throws Exception {
        doSetUp(componentName);

        final Pets pets = template.requestBodyAndHeader("direct:findPetsByStatus", NO_BODY, "status", "available",
                Pets.class);

        assertNotNull(pets);
        assertNotNull(pets.pets);
        assertEquals(2, pets.pets.size());

        petstore.verify(
                getRequestedFor(urlPathEqualTo("/api/v3/pet/findByStatus")).withQueryParam("status", equalTo("available"))
                        .withHeader("Accept", equalTo("application/xml, application/json")));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext camelContext = super.createCamelContext();

        final RestOpenApiComponent component = new RestOpenApiComponent();
        component.setComponentName(componentName);
        component.setHost("http://localhost:" + petstore.port());
        component.setSpecificationUri(RestOpenApiComponentV3Test.class.getResource("/openapi-v3.json").toURI());

        camelContext.addComponent("petStore", component);

        final RestOpenApiComponent altPetStore = new RestOpenApiComponent();
        altPetStore.setComponentName(componentName);
        altPetStore.setHost("http://localhost:" + petstore.port());
        altPetStore.setSpecificationUri(RestOpenApiComponentV3Test.class.getResource("/alt-openapi.json").toURI());

        camelContext.addComponent("altPetStore", altPetStore);

        return camelContext;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                final JAXBContext jaxbContext = JAXBContext.newInstance(Pet.class, Pets.class);

                final JaxbDataFormat jaxb = new JaxbDataFormat(jaxbContext);

                jaxb.setJaxbProviderProperties(Collections.singletonMap(Marshaller.JAXB_FORMATTED_OUTPUT, false));

                from("direct:getPetById").to("petStore:getPetById").unmarshal(jaxb);

                from("direct:getPetByIdWithEndpointParams").to("petStore:getPetById?petId=14").unmarshal(jaxb);

                from("direct:addPet").marshal(jaxb).to("petStore:addPet").unmarshal(jaxb);

                from("direct:findPetsByStatus").to("petStore:findPetsByStatus").unmarshal(jaxb);
            }
        };
    }

    public static Iterable<String> knownProducers() {
        return Arrays.asList(RestEndpoint.DEFAULT_REST_PRODUCER_COMPONENTS);
    }

    @BeforeAll
    public static void setupStubs() throws IOException, URISyntaxException {
        petstore.stubFor(get(urlEqualTo("/openapi-v3.json")).willReturn(aResponse().withBody(
                Files.readAllBytes(Paths.get(RestOpenApiComponentV3Test.class.getResource("/openapi-v3.json").toURI())))));

        petstore.stubFor(post(urlEqualTo("/api/v3/pet"))
                .withRequestBody(equalTo(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Pet><name>Jean-Luc Picard</name></Pet>"))
                .willReturn(aResponse().withStatus(HttpURLConnection.HTTP_CREATED)
                        .withBody("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Pet><id>14</id></Pet>")));

        petstore.stubFor(
                get(urlEqualTo("/api/v3/pet/14")).willReturn(aResponse().withStatus(HttpURLConnection.HTTP_OK).withBody(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Pet><id>14</id><name>Olafur Eliason Arnalds</name></Pet>")));

        petstore.stubFor(get(urlPathEqualTo("/api/v3/pet/findByStatus")).withQueryParam("status", equalTo("available"))
                .willReturn(aResponse().withStatus(HttpURLConnection.HTTP_OK).withBody(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><pets><Pet><id>1</id><name>Olafur Eliason Arnalds</name></Pet><Pet><name>Jean-Luc Picard</name></Pet></pets>")));

        petstore.stubFor(get(urlEqualTo("/api/v3/pet/14?api_key=dolphins"))
                .willReturn(aResponse().withStatus(HttpURLConnection.HTTP_OK).withBody(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Pet><id>14</id><name>Olafur Eliason Arnalds</name></Pet>")));
    }

}
