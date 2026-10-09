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
package org.apache.camel.component.rest.postman;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A placeholder that neither the collection nor the variables option defines may be resolved from Camel properties,
 * which by default also cover JVM system properties and OS environment variables.
 * <p>
 * A collection fetched from the Postman cloud or over HTTP can be changed by whoever edits or serves it, so by default
 * its placeholders must not be able to copy those values into an outgoing request. A collection read from the classpath
 * or the file system ships with the application and keeps resolving them.
 */
class RestPostmanPropertyFallbackTest {

    private static final String UID = "12ece9e1-2abf-4edc-8e34-de66e74114d2";
    private static final String COLLECTION = "property-fallback-collection.json";
    private static final String SYSTEM_PROPERTY = "restPostmanSysLeakProbe";

    private WireMockServer server;
    private DefaultCamelContext context;
    private ProducerTemplate template;

    @BeforeEach
    void setUp() throws Exception {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        WireMock.configureFor("localhost", server.port());

        String collection = IOHelper.loadText(getClass().getClassLoader().getResourceAsStream(COLLECTION));
        // the Postman cloud wraps the collection in a "collection" envelope
        server.stubFor(get(urlEqualTo("/collections/" + UID))
                .willReturn(aResponse().withStatus(200)
                        .withBody(("{\"collection\":" + collection + "}").getBytes(StandardCharsets.UTF_8))));
        server.stubFor(get(urlEqualTo("/" + COLLECTION))
                .willReturn(aResponse().withStatus(200).withBody(collection.getBytes(StandardCharsets.UTF_8))));
        server.stubFor(get(urlPathEqualTo("/v3/status")).willReturn(aResponse().withStatus(200).withBody("ok")));
        server.stubFor(get(urlPathEqualTo("/v3/format")).willReturn(aResponse().withStatus(200).withBody("ok")));

        System.setProperty(SYSTEM_PROPERTY, "from-system-property");

        Properties properties = new Properties();
        properties.setProperty("leakProbe", "from-camel-properties");
        context = new DefaultCamelContext();
        context.getPropertiesComponent().setInitialProperties(properties);
        context.start();
        template = context.createProducerTemplate();
    }

    @AfterEach
    void tearDown() {
        context.stop();
        server.stop();
        System.clearProperty(SYSTEM_PROPERTY);
    }

    private String cloudUri(String extra) {
        return uri(UID, "postmanApiUrl=http://localhost:" + server.port() + "&postmanApiKey=PMAK-test", extra);
    }

    private String httpUri(String extra) {
        return uri("http://localhost:" + server.port() + "/" + COLLECTION, null, extra);
    }

    private String classpathUri(String extra) {
        return uri("classpath:" + COLLECTION, null, extra);
    }

    private String uri(String source, String sourceOptions, String extra) {
        return uri(source, "getStatus", sourceOptions, extra);
    }

    private String uri(String source, String requestId, String sourceOptions, String extra) {
        return "rest-postman:" + source + "#" + requestId
               + "?variable.baseUrl=http://localhost:" + server.port() + "/v3"
               + "&variable.region=eu"
               + (sourceOptions != null ? "&" + sourceOptions : "")
               + (extra != null ? "&" + extra : "");
    }

    private void call(String uri) {
        assertThat(template.requestBody(uri, null, String.class)).isEqualTo("ok");
    }

    /**
     * Verifies the call to the API: the collection variable and the variables option are always resolved, the two
     * probes only from properties.
     */
    private static void verifyStatusCall(String leak, String systemPropertyLeak) {
        WireMock.verify(getRequestedFor(urlPathEqualTo("/v3/status"))
                .withHeader("X-Tenant", equalTo("acme"))
                .withHeader("X-Region", equalTo("eu"))
                .withHeader("X-Leak", equalTo(leak))
                .withHeader("X-Leak-Sys", equalTo(systemPropertyLeak)));
    }

    @Test
    void shouldNotResolvePropertiesForACloudCollection() {
        call(cloudUri(null));

        verifyStatusCall("{{leakProbe}}", "{{" + SYSTEM_PROPERTY + "}}");
    }

    @Test
    void shouldNotResolvePropertiesForACollectionFetchedOverHttp() {
        call(httpUri(null));

        verifyStatusCall("{{leakProbe}}", "{{" + SYSTEM_PROPERTY + "}}");
    }

    @Test
    void shouldResolvePropertiesForAClasspathCollection() {
        call(classpathUri(null));

        verifyStatusCall("from-camel-properties", "from-system-property");
    }

    @Test
    void shouldResolvePropertiesForACloudCollectionWhenEnabled() {
        call(cloudUri("resolveVariablesFromProperties=enabled"));

        verifyStatusCall("from-camel-properties", "from-system-property");
    }

    @Test
    void shouldNotResolvePropertiesForAClasspathCollectionWhenDisabled() {
        call(classpathUri("resolveVariablesFromProperties=disabled"));

        verifyStatusCall("{{leakProbe}}", "{{" + SYSTEM_PROPERTY + "}}");
    }

    @Test
    void shouldDecideBySourceWhenAuto() {
        call(cloudUri("resolveVariablesFromProperties=auto"));
        verifyStatusCall("{{leakProbe}}", "{{" + SYSTEM_PROPERTY + "}}");

        server.resetRequests();
        call(classpathUri("resolveVariablesFromProperties=auto"));
        verifyStatusCall("from-camel-properties", "from-system-property");
    }

    @Test
    void shouldRejectAnUnknownResolveVariablesFromPropertiesValue() {
        String uri = cloudUri("resolveVariablesFromProperties=yes");

        assertThatThrownBy(() -> template.requestBody(uri, null, String.class))
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid resolveVariablesFromProperties: yes");
        WireMock.verify(0, getRequestedFor(urlPathEqualTo("/v3/status")));
    }

    /**
     * The route author can still hand a property to a cloud collection, by naming it in the endpoint URI.
     */
    @Test
    void shouldResolvePropertiesTheRouteAuthorPassesAsVariables() {
        call(cloudUri("variable.leakProbe={{leakProbe}}&variable." + SYSTEM_PROPERTY + "={{" + SYSTEM_PROPERTY + "}}"));

        verifyStatusCall("from-camel-properties", "from-system-property");
    }

    /**
     * The Accept header of the collection becomes an option of the delegate endpoint URI, so it is resolved like any
     * other header value.
     */
    @Test
    void shouldResolveTheAcceptHeaderFromTheCollectionVariables() {
        call(uri("classpath:" + COLLECTION, "getFormat", null, null));

        WireMock.verify(getRequestedFor(urlPathEqualTo("/v3/format")).withHeader("Accept", equalTo("application/json")));
    }

    /**
     * Camel resolves the property placeholders of an endpoint URI, functions included, so a placeholder left in the
     * Accept header of even a local collection must not reach the delegate endpoint URI.
     */
    @Test
    void shouldRejectAPlaceholderFunctionInTheAcceptHeader() {
        String uri = uri("classpath:" + COLLECTION, "getFunction", null, null);

        assertThatThrownBy(() -> template.requestBody(uri, null, String.class))
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{{sys:" + SYSTEM_PROPERTY + "}}")
                .hasMessageContaining("still contains a {{placeholder}}")
                .hasMessageNotContaining("from-system-property");
        WireMock.verify(0, getRequestedFor(urlPathEqualTo("/v3/function")));
    }
}
