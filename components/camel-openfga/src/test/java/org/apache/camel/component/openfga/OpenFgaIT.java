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
package org.apache.camel.component.openfga;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.openfga.security.OpenFgaSecurityPolicy;
import org.apache.camel.test.infra.openfga.services.OpenFgaService;
import org.apache.camel.test.infra.openfga.services.OpenFgaServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test against a real OpenFGA server, evaluating the authorization model in
 * {@code src/test/resources/authorization-model.json}.
 * <p/>
 * The store, the model and the starting tuples are created through OpenFGA's HTTP API rather than through the
 * component, so that what the component does is measured against a graph it did not build.
 */
class OpenFgaIT extends CamelTestSupport {

    @RegisterExtension
    static OpenFgaService service = OpenFgaServiceFactory.createSingletonService();

    private static final Pattern ID = Pattern.compile("\"(?:id|authorization_model_id)\"\\s*:\\s*\"([^\"]+)\"");

    private static String storeId;
    private static String modelId;

    @BeforeAll
    static void createStoreAndModel() throws Exception {
        storeId = extractId(post("/stores", "{\"name\":\"camel-openfga-it\"}"));
        String model;
        try (InputStream in = OpenFgaIT.class.getResourceAsStream("/authorization-model.json")) {
            model = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        modelId = extractId(post("/stores/" + storeId + "/authorization-models", model));

        // anne owns the budget, which the model turns into reader as well; bob may only read the roadmap; and the
        // announcement is readable by user:* - the public-access tuple that makes the wildcard test meaningful
        post("/stores/" + storeId + "/write",
                "{\"authorization_model_id\":\"" + modelId + "\",\"writes\":{\"tuple_keys\":["
                                              + "{\"user\":\"user:anne\",\"relation\":\"owner\",\"object\":\"document:budget\"},"
                                              + "{\"user\":\"user:bob\",\"relation\":\"reader\",\"object\":\"document:roadmap\"},"
                                              + "{\"user\":\"user:*\",\"relation\":\"reader\",\"object\":\"document:announcement\"}"
                                              + "]}}");
    }

    private static String post(String path, String body) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create(service.getOpenFgaUrl() + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new IOException("OpenFGA rejected " + path + ": " + response.body());
        }
        return response.body();
    }

    private static String extractId(String json) throws IOException {
        Matcher matcher = ID.matcher(json);
        if (!matcher.find()) {
            throw new IOException("No id in the OpenFGA response: " + json);
        }
        return matcher.group(1);
    }

