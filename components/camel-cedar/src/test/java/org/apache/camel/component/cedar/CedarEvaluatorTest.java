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
package org.apache.camel.component.cedar;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.cedarpolicy.AuthorizationEngine;
import com.cedarpolicy.BasicAuthorizationEngine;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real JNI evaluation on the executed feasibility platform; other packaged platforms need dedicated CI. */
@EnabledIf("glibcAvailable")
@EnabledOnOs(OS.LINUX)
@EnabledIfSystemProperty(named = "os.arch", matches = "amd64|x86_64")
class CedarEvaluatorTest {
    static boolean glibcAvailable() {
        return Files.exists(Path.of("/lib64/ld-linux-x86-64.so.2"))
                || Files.exists(Path.of("/lib/x86_64-linux-gnu/ld-linux-x86-64.so.2"));
    }

    static final String PERMIT
            = "permit(principal == User::\"alice\", action == Action::\"read\", resource == Doc::\"budget\");";
    static final String SCHEMA
            = "entity User; entity Doc; action \"read\" appliesTo {principal: User, resource: Doc, context: {}};";

    static class Fixture implements AutoCloseable {
        final DefaultCamelContext context = new DefaultCamelContext();
        final CedarConfiguration config = new CedarConfiguration();
        final Path directory;
        final Path policies;
        final Path schema;
        final CedarEvaluator evaluator;

        Fixture(String policy, String schemaText) throws Exception {
            this(policy, schemaText, null);
        }

        Fixture(String policy, String schemaText, Supplier<AuthorizationEngine> engineFactory) throws Exception {
            directory = Files.createTempDirectory("camel-cedar-");
            policies = directory.resolve("policies.cedar");
            schema = directory.resolve("schema.cedarschema");
            Files.writeString(policies, policy);
            Files.writeString(schema, schemaText);
            config.setPoliciesResource(policies.toUri().toString());
            config.setSchemaResource(schema.toUri().toString());
            config.setPrincipal(ExpressionBuilder.constantExpression("User::\"alice\""));
            config.setAction(ExpressionBuilder.constantExpression("Action::\"read\""));
            config.setResource(ExpressionBuilder.constantExpression("Doc::\"budget\""));
            evaluator = engineFactory == null
                    ? new CedarEvaluator(context, config) : new CedarEvaluator(context, config, engineFactory);
        }

        Exchange exchange() {
            return new DefaultExchange(context);
        }

        void start() throws Exception {
            evaluator.start();
        }

        @Override
        public void close() throws Exception {
            evaluator.stop();
            context.close();
            try (var files = Files.list(directory)) {
                for (Path file : files.toList()) {
                    Files.delete(file);
                }
            }
            Files.delete(directory);
        }
    }

    @Test
    void permitDefaultDenyAndForbid() throws Exception {
        try (Fixture fixture = new Fixture(PERMIT, SCHEMA)) {
            fixture.start();
            assertThat(fixture.evaluator.evaluate(fixture.exchange()).isAllowed()).isTrue();
            Files.writeString(fixture.policies, "");
            fixture.evaluator.reload();
            assertThat(fixture.evaluator.evaluate(fixture.exchange()).outcome())
                    .isEqualTo(CedarAuthorizationResult.Outcome.DENY);
            Files.writeString(fixture.policies, PERMIT + "forbid(principal,action,resource);");
            fixture.evaluator.reload();
            assertThat(fixture.evaluator.evaluate(fixture.exchange()).outcome())
                    .isEqualTo(CedarAuthorizationResult.Outcome.DENY);
        }
    }

