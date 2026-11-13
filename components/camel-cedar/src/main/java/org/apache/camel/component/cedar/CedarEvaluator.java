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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import com.cedarpolicy.AuthorizationEngine;
import com.cedarpolicy.BasicAuthorizationEngine;
import com.cedarpolicy.CedarJson;
import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.EntityValidationRequest;
import com.cedarpolicy.model.ValidationRequest;
import com.cedarpolicy.model.ValidationResponse;
import com.cedarpolicy.model.entity.Entities;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.Value;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns parsed SDK objects privately. Every evaluation captures exactly one validated snapshot. */
public final class CedarEvaluator extends ServiceSupport {
    private static final Logger LOG = LoggerFactory.getLogger(CedarEvaluator.class);
    private static final TypeReference<Map<String, Value>> CONTEXT_TYPE = new TypeReference<>() {
    };
    private final CamelContext camelContext;
    private final CedarConfiguration configuration;
    private final Supplier<AuthorizationEngine> engineFactory;
    private AuthorizationEngine engine;
    private volatile Snapshot snapshot;

    private record Snapshot(PolicySet policies, Schema schema, Entities entities, String revision) {
    }

    /**
     * A failure the evaluator found itself. Its message names the check that failed and never carries input, policy
     * text or an SDK message, so it can be reported and logged as it is.
     */
    private static final class Rejected extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        Rejected(String message) {
            super(message);
        }
    }

    public CedarEvaluator(CamelContext camelContext, CedarConfiguration configuration) {
        this(camelContext, configuration, BasicAuthorizationEngine::new);
    }

    CedarEvaluator(CamelContext camelContext, CedarConfiguration configuration, Supplier<AuthorizationEngine> engineFactory) {
        this.camelContext = camelContext;
        this.configuration = configuration;
        this.engineFactory = engineFactory;
    }

    @Override
    protected void doStart() throws Exception {
        if (configuration.getPrincipal() == null || configuration.getAction() == null || configuration.getResource() == null) {
            throw new IllegalArgumentException("Cedar requires configured principal, action and resource expressions");
        }
        if (configuration.getPoliciesResource() == null || configuration.getPoliciesResource().isBlank()
                || configuration.getSchemaResource() == null || configuration.getSchemaResource().isBlank()) {
            throw new IllegalArgumentException("Cedar requires policiesResource and schemaResource");
        }
        if (configuration.getMaxResourceSize() < 1 || configuration.getMaxResourceSize() == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Cedar maxResourceSize must be between 1 and 2147483646 bytes");
        }
        try {
            engine = engineFactory.get();
        } catch (LinkageError | RuntimeException e) {
            throw nativeUnavailable(e);
        }
        try {
            // parsing an entity type name loads the native library as well, so the expressions are initialized under
            // the same guard as the engine; a configuration error of an expression propagates as it is
            configuration.getPrincipal().init(camelContext);
            configuration.getAction().init(camelContext);
            configuration.getResource().init(camelContext);
            if (configuration.getContext() != null) {
                configuration.getContext().init(camelContext);
            }
            reload();
        } catch (LinkageError e) {
            // some JNI failures surface on the first native call, after the library construction succeeded
            throw nativeUnavailable(e);
        }
    }

    private static IllegalStateException nativeUnavailable(Throwable cause) {
        String version = BasicAuthorizationEngine.class.getPackage().getImplementationVersion();
        return new IllegalStateException(
                "Cedar Java" + (version == null ? "" : " " + version) + " native library initialization failed ("
                                         + cause.getClass().getName() + ") on " + System.getProperty("os.name") + "/"
                                         + System.getProperty("os.arch")
                                         + ". Use a packaged JVM platform (tested: Linux amd64 glibc) and a writable executable native extraction location.");
    }

    @Override
    protected synchronized void doStop() {
        snapshot = null;
        engine = null;
    }

    /** Reloads trusted configured resources; a failed candidate never replaces the active snapshot. */
    public synchronized void reload() throws Exception {
        if (engine == null) {
            throw new IllegalStateException("Cedar evaluator is not started");
        }
        String kept = snapshot == null ? "no revision is active" : "the previous revision is retained";
        String step = "reading";
        String option = "policiesResource";
        try {
            String policiesText = read(configuration.getPoliciesResource());
            option = "schemaResource";
            String schemaText = read(configuration.getSchemaResource());
            option = "entitiesResource";
            String entitiesText
                    = configuration.getEntitiesResource() == null ? "[]" : read(configuration.getEntitiesResource());
            step = "parsing";
            option = "schemaResource";
            Schema schema = Schema.parse(Schema.JsonOrCedar.Cedar, schemaText);
            option = "policiesResource";
            PolicySet policies = PolicySet.parsePolicies(policiesText);
            step = "validating";
            ValidationResponse validation = engine.validate(new ValidationRequest(schema, policies));
            if (!validation.validationPassed()) {
                throw new Rejected(validationFindings(validation));
            }
            step = "parsing";
            option = "entitiesResource";
            Entities entities = Entities.parse(entitiesText);
            step = "validating";
            engine.validateEntities(new EntityValidationRequest(schema, new ArrayList<>(entities.getEntities())));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String content : List.of(policiesText, schemaText, entitiesText)) {
                byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(bytes);
            }
            snapshot = new Snapshot(policies, schema, entities, HexFormat.of().formatHex(digest.digest()));
        } catch (Exception e) {
            // SDK parse and validation messages may contain policy text or entity attributes, so they are neither
            // logged nor retained as causes; the step, the option, the exception class and the evaluator's own
            // findings are what an operator needs
            String reason = e instanceof Rejected ? e.getMessage() : e.getClass().getName();
            String message = "Cedar " + step + " of " + option + " failed (" + reason + "); " + kept;
            LOG.warn(message);
            throw new IllegalArgumentException(message);
        }
    }

    /** Policy identifiers and error kinds of a failed schema validation; the SDK messages stay out. */
    private static String validationFindings(ValidationResponse validation) {
        List<String> findings = new ArrayList<>();
        validation.success.ifPresent(success -> success.validationErrors.forEach(
                error -> findings.add(error.getPolicyId() + ": " + error.getError().code.orElse("validation error"))));
        validation.errors.ifPresent(errors -> errors.forEach(error -> findings.add(error.code.orElse("validation error"))));
        return findings.isEmpty()
                ? "policy validation against the schema failed"
                : "policy validation against the schema failed: " + String.join(", ", findings);
    }

    public String getRevision() {
        Snapshot current = snapshot;
        return current == null ? null : current.revision();
    }

    private String read(String uri) throws Exception {
        if (uri == null || uri.isBlank()) {
            throw new Rejected("the resource URI is missing");
        }
        if (!uri.startsWith("classpath:") && !uri.startsWith("file:")) {
            throw new Rejected("only classpath: and file: URIs are accepted");
        }
        try (InputStream input = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, uri)) {
            byte[] bytes = input.readNBytes(configuration.getMaxResourceSize() + 1);
            if (bytes.length > configuration.getMaxResourceSize()) {
                throw new Rejected("the resource is larger than maxResourceSize");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /** Always preserves the body. Operational and invalid-input failures are explicit ERROR results, never DENY. */
    public CedarAuthorizationResult evaluate(Exchange exchange) {
        exchange.removeProperty(CedarConstants.AUTHORIZATION_RESULT);
        exchange.getMessage().removeHeader(CedarConstants.AUTHORIZATION_RESULT);
        Snapshot current = snapshot;
        CedarAuthorizationResult result;
        if (current == null) {
            result = error(null, "NOT_STARTED");
        } else {
            try {
                EntityUID principal = uid(configuration.getPrincipal(), exchange);
                EntityUID action = uid(configuration.getAction(), exchange);
                EntityUID resource = uid(configuration.getResource(), exchange);
                Map<String, Value> context = Map.of();
                if (configuration.getContext() != null) {
                    Object value = configuration.getContext().evaluate(exchange, Object.class);
                    if (value == null) {
                        throw new Rejected("the context expression yielded null");
                    }
                    context = CedarJson.objectMapper().convertValue(value, CONTEXT_TYPE);
                    if (CedarJson.objectMapper().writeValueAsBytes(context).length > configuration.getMaxResourceSize()) {
                        throw new Rejected("the context is larger than maxResourceSize");
                    }
                }
                Entities entities = entities(current, exchange);
                AuthorizationRequest request = new AuthorizationRequest(
                        principal, action, resource,
                        Optional.of(context), Optional.of(current.schema()), true);
                var response = engine.isAuthorized(request, current.policies(), entities);
                if (response.success.isEmpty()) {
                    List<CedarAuthorizationResult.Diagnostic> diagnostics = response.errors
                            .map(errors -> errors.stream().map(error -> new CedarAuthorizationResult.Diagnostic(
                                    null, error.code.orElse("REQUEST_VALIDATION_OR_ENGINE_ERROR"))).toList())
                            .orElse(List.of());
                    if (diagnostics.isEmpty()) {
                        result = error(current.revision(), "REQUEST_VALIDATION_OR_ENGINE_ERROR");
                    } else {
                        result = new CedarAuthorizationResult(
                                CedarAuthorizationResult.Outcome.ERROR,
                                CedarAuthorizationResult.Decision.UNKNOWN, Set.of(), diagnostics, current.revision());
                    }
                } else {
                    var success = response.success.orElseThrow();
                    List<CedarAuthorizationResult.Diagnostic> diagnostics = success.getErrors().stream()
                            .map(e -> new CedarAuthorizationResult.Diagnostic(
                                    e.getPolicyId(), e.getError().code.orElse("EVALUATION_ERROR")))
                            .toList();
                    var decision = success.isAllowed()
                            ? CedarAuthorizationResult.Decision.ALLOW : CedarAuthorizationResult.Decision.DENY;
                    var outcome = !diagnostics.isEmpty() && configuration.isStrict() ? CedarAuthorizationResult.Outcome.ERROR
                            : success.isAllowed() ? CedarAuthorizationResult.Outcome.ALLOW
                            : CedarAuthorizationResult.Outcome.DENY;
                    result = new CedarAuthorizationResult(
                            outcome, decision, success.getReason(), diagnostics, current.revision());
                }
            } catch (Exception e) {
                if (e instanceof Rejected) {
                    LOG.warn("Cedar evaluation rejected its input ({}); the result is an ERROR outcome", e.getMessage());
                } else {
                    LOG.warn("Cedar evaluation failed ({}); the result is an ERROR outcome", e.getClass().getName());
                }
                result = error(current.revision(), "INPUT_OR_EVALUATION_ERROR");
            }
        }
        exchange.setProperty(CedarConstants.AUTHORIZATION_RESULT, result);
        return result;
    }

    private Entities entities(Snapshot current, Exchange exchange) throws Exception {
        if (configuration.getEntityProvider() == null) {
            // the static entities were validated against the schema when the snapshot was loaded
            return current.entities();
        }
        Set<Entity> entities = new HashSet<>(current.entities().getEntities());
        String json = configuration.getEntityProvider().entities(exchange);
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > configuration.getMaxResourceSize()) {
            throw new Rejected("the entity provider returned null or more than maxResourceSize bytes");
        }
        Set<EntityUID> ids = new HashSet<>();
        entities.forEach(entity -> ids.add(entity.getEUID()));
        for (Entity entity : Entities.parse(json).getEntities()) {
            if (!ids.add(entity.getEUID())) {
                throw new Rejected("the entity provider returned an entity UID that is already defined");
            }
            entities.add(entity);
        }
        // the entities a provider adds are validated against the schema for this request
        Entities requestEntities = new Entities(entities);
        engine.validateEntities(new EntityValidationRequest(current.schema(), new ArrayList<>(entities)));
        return requestEntities;
    }

    private static EntityUID uid(Expression expression, Exchange exchange) {
        Object value = expression.evaluate(exchange, Object.class);
        if (value instanceof EntityUID uid) {
            return uid;
        }
        if (value instanceof String text) {
            return EntityUID.parse(text)
                    .orElseThrow(() -> new Rejected("an entity expression yielded text that is not an entity UID"));
        }
        throw new Rejected("an entity expression yielded neither an EntityUID nor a String");
    }

    private static CedarAuthorizationResult error(String revision, String code) {
        return new CedarAuthorizationResult(
                CedarAuthorizationResult.Outcome.ERROR, CedarAuthorizationResult.Decision.UNKNOWN,
                Set.of(), List.of(new CedarAuthorizationResult.Diagnostic(null, code)), revision);
    }
}