    private static String openfga(String operation, String options) {
        String uri = "openfga:" + operation + "?apiUrl=" + service.getOpenFgaUrl()
                     + "&storeId=" + storeId + "&authorizationModelId=" + modelId;
        return options.isEmpty() ? uri : uri + "&" + options;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        OpenFgaSecurityPolicy policy = new OpenFgaSecurityPolicy(service.getOpenFgaUrl(), storeId);
        policy.setAuthorizationModelId(modelId);
        policy.setRelation("writer");
        policy.setUser("user:${exchangeProperty.subject}");
        policy.setObject("document:${header.documentId}");

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:guarded")
                        .policy(policy)
                        .to("mock:allowed");
            }
        };
    }

    private Exchange check(String subject, String documentId, String relation) {
        return template.request(
                openfga("check", "relation=" + relation + "&user=user:${header.subject}"
                                 + "&object=document:${header.documentId}"),
                e -> {
                    e.getMessage().setHeader("subject", subject);
                    e.getMessage().setHeader("documentId", documentId);
                });
    }

    @Test
    void allowsARelationTheModelInfersFromAnother() {
        // anne is only an owner; reader is a union that includes owner, so the check resolves through the model
        Exchange out = check("anne", "budget", "reader");

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isEqualTo(true);
        assertThat(out.getMessage().getHeader(OpenFgaConstants.STORE_ID)).isEqualTo(storeId);
    }

    @Test
    void deniesARelationNoTupleGrants() {
        Exchange out = check("bob", "budget", "reader");

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isEqualTo(false);
        assertThat(out.getMessage().getHeader(OpenFgaConstants.DENY_REASON)).isEqualTo("denied");
    }

    @Test
    void refusesAWildcardSubjectEvenThoughTheServerWouldAnswerTrue() throws Exception {
        // the store really does hold user:* reader on document:announcement, so OpenFGA would answer true here - which
        // is the point: "may everyone read this" is not the question the route asked
        assertThat(rawCheck("user:*", "reader", "document:announcement")).contains("\"allowed\":true");

        Exchange out = check("*", "announcement", "reader");

        assertThat(out.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isEqualTo(false);
        assertThat(out.getMessage().getHeader(OpenFgaConstants.DENY_REASON)).isEqualTo("wildcard-subject");
    }

    private static String rawCheck(String user, String relation, String object) throws Exception {
        return post("/stores/" + storeId + "/check",
                "{\"authorization_model_id\":\"" + modelId + "\",\"tuple_key\":{\"user\":\"" + user
                                                     + "\",\"relation\":\"" + relation + "\",\"object\":\"" + object
                                                     + "\"}}");
    }

    @Test
    void refusesAnObjectTheServerRejectsEvenWithFailOpenEnabled() throws Exception {
        // document:x#y gets past the component's own guards - '#' is legitimate in a userset subject - but OpenFGA
        // rejects it as an object with HTTP 400. Prove the server really does reject it...
        assertThat(rawCheckStatus("user:anne", "reader", "document:x#y")).isEqualTo(400);

        // ...and that failOpen does not turn that rejection into an allow, which is the bypass a caller able to
        // influence the identifier would otherwise have
        Exchange out = template.request(
                openfga("check", "relation=reader&user=user:anne&object=document:${header.documentId}&failOpen=true"),
                e -> e.getMessage().setHeader("documentId", "x#y"));

        assertThat(out.getException()).isInstanceOf(OpenFgaEvaluationException.class);
        assertThat(out.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isNull();
    }

    @Test
    void proceedsWhenTheServerIsUnreachableAndFailOpenIsEnabled() {
        // the other half of the same rule: an unreachable decision point is what failOpen is actually for
        Exchange out = template.request(
                "openfga:check?apiUrl=http://localhost:1&storeId=" + storeId
                                        + "&relation=reader&user=user:anne&object=document:budget&failOpen=true"
                                        + "&connectTimeout=500&readTimeout=500&maxRetries=0",
                e -> {
                });

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isEqualTo(true);
    }

    private static int rawCheckStatus(String user, String relation, String object) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create(service.getOpenFgaUrl() + "/stores/" + storeId + "/check"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"authorization_model_id\":\"" + modelId + "\",\"tuple_key\":{\"user\":\"" + user
                                                                  + "\",\"relation\":\"" + relation
                                                                  + "\",\"object\":\"" + object + "\"}}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    @Test
    void aContextualTupleGrantsAndIsNotStored() {
        // dave holds nothing on the budget in the store
        assertThat(check("dave", "budget", "reader").getMessage().getHeader(OpenFgaConstants.ALLOWED))
                .isEqualTo(false);

        // the same check with a contextual tuple asserting ownership answers true: a contextual tuple is read exactly
        // like a stored one, which is precisely why the option is endpoint-only and never taken from the message
        Exchange granted = template.request(
                openfga("check", "relation=reader&user=user:dave&object=document:budget"
                                 + "&contextualTuples=user:dave,owner,document:budget"),
                e -> {
                });
        assertThat(granted.getException()).isNull();
        assertThat(granted.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isEqualTo(true);

        // and nothing was written: without the tuple the plain check denies again
        assertThat(check("dave", "budget", "reader").getMessage().getHeader(OpenFgaConstants.ALLOWED))
                .isEqualTo(false);
    }

    @Test
    void listsTheObjectsASubjectCanRead() {
        Exchange out = template.request(
                openfga("listObjects", "relation=reader&user=user:anne&type=document"), e -> {
                });

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getBody(List.class))
                .contains("document:budget", "document:announcement")
                .doesNotContain("document:roadmap");
    }

    @Test
    void listsTheRelationsASubjectHoldsOnAnObject() {
        Exchange out = template.request(
                openfga("listRelations", "user=user:anne&object=document:budget&relations=reader,writer,owner"), e -> {
                });

        assertThat(out.getException()).isNull();
        // owner is written; reader and writer are both unions that include owner
        assertThat(out.getMessage().getBody(List.class)).containsExactlyInAnyOrder("reader", "writer", "owner");
    }

    @Test
    void listsTheSubjectsThatHoldARelationOnAnObject() {
        Exchange out = template.request(
                openfga("listUsers", "object=document:roadmap&relation=reader&userFilters=user"), e -> {
                });

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getBody(List.class)).contains("user:bob");
    }

    @Test
    void filtersACollectionDownToWhatASubjectMayRead() {
        Exchange out = template.request(
                openfga("batchCheck", "relation=reader&user=user:bob"),
                e -> e.getMessage().setBody(
                        List.of("document:budget", "document:roadmap", "document:announcement")));

        assertThat(out.getException()).isNull();
        // bob was granted the roadmap, and the announcement is public
        assertThat(out.getMessage().getBody(List.class))
                .containsExactly("document:roadmap", "document:announcement");
    }

    @Test
    void grantsAndThenRevokesAccessThroughTheRelationshipGraph() {
        assertThat(check("carol", "budget", "reader").getMessage().getHeader(OpenFgaConstants.ALLOWED))
                .isEqualTo(false);

        Exchange granted = template.request(
                openfga("writeTuples", "user=user:${header.subject}&relation=reader"
                                       + "&object=document:${header.documentId}"),
                e -> {
                    e.getMessage().setHeader("subject", "carol");
                    e.getMessage().setHeader("documentId", "budget");
                });
        assertThat(granted.getException()).isNull();
        assertThat(granted.getMessage().getHeader(OpenFgaConstants.WRITTEN_TUPLES)).isEqualTo(1);

        assertThat(check("carol", "budget", "reader").getMessage().getHeader(OpenFgaConstants.ALLOWED))
                .isEqualTo(true);

        Exchange revoked = template.request(
                openfga("deleteTuples", ""),
                e -> e.getMessage().setBody(
                        Map.of("user", "user:carol", "relation", "reader", "object", "document:budget")));
        assertThat(revoked.getException()).isNull();
        assertThat(revoked.getMessage().getHeader(OpenFgaConstants.DELETED_TUPLES)).isEqualTo(1);

        assertThat(check("carol", "budget", "reader").getMessage().getHeader(OpenFgaConstants.ALLOWED))
                .isEqualTo(false);
    }

    @Test
    void letsAGuardedRouteRunForASubjectThatHoldsTheRelation() throws Exception {
        MockEndpoint allowed = getMockEndpoint("mock:allowed");
        allowed.expectedMessageCount(1);

        Exchange out = template.request("direct:guarded", e -> {
            e.setProperty("subject", "anne");
            e.getMessage().setHeader("documentId", "budget");
        });

        assertThat(out.getException()).isNull();
        allowed.assertIsSatisfied();
    }

    @Test
    void stopsAGuardedRouteForASubjectThatDoesNot() throws Exception {
        MockEndpoint allowed = getMockEndpoint("mock:allowed");
        allowed.expectedMessageCount(0);

        Exchange out = template.request("direct:guarded", e -> {
            e.setProperty("subject", "bob");
            e.getMessage().setHeader("documentId", "budget");
        });

        assertThat(out.getException()).isInstanceOf(CamelAuthorizationException.class);
        allowed.assertIsSatisfied();
    }
}