    @Test
    void strictEvaluationErrorsRetainRawAllowAndSanitizedDiagnostics() throws Exception {
        String schema = SCHEMA.replace("context: {}", "context: {divisor: Long}");
        try (Fixture fixture = new Fixture(
                PERMIT + "forbid(principal,action,resource) when { 9223372036854775807 + context.divisor == 0 };", schema)) {
            fixture.config.setContext(ExpressionBuilder.constantExpression(Map.of("divisor", 1)));
            fixture.start();
            var result = fixture.evaluator.evaluate(fixture.exchange());
            assertThat(result.outcome()).isEqualTo(CedarAuthorizationResult.Outcome.ERROR);
            assertThat(result.engineDecision()).isEqualTo(CedarAuthorizationResult.Decision.ALLOW);
            assertThat(result.diagnostics()).isNotEmpty();
            fixture.config.setStrict(false);
            var compatibility = fixture.evaluator.evaluate(fixture.exchange());
            assertThat(compatibility.isAllowed()).isTrue();
            assertThat(compatibility.diagnostics()).isEqualTo(result.diagnostics());
        }
    }

    public record RequestContext(long level) {
    }

    @Test
    void mapsPojoContextAndPreservesBodyAndClearsStaleOutputs() throws Exception {
        try (Fixture fixture = new Fixture(
                "permit(principal,action,resource) when { context.level > 2 };",
                SCHEMA.replace("context: {}", "context: {level: Long}"))) {
            fixture.config.setContext(ExpressionBuilder.constantExpression(new RequestContext(3)));
            fixture.start();
            Exchange exchange = fixture.exchange();
            Object body = new ByteArrayInputStream(new byte[] { 1, 2, 3 });
            exchange.getMessage().setBody(body);
            exchange.getMessage().setHeader(CedarConstants.AUTHORIZATION_RESULT, "untrusted");
            var result = fixture.evaluator.evaluate(exchange);
            assertThat(result.isAllowed()).isTrue();
            assertThat(exchange.getMessage().getBody()).isSameAs(body);
            assertThat(exchange.getMessage().getHeader(CedarConstants.AUTHORIZATION_RESULT)).isNull();
            fixture.config.setPrincipal(ExpressionBuilder.constantExpression("not a uid; permit(principal,action,resource);"));
            assertThat(fixture.evaluator.evaluate(exchange).outcome()).isEqualTo(CedarAuthorizationResult.Outcome.ERROR);
            assertThat(exchange.getProperty(CedarConstants.AUTHORIZATION_RESULT)).isNotSameAs(result);
            assertThat(exchange.getProperty(CedarConstants.AUTHORIZATION_RESULT).toString()).doesNotContain("alice",
                    "not a uid");
        }
    }

    @Test
    void validatesSchemaAndRetainsRevisionAfterInvalidReload() throws Exception {
        try (Fixture fixture = new Fixture(PERMIT, SCHEMA)) {
            fixture.start();
            String revision = fixture.evaluator.getRevision();
            Files.writeString(fixture.policies, "permit(principal,action,resource) when {principal.secret == true};");
            assertThatThrownBy(fixture.evaluator::reload)
                    .hasMessageContaining(
                            "validating of policiesResource failed (policy validation against the schema failed: ")
                    .hasMessageContaining("the previous revision is retained").hasNoCause();
            assertThat(fixture.evaluator.getRevision()).isEqualTo(revision);
            assertThat(fixture.evaluator.evaluate(fixture.exchange()).isAllowed()).isTrue();
            Files.writeString(fixture.policies, "broken syntax");
            assertThatThrownBy(fixture.evaluator::reload).isInstanceOf(IllegalArgumentException.class);
        }
        try (Fixture fixture = new Fixture("broken syntax", SCHEMA)) {
            assertThatThrownBy(fixture::start).hasMessageContaining("parsing of policiesResource failed (")
                    .hasMessageContaining("no revision is active").hasMessageNotContaining("broken syntax");
            assertThat(fixture.evaluator.getRevision()).isNull();
        }
        try (Fixture fixture = new Fixture(PERMIT, "invalid schema")) {
            assertThatThrownBy(fixture::start).hasMessageContaining("parsing of schemaResource failed (");
        }
        try (Fixture fixture = new Fixture(PERMIT, SCHEMA)) {
            Path entities = fixture.directory.resolve("entities.json");
            Files.writeString(entities, "invalid JSON");
            fixture.config.setEntitiesResource(entities.toUri().toString());
            assertThatThrownBy(fixture::start).hasMessageContaining("parsing of entitiesResource failed (");
        }
    }

