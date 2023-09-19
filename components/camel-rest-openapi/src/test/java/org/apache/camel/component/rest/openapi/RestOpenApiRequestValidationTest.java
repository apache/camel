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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jakarta.xml.bind.JAXBContext;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.EnumFeature;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RestOpenApiRequestValidationTest extends CamelTestSupport {

    public static WireMockServer wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());

    @BeforeAll
    public static void startWireMockServer() throws Exception {
        wireMockServer.start();
        setUpPetStoreStubs("/openapi.json", "/v2/pet");
        setUpPetStoreStubs("/openapi-v3.json", "/api/v3/pet");
        setUpPetStoreStubs("/petstore-3.1.yaml", "/api/v31/pet");
        setUpFruitsApiStubs("/fruits-2.0.yaml");
        setUpFruitsApiStubs("/fruits-3.0.yaml");
    }

    @AfterAll
    public static void stopWireMockServer() {
        wireMockServer.stop();
    }

    @BeforeEach
    public void resetWireMock() {
        wireMockServer.resetRequests();
    }

    static void setUpPetStoreStubs(String specificationPath, String urlBasePath) throws Exception {
        wireMockServer.stubFor(get(urlEqualTo(specificationPath)).willReturn(aResponse().withBody(
                Files.readAllBytes(Paths.get(
                        Objects.requireNonNull(RestOpenApiComponentV3Test.class.getResource(specificationPath)).toURI())))));

        String validationEnabledPetJson
                = "{\"id\":10,\"name\":\"doggie\",\"photoUrls\":[\"https://test.photos.org/doggie.gif\"]}";
        wireMockServer.stubFor(post(urlEqualTo(urlBasePath))
                .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
                .withRequestBody(equalTo(
                        validationEnabledPetJson))
                .willReturn(aResponse().withStatus(HttpURLConnection.HTTP_CREATED)
                        .withBody(validationEnabledPetJson)));

        String validationEnabledPetXml
                = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                  "<Pet>\n" +
                  "    <id>10</id>\n" +
                  "    <name>doggie</name>\n" +
                  "    <photoUrls>https://test.photos.org/doggie.gif</photoUrls>\n" +
                  "</Pet>\n";
        wireMockServer.stubFor(post(urlEqualTo(urlBasePath))
                .withHeader("Content-Type", equalTo("application/xml"))
                .withRequestBody(equalTo(
                        validationEnabledPetXml))
                .willReturn(aResponse().withStatus(HttpURLConnection.HTTP_CREATED)
                        .withBody(validationEnabledPetXml)));

        String validationDisabledPetJson = "{\"id\":10,\"name\":\"doggie\"}";
        wireMockServer.stubFor(post(urlEqualTo(urlBasePath))
                .withRequestBody(equalTo(
                        validationDisabledPetJson))
                .willReturn(aResponse().withStatus(HttpURLConnection.HTTP_CREATED)
                        .withBody(validationDisabledPetJson)));

        String petsJson = "[{\"id\":1,\"name\":\"doggie\", \"id\":2,\"name\":\"doggie2\"}]";
        wireMockServer.stubFor(get(urlPathEqualTo(urlBasePath + "/findByStatus"))
                .withQueryParam("status", equalTo("available"))
                .willReturn(aResponse().withStatus(HttpURLConnection.HTTP_OK)
                        .withBody(petsJson)));

        wireMockServer.stubFor(delete(urlPathEqualTo(urlBasePath + "/10"))
                .willReturn(aResponse().withStatus(HttpURLConnection.HTTP_OK)
                        .withBody("Pet deleted")));

        String uploadImageJson
                = "{\"id\":1,\"category\":{\"id\":1,\"name\":\"Pet\"},\"name\":\"Test\",\"photoUrls\":[\"image.jpg\"],\"tags\":[],\"status\":\"available\"}";
        wireMockServer.stubFor(post(urlPathEqualTo(urlBasePath + "/1/uploadImage"))
                .withRequestBody(binaryEqualTo(createUploadImage()))
                .willReturn(aResponse().withStatus(HttpURLConnection.HTTP_OK)
                        .withBody(uploadImageJson)));
    }

    static void setUpFruitsApiStubs(String specificationPath) throws Exception {
        String urlBasePath = "/api/v1/fruit";

        wireMockServer.stubFor(get(urlEqualTo(specificationPath)).willReturn(aResponse().withBody(
                Files.readAllBytes(Paths.get(
                        Objects.requireNonNull(RestOpenApiComponentV3Test.class.getResource(specificationPath)).toURI())))));

        wireMockServer.stubFor(post(urlPathEqualTo(urlBasePath + "/form"))
                .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
                .willReturn(aResponse().withStatus(HttpURLConnection.HTTP_OK)
                        .withBody("{\"name\":\"Lemon\",\"color\":\"Yellow\"}")));

        wireMockServer.stubFor(delete(urlPathEqualTo(urlBasePath + "/1"))
                .withHeader("deletionReason", matching("Test deletion reason"))
                .willReturn(aResponse().withStatus(HttpURLConnection.HTTP_OK)
                        .withBody("Fruit deleted")));

        wireMockServer.stubFor(delete(urlPathEqualTo(urlBasePath + "/1"))
                .withHeader("deletionReason", containing("Test deletion reason 1"))
                .withHeader("deletionReason", containing("Test deletion reason 2"))
                .willReturn(aResponse().withStatus(HttpURLConnection.HTTP_OK)
                        .withBody("Fruit deleted")));

        wireMockServer.stubFor(delete(urlPathEqualTo(urlBasePath))
                .withQueryParam("id", containing("1"))
                .withQueryParam("id", containing("2"))
                .willReturn(aResponse().withStatus(HttpURLConnection.HTTP_OK)
                        .withBody("Fruits deleted")));
    }

    @ParameterizedTest
    @MethodSource("petStoreVersions")
    void requestValidationDisabled(String petStoreVersion) {
        Pet pet = new Pet();
        pet.setId(10);
        pet.setName("doggie");

        Pet createdPet = template.requestBodyAndHeader("direct:validationDisabled", pet, "petStoreVersion", petStoreVersion,
                Pet.class);
        assertEquals(10, createdPet.getId());
    }

    @ParameterizedTest
    @MethodSource("petStoreVersions")
    void requestValidationWithCustomLevels(String petStoreVersion) {
        Pet pet = new Pet();
        pet.setId(10);
        pet.setName("doggie");

        Pet createdPet = template.requestBodyAndHeader("direct:customLevels", pet, "petStoreVersion", petStoreVersion,
                Pet.class);
        assertEquals(10, createdPet.getId());
    }

    @ParameterizedTest
    @MethodSource("petStoreVersions")
    void requestValidationWithValidJsonBody(String petStoreVersion) {
        Pet pet = new Pet();
        pet.setId(10);
        pet.setName("doggie");
        pet.setPhotoUrls(List.of("https://test.photos.org/doggie.gif"));

        Pet createdPet
                = template.requestBodyAndHeader("direct:validateJsonBody", pet, "petStoreVersion", petStoreVersion, Pet.class);
        assertEquals(10, createdPet.getId());
    }

    @ParameterizedTest
    @MethodSource("petStoreVersions")
    void requestValidationWithJsonBodyAndMissingMandatoryFields(String petStoreVersion) {
        Pet pet = new Pet();
        pet.setName(null);

        Exchange exchange = template.request("direct:validateJsonBody", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getMessage().setHeader("petStoreVersion", petStoreVersion);
                exchange.getMessage().setBody(pet);
            }
        });

        Exception exception = exchange.getException();
        assertNotNull(exception);
        assertInstanceOf(RestOpenApiValidationException.class, exception);

        RestOpenApiValidationException validationException = (RestOpenApiValidationException) exception;
        Set<String> errors = validationException.getValidationErrors();
        assertEquals(1, errors.size());
        assertEquals("Object has missing required properties ([\"name\",\"photoUrls\"])", errors.iterator().next());
    }

    @ParameterizedTest
    @MethodSource("petStoreVersions")
    void requestValidationWithBadlyFormedJsonBody(String petStoreVersion) {
        Exchange exchange = template.request("direct:validateInvalidJsonBody", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getMessage().setHeader("petStoreVersion", petStoreVersion);
                exchange.getMessage().setBody("invalid JSON string");
            }
        });

        Exception exception = exchange.getException();
        assertNotNull(exception);
        assertInstanceOf(RestOpenApiValidationException.class, exception);

        RestOpenApiValidationException validationException = (RestOpenApiValidationException) exception;
        Set<String> errors = validationException.getValidationErrors();
        assertEquals(1, errors.size());

        String errorMessage = errors.iterator().next();
        assertTrue(errorMessage.startsWith("Unable to parse JSON"));
    }

    @ParameterizedTest
    @MethodSource("petStoreVersions")
    void requestValidationWithValidXmlBody(String petStoreVersion) {
        Pet pet = new Pet();
        pet.setId(10);
        pet.setName("doggie");
        pet.setPhotoUrls(List.of("https://test.photos.org/doggie.gif"));

        Pet createdPet
                = template.requestBodyAndHeader("direct:validateXmlBody", pet, "petStoreVersion", petStoreVersion, Pet.class);
        assertEquals(10, createdPet.getId());
    }

    @ParameterizedTest
    @MethodSource("petStoreVersions")
    void requestValidationWithNullBody(String petStoreVersion) {
        Exchange exchange = template.request("direct:validateNullBody", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getMessage().setHeader("petStoreVersion", petStoreVersion);
                exchange.getMessage().setBody(null);
            }
        });

        Exception exception = exchange.getException();
        assertNotNull(exception);
        assertInstanceOf(RestOpenApiValidationException.class, exception);

        RestOpenApiValidationException validationException = (RestOpenApiValidationException) exception;
        Set<String> errors = validationException.getValidationErrors();
        assertEquals(1, errors.size());
        assertEquals("A request body is required but none found.", errors.iterator().next());
    }

    @ParameterizedTest
    @MethodSource("petStoreVersions")
    void requestValidationWithInvalidContentType(String petStoreVersion) {
        Pet pet = new Pet();
        pet.setId(10);
        pet.setName("doggie");
        pet.setPhotoUrls(List.of("https://test.photos.org/doggie.gif"));

        Exchange exchange = template.request("direct:validateContentType", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getMessage().setHeader("petStoreVersion", petStoreVersion);
                exchange.getMessage().setBody(pet);
            }
        });

        Exception exception = exchange.getException();
        assertNotNull(exception);
        assertInstanceOf(RestOpenApiValidationException.class, exception);

        RestOpenApiValidationException validationException = (RestOpenApiValidationException) exception;
        Set<String> errors = validationException.getValidationErrors();
        assertEquals(1, errors.size());

        String errorMessage = errors.iterator().next();
        assertTrue(
                errorMessage.startsWith("Request Content-Type header '[application/camel]' does not match any allowed types"));
    }

    @ParameterizedTest
    @MethodSource("petStoreVersions")
    void requestValidationWithRequiredPathAndQueryParameter(String petStoreVersion) {
        Map<String, Object> headers = Map.of(
                "petStoreVersion", petStoreVersion,
                "petId", 10,
                "api_key", "foo");
        String result = template.requestBodyAndHeaders("direct:validateDelete", null, headers, String.class);
        assertEquals("Pet deleted", result);
    }

    @ParameterizedTest
    @MethodSource("petStoreVersions")
    @SuppressWarnings("unchecked")
    void requestValidationWithRequiredQueryParameter(String petStoreVersion) {
        Map<String, Object> headers = Map.of(
                "status", "available",
                "petStoreVersion", petStoreVersion);
        List<Pet> pets = template.requestBodyAndHeaders("direct:validateOperationForQueryParams", null, headers, List.class);
        assertFalse(pets.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = { "petStoreV2", "petStoreV3" })
    void requestValidationWithBinaryBody(String petStoreVersion) throws IOException {
        Map<String, Object> headers = Map.of(
                "petId", 1,
                "petStoreVersion", petStoreVersion);
        Pet pet = template.requestBodyAndHeaders("direct:binaryContent", createUploadImage(), headers, Pet.class);
        assertNotNull(pet);
        assertEquals(1, pet.getPhotoUrls().size());
    }

    @ParameterizedTest
    @MethodSource("petStoreVersions")
    void restOpenApiEndpointDefaultOptions(String petStoreVersion) throws Exception {
        RestOpenApiEndpoint endpoint = context.getEndpoint(petStoreVersion + ":#addPet", RestOpenApiEndpoint.class);
        endpoint.createProducer();
        assertFalse(endpoint.isRequestValidationEnabled());
        assertNull(endpoint.getRequestValidationCustomizer());
        assertTrue(endpoint.getRequestValidationLevels().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("fruitsApiVersions")
    void requestValidationRequiredHeaderParamsNotPresent(String fruitsApiVersion) {
        Exchange exchange = template.request("direct:headerParam", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getMessage().setHeader("fruitsApiVersion", fruitsApiVersion);
            }
        });

        Exception exception = exchange.getException();
        assertNotNull(exception);
        assertInstanceOf(RestOpenApiValidationException.class, exception);

        RestOpenApiValidationException validationException = (RestOpenApiValidationException) exception;
        Set<String> errors = validationException.getValidationErrors();
        assertEquals(1, errors.size());
        assertTrue(errors.iterator().next().startsWith("Header parameter 'deletionReason' is required"));
    }

    @ParameterizedTest
    @MethodSource("fruitsApiVersions")
    void requestValidationRequiredHeaderParamsPresent(String fruitsApiVersion) {
        Map<String, Object> headers = Map.of(
                "fruitsApiVersion", fruitsApiVersion,
                "id", 1,
                "deletionReason", "Test deletion reason");

        String result = template.requestBodyAndHeaders("direct:headerParam", null, headers, String.class);
        assertEquals("Fruit deleted", result);
    }

    @ParameterizedTest
    @MethodSource("fruitsApiVersions")
    void requestValidationRequiredHeaderParamsPresentAsList(String fruitsApiVersion) {
        Map<String, Object> headers = Map.of(
                "fruitsApiVersion", fruitsApiVersion,
                "id", 1,
                "deletionReason", List.of("Test deletion reason 1", "Test deletion reason 2"));

        String result = template.requestBodyAndHeaders("direct:headerParam", null, headers, String.class);
        assertEquals("Fruit deleted", result);
    }

    @ParameterizedTest
    @MethodSource("fruitsApiVersions")
    void requestValidationRequiredFormParamsNotPresent(String fruitsApiVersion) {
        Exchange exchange = template.request("direct:formParam", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getMessage().setHeader("fruitsApiVersion", fruitsApiVersion);
                exchange.getMessage().setBody("name=&color=");
            }
        });

        Exception exception = exchange.getException();
        assertNotNull(exception);
        assertInstanceOf(RestOpenApiValidationException.class, exception);

        RestOpenApiValidationException validationException = (RestOpenApiValidationException) exception;
        Set<String> errors = validationException.getValidationErrors();
        // [Path '/name'] Instance type (null) does not match any allowed primitive type string
        assertEquals(2, errors.size());
        Iterator<String> iterator = errors.iterator();
        assertEquals("[Path '/color'] Instance type (null) does not match any allowed primitive type (allowed: [\"string\"])",
                iterator.next());
        assertEquals("[Path '/name'] Instance type (null) does not match any allowed primitive type (allowed: [\"string\"])",
                iterator.next());
    }

    @ParameterizedTest
    @MethodSource("fruitsApiVersions")
    void requestValidationRequiredFormParamsPresent(String fruitsApiVersion) {
        String result = template.requestBodyAndHeader("direct:formParam", "name=Lemon&color=Yellow", "fruitsApiVersion",
                fruitsApiVersion, String.class);
        assertEquals("{\"name\":\"Lemon\",\"color\":\"Yellow\"}", result);
    }

    @ParameterizedTest
    @MethodSource("fruitsApiVersions")
    void requestValidationRequiredQueryParamsPresentAsList(String fruitsApiVersion) {
        Map<String, Object> headers = Map.of(
                "fruitsApiVersion", fruitsApiVersion,
                "id", List.of("1", "2"));

        String result = template.requestBodyAndHeaders("direct:queryParam", null, headers, String.class);
        assertEquals("Fruits deleted", result);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.addComponent("petStoreV2", createRestOpenApiComponent("openapi.json"));
        camelContext.addComponent("petStoreV3", createRestOpenApiComponent("openapi-v3.json"));
        camelContext.addComponent("petStoreV31", createRestOpenApiComponent("petstore-3.1.yaml"));
        camelContext.addComponent("fruitsV2", createRestOpenApiComponent("fruits-2.0.yaml"));
        camelContext.addComponent("fruitsV3", createRestOpenApiComponent("fruits-3.0.yaml"));
        camelContext.getGlobalOptions().put("CamelJacksonEnableTypeConverter", "true");
        camelContext.getGlobalOptions().put("CamelJacksonTypeConverterToPojo", "true");
        return camelContext;
    }

    public static Iterable<String> petStoreVersions() {
        return List.of("petStoreV2", "petStoreV3", "petStoreV31");
    }

    public static Iterable<String> fruitsApiVersions() {
        return List.of("fruitsV2", "fruitsV3");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(EnumFeature.WRITE_ENUMS_TO_LOWERCASE, true);

        JacksonDataFormat jacksonDataFormat = new JacksonDataFormat();
        jacksonDataFormat.setObjectMapper(mapper);

        JAXBContext jaxbContext = JAXBContext.newInstance(Pet.class);
        JaxbDataFormat jaxbDataFormat = new JaxbDataFormat(jaxbContext);

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:validationDisabled")
                        .marshal(jacksonDataFormat)
                        .toD("${header.petStoreVersion}:#addPet")
                        .unmarshal(jacksonDataFormat);

                from("direct:validateJsonBody")
                        .marshal(jacksonDataFormat)
                        // Append charset to verify the validator internals can handle it
                        .setHeader(Exchange.CONTENT_TYPE).constant("application/json; charset=UTF-8")
                        .toD("${header.petStoreVersion}:#addPet?requestValidationEnabled=true")
                        .unmarshal(jacksonDataFormat);

                from("direct:validateDelete")
                        .toD("${header.petStoreVersion}:#deletePet?requestValidationEnabled=true");

                from("direct:validateXmlBody")
                        .marshal(jaxbDataFormat)
                        .setHeader(Exchange.CONTENT_TYPE).constant("application/xml")
                        .toD("${header.petStoreVersion}:#addPet?requestValidationEnabled=true&consumes=application/xml&produces=application/xml")
                        .unmarshal(jaxbDataFormat);

                from("direct:validateContentType")
                        .marshal(jacksonDataFormat)
                        .setHeader(Exchange.CONTENT_TYPE).constant("application/camel")
                        .toD("${header.petStoreVersion}:#addPet?requestValidationEnabled=true")
                        .unmarshal(jaxbDataFormat);

                from("direct:validateNullBody")
                        .toD("${header.petStoreVersion}:#addPet?requestValidationEnabled=true");

                from("direct:validateInvalidJsonBody")
                        .setHeader(Exchange.CONTENT_TYPE).constant("application/json")
                        .toD("${header.petStoreVersion}:#addPet?requestValidationEnabled=true");

                from("direct:validateOperationForQueryParams")
                        .toD("${header.petStoreVersion}:findPetsByStatus?requestValidationEnabled=true")
                        .unmarshal(jacksonDataFormat);

                from("direct:binaryContent")
                        .toD("${header.petStoreVersion}:uploadFile?requestValidationEnabled=true&produces=application/octet-stream")
                        .unmarshal(jacksonDataFormat);

                from("direct:customLevels")
                        .marshal(jacksonDataFormat)
                        .toD("${header.petStoreVersion}:#addPet?requestValidationEnabled=true&validation.request.body=IGNORE")
                        .unmarshal(jacksonDataFormat);

                from("direct:headerParam")
                        .toD("${header.fruitsApiVersion}:#deleteFruit?requestValidationEnabled=true");

                from("direct:formParam")
                        .setHeader(Exchange.CONTENT_TYPE).constant("application/x-www-form-urlencoded")
                        .toD("${header.fruitsApiVersion}:#addFruitFromForm?requestValidationEnabled=true");

                from("direct:queryParam")
                        .toD("${header.fruitsApiVersion}:#deleteFruits?requestValidationEnabled=true");
            }
        };
    }

    private RestOpenApiComponent createRestOpenApiComponent(String specificationUri) {
        RestOpenApiComponent component = new RestOpenApiComponent();
        component.setComponentName("http");
        component.setSpecificationUri(URI.create("classpath:" + specificationUri));
        component.setConsumes("application/json");
        component.setProduces("application/json");
        component.setHost("http://localhost:" + wireMockServer.port());
        return component;
    }

    private static byte[] createUploadImage() throws IOException {
        // Creates a 50x50 square filled with white
        BufferedImage image = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 50, 50);
        graphics.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }
}