    @Test
    void staticEntitiesAreValidatedAtLoadNotPerExchange() throws Exception {
        AtomicInteger entityValidations = new AtomicInteger();
        AuthorizationEngine real = new BasicAuthorizationEngine();
        AuthorizationEngine counting = (AuthorizationEngine) Proxy.newProxyInstance(
                AuthorizationEngine.class.getClassLoader(), new Class<?>[] { AuthorizationEngine.class },
                (proxy, method, args) -> {
                    if ("validateEntities".equals(method.getName())) {
                        entityValidations.incrementAndGet();
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        String schema
                = "entity Group; entity User in [Group]; entity Doc; action \"read\" appliesTo {principal: User, resource: Doc, context: {}};";
        try (Fixture fixture
                = new Fixture("permit(principal in Group::\"admins\",action,resource);", schema, () -> counting)) {
            Path entities = fixture.directory.resolve("entities.json");
            Files.writeString(entities, "[{\"uid\":{\"type\":\"Group\",\"id\":\"admins\"},\"attrs\":{},\"parents\":[]}]");
            fixture.config.setEntitiesResource(entities.toUri().toString());
            fixture.start();
            assertThat(entityValidations).hasValue(1);
            // without a provider the request uses the static entities validated at load; alice is in no group
            assertThat(fixture.evaluator.evaluate(fixture.exchange()).outcome())
                    .isEqualTo(CedarAuthorizationResult.Outcome.DENY);
            assertThat(fixture.evaluator.evaluate(fixture.exchange()).outcome())
                    .isEqualTo(CedarAuthorizationResult.Outcome.DENY);
            assertThat(entityValidations).hasValue(1);
            // the entities a provider adds are validated for the request
            fixture.config.setEntityProvider(
                    exchange -> "[{\"uid\":{\"type\":\"User\",\"id\":\"alice\"},\"attrs\":{},\"parents\":[{\"type\":\"Group\",\"id\":\"admins\"}]}]");
            assertThat(fixture.evaluator.evaluate(fixture.exchange()).isAllowed()).isTrue();
            assertThat(entityValidations).hasValue(2);
        }
    }

    @Test
    void entityHierarchyAndProviderValidation() throws Exception {
        String schema
                = "entity Group; entity User in [Group]; entity Doc; action \"read\" appliesTo {principal: User, resource: Doc, context: {}};";
        try (Fixture fixture = new Fixture("permit(principal in Group::\"admins\",action,resource);", schema)) {
            Path entities = fixture.directory.resolve("entities.json");
            Files.writeString(entities, "[{\"uid\":{\"type\":\"Group\",\"id\":\"admins\"},\"attrs\":{},\"parents\":[]}]");
            fixture.config.setEntitiesResource(entities.toUri().toString());
            fixture.config.setEntityProvider(
                    exchange -> "[{\"uid\":{\"type\":\"User\",\"id\":\"alice\"},\"attrs\":{},\"parents\":[{\"type\":\"Group\",\"id\":\"admins\"}]}]");
            fixture.start();
            assertThat(fixture.evaluator.evaluate(fixture.exchange()).isAllowed()).isTrue();
            fixture.config.setEntityProvider(
                    exchange -> "[{\"uid\":{\"type\":\"Unknown\",\"id\":\"entity\"},\"attrs\":{},\"parents\":[]}]");
            assertThat(fixture.evaluator.evaluate(fixture.exchange()).outcome())
                    .isEqualTo(CedarAuthorizationResult.Outcome.ERROR);
            fixture.config.setEntityProvider(exchange -> "invalid entity json");
            assertThat(fixture.evaluator.evaluate(fixture.exchange()).outcome())
                    .isEqualTo(CedarAuthorizationResult.Outcome.ERROR);
        }
    }

    @Test
    void rejectsInvalidRequestsAndResourceBounds() throws Exception {
        try (Fixture fixture = new Fixture(PERMIT, SCHEMA)) {
            fixture.start();
            fixture.config.setAction(ExpressionBuilder.constantExpression("Action::\"unknown\""));
            assertThat(fixture.evaluator.evaluate(fixture.exchange()).outcome())
                    .isEqualTo(CedarAuthorizationResult.Outcome.ERROR);
            fixture.config.setMaxResourceSize(1);
            assertThatThrownBy(fixture.evaluator::reload).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reading of policiesResource failed (the resource is larger than maxResourceSize)");
        }
        try (Fixture fixture = new Fixture(PERMIT, SCHEMA)) {
            fixture.config.setPrincipal(null);
            assertThatThrownBy(fixture::start).hasMessageContaining("expressions");
        }
    }

    @Test
    void policyNeverInvokesDownstreamOnDenyOrError() throws Exception {
        try (Fixture fixture = new Fixture("", SCHEMA)) {
            AtomicInteger downstream = new AtomicInteger();
            CedarSecurityProcessor processor
                    = new CedarSecurityProcessor(exchange -> downstream.incrementAndGet(), fixture.evaluator);
            processor.start();
            try {
                Exchange denied = fixture.exchange();
                processor.process(denied);
                assertThat(denied.getException()).isInstanceOf(CamelAuthorizationException.class);
                fixture.config.setPrincipal(ExpressionBuilder.constantExpression("invalid"));
                Exchange error = fixture.exchange();
                processor.process(error);
                assertThat(error.getException()).isInstanceOf(CamelAuthorizationException.class);
                assertThat(downstream).hasValue(0);
            } finally {
                processor.stop();
            }
        }
    }

    @Test
    void concurrentReloadAndEvaluationUseCoherentSnapshots() throws Exception {
        try (Fixture fixture = new Fixture(PERMIT, SCHEMA)) {
            fixture.start();
            String allowRevision = fixture.evaluator.getRevision();
            Files.writeString(fixture.policies, "forbid(principal,action,resource);");
            fixture.evaluator.reload();
            String denyRevision = fixture.evaluator.getRevision();
            try (var executor = Executors.newFixedThreadPool(8)) {
                List<Callable<Boolean>> tasks = new ArrayList<>();
                CountDownLatch evaluating = new CountDownLatch(1);
                tasks.add(() -> {
                    if (!evaluating.await(10, TimeUnit.SECONDS)) {
                        return false;
                    }
                    for (int i = 0; i < 20; i++) {
                        Files.writeString(fixture.policies, i % 2 == 0 ? PERMIT : "forbid(principal,action,resource);");
                        fixture.evaluator.reload();
                    }
                    return true;
                });
                for (int i = 0; i < 200; i++) {
                    tasks.add(() -> {
                        evaluating.countDown();
                        var result = fixture.evaluator.evaluate(fixture.exchange());
                        return result.revision().equals(allowRevision)
                                ? result.isAllowed()
                                : result.revision().equals(denyRevision)
                                        && result.outcome() == CedarAuthorizationResult.Outcome.DENY;
                    });
                }
                for (var result : executor.invokeAll(tasks)) {
                    assertThat(result.get()).isTrue();
                }
            }
        }
    }

    @Test
    void policySharesLifecycleAndReloadsActiveRoutes() throws Exception {
        try (Fixture fixture = new Fixture(PERMIT, SCHEMA)) {
            CedarSecurityPolicy policy = new CedarSecurityPolicy();
            policy.setPoliciesResource(fixture.config.getPoliciesResource());
            policy.setSchemaResource(fixture.config.getSchemaResource());
            policy.setPrincipal(fixture.config.getPrincipal());
            policy.setAction(fixture.config.getAction());
            policy.setResource(fixture.config.getResource());
            AtomicInteger downstream = new AtomicInteger();
            CedarSecurityProcessor first
                    = new CedarSecurityProcessor(e -> downstream.incrementAndGet(), policy, fixture.context);
            CedarSecurityProcessor second
                    = new CedarSecurityProcessor(e -> downstream.incrementAndGet(), policy, fixture.context);
            first.start();
            second.start();
            try {
                Exchange allowed = fixture.exchange();
                first.process(allowed);
                assertThat(downstream).hasValue(1);
                Files.writeString(fixture.policies, "forbid(principal,action,resource);");
                policy.reload();
                Exchange denied = fixture.exchange();
                second.process(denied);
                assertThat(denied.getException()).isNotNull();
                first.stop();
                Files.writeString(fixture.policies, PERMIT);
                policy.reload();
                Exchange stillStarted = fixture.exchange();
                second.process(stillStarted);
                assertThat(stillStarted.getException()).isNull();
                assertThat(downstream).hasValue(2);
            } finally {
                first.stop();
                second.stop();
            }
            assertThatThrownBy(policy::reload).hasMessageContaining("no started routes");
        }
    }

    @Test
    void producerUsesEndpointLifecycleAndPreservesPojoBody() throws Exception {
        try (Fixture fixture = new Fixture(PERMIT, SCHEMA)) {
            CedarComponent component = new CedarComponent();
            component.setCamelContext(fixture.context);
            CedarEndpoint endpoint = new CedarEndpoint("cedar:authorize", component, fixture.config);
            endpoint.start();
            Producer producer = endpoint.createProducer();
            producer.start();
            try {
                Exchange exchange = fixture.exchange();
                Object body = new RequestContext(4);
                exchange.getMessage().setBody(body);
                producer.process(exchange);
                assertThat(
                        exchange.getProperty(CedarConstants.AUTHORIZATION_RESULT, CedarAuthorizationResult.class).isAllowed())
                        .isTrue();
                assertThat(exchange.getMessage().getBody()).isSameAs(body);
                String revision = endpoint.getEvaluator().getRevision();
                Files.writeString(fixture.policies, "forbid(principal,action,resource);");
                endpoint.reload();
                assertThat(endpoint.getEvaluator().getRevision()).isNotEqualTo(revision);
                producer.process(exchange);
                assertThat(exchange.getProperty(CedarConstants.AUTHORIZATION_RESULT, CedarAuthorizationResult.class).outcome())
                        .isEqualTo(CedarAuthorizationResult.Outcome.DENY);
            } finally {
                producer.stop();
                endpoint.stop();
            }
            assertThat(endpoint.getEvaluator()).isNull();
        }
    }

    @Test
    void rejectsRemoteResourcesWithoutReplacingActiveRevision() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/policy", request -> {
            requests.incrementAndGet();
            byte[] policy = PERMIT.getBytes(StandardCharsets.UTF_8);
            request.sendResponseHeaders(200, policy.length);
            try (var output = request.getResponseBody()) {
                output.write(policy);
            }
        });
        server.start();
        try (Fixture fixture = new Fixture(PERMIT, SCHEMA)) {
            fixture.start();
            String revision = fixture.evaluator.getRevision();
            fixture.config.setPoliciesResource("http://127.0.0.1:" + server.getAddress().getPort() + "/policy");
            assertThatThrownBy(fixture.evaluator::reload).isInstanceOf(IllegalArgumentException.class).hasNoCause()
                    .hasMessageContaining("reading of policiesResource failed (only classpath: and file: URIs are accepted)");
            assertThat(requests).hasValue(0);
            assertThat(fixture.evaluator.getRevision()).isEqualTo(revision);
            assertThat(fixture.evaluator.evaluate(fixture.exchange()).isAllowed()).isTrue();
        } finally {
            server.stop(0);
        }
    }
}
