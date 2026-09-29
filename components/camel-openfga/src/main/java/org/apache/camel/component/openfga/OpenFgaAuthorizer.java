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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.configuration.ClientCheckOptions;
import dev.openfga.sdk.api.model.ConsistencyPreference;
import dev.openfga.sdk.errors.FgaError;
import dev.openfga.sdk.errors.SdkSerializationException;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asks OpenFGA whether an exchange may proceed, and records the answer on it.
 * <p/>
 * Shared by the {@code openfga:check} producer and by {@code OpenFgaSecurityPolicy} so that both resolve the subject
 * the same way, apply the same guards, and leave the same headers behind - a route behaves identically whichever of the
 * two enforces it.
 * <p/>
 * <b>What is asked is fixed by configuration.</b> The store, the authorization model revision, the relation and the
 * expression that produces the subject all come from the endpoint or the policy bean. A message can influence the
 * <em>object</em> - naming which resource it wants is its job - but it cannot choose the store that judges it, the
 * model revision, the permission demanded, or who it is.
 */
public class OpenFgaAuthorizer {

    private static final Logger LOG = LoggerFactory.getLogger(OpenFgaAuthorizer.class);

    /**
     * Added to the worst case the SDK's own timeouts and retries allow for, as the bound on how long one exchange may
     * wait. It is not a tuning knob: it exists so that a routing thread cannot park indefinitely if a future never
     * completes, and it is deliberately generous enough never to fire before the SDK has given up on its own.
     */
    private static final long AWAIT_SAFETY_MARGIN_MILLIS = 5000;

    private final OpenFgaClient client;
    private final String storeId;
    private final String authorizationModelId;
    private final Expression user;
    private final Expression object;
    private final Expression relation;
    private final ConsistencyPreference consistency;
    private final boolean failOpen;
    private final long awaitTimeoutMillis;

    public OpenFgaAuthorizer(OpenFgaClient client, OpenFgaConfiguration configuration, CamelContext camelContext) {
        this.client = ObjectHelper.notNull(client, "client");
        this.storeId = configuration.getStoreId();
        this.authorizationModelId = configuration.getAuthorizationModelId();
        this.user = compile(camelContext, configuration.getUser());
        this.object = compile(camelContext, configuration.getObject());
        this.relation = compile(camelContext, configuration.getRelation());
        this.consistency = parseConsistency(configuration.getConsistency());
        this.failOpen = configuration.isFailOpen();
        this.awaitTimeoutMillis = awaitTimeout(configuration);
    }

    /**
     * Compiles an option into a Simple expression, so a literal is used as written and a
     * <code>${exchangeProperty.subject}</code> resolves per exchange.
     * <p/>
     * Evaluating the option is safe because the option is written by the route author, who is trusted to run arbitrary
     * code anyway. What the expression <em>reads</em> is the thing to be careful about, and that is a matter for the
     * documentation of {@code user} rather than for this method.
     */
    private static Expression compile(CamelContext camelContext, String text) {
        if (ObjectHelper.isEmpty(text)) {
            return null;
        }
        Expression expression = camelContext.resolveLanguage("simple").createExpression(text);
        expression.init(camelContext);
        return expression;
    }

    private static ConsistencyPreference parseConsistency(String consistency) {
        if (ObjectHelper.isEmpty(consistency)) {
            return null;
        }
        ConsistencyPreference parsed = ConsistencyPreference.fromValue(consistency);
        // fromValue does not reject an unknown value, it answers UNKNOWN_DEFAULT_OPEN_API - and its wire form,
        // "unknown_default_open_api", would then be sent on every request. The enums= on the @UriParam is only
        // metadata for the catalog and tooling, so nothing else stops a typo like "higher_consistency" either
        if (parsed == null || parsed == ConsistencyPreference.UNKNOWN_DEFAULT_OPEN_API) {
            throw new IllegalArgumentException(
                    "Unknown consistency '" + consistency + "'; expected one of "
                                               + ConsistencyPreference.UNSPECIFIED.getValue() + ", "
                                               + ConsistencyPreference.MINIMIZE_LATENCY.getValue() + ", "
                                               + ConsistencyPreference.HIGHER_CONSISTENCY.getValue());
        }
        return parsed;
    }

    /**
     * The longest one exchange may wait for an answer: what the SDK itself allows for - a connect and a request per
     * attempt, across the first attempt and every retry - plus a margin.
     */
    private static long awaitTimeout(OpenFgaConfiguration configuration) {
        long perAttempt = configuration.getConnectTimeout() + configuration.getReadTimeout();
        long attempts = Math.max(0, configuration.getMaxRetries()) + 1L;
        return perAttempt * attempts + AWAIT_SAFETY_MARGIN_MILLIS;
    }

    /**
     * Asks OpenFGA whether the exchange may proceed and records the verdict on it.
     *
     * @param  exchange                   the exchange to authorize
     * @return                            true when the relationship exists and the exchange may proceed
     * @throws OpenFgaEvaluationException when no answer could be obtained and {@code failOpen} is false
     */
    public boolean check(Exchange exchange) throws OpenFgaEvaluationException {
        // Clear first, and before anything is resolved. A verdict a message arrived with is a claim, not evidence, and
        // overwriting at the end would not be enough: the paths that throw never reach the end, and a route that
        // handles the exception would carry on with the sender's own verdict still on the message. Clearing before the
        // expressions are evaluated also means an endpoint mistakenly configured to read one of these headers back
        // resolves to blank and is denied, rather than inheriting a subject from an earlier decision.
        clearDecisionHeaders(exchange);

        String resolvedUser = resolveUser(exchange);
        String resolvedRelation = resolveRelation(exchange);
        String resolvedObject = resolveObject(exchange);
        if (resolvedUser == null || resolvedRelation == null || resolvedObject == null) {
            return false;
        }

        ClientCheckRequest request = new ClientCheckRequest()
                .user(resolvedUser)
                .relation(resolvedRelation)
                ._object(resolvedObject);

        Boolean allowed;
        try {
            allowed = await(exchange, "check " + resolvedRelation + " on " + resolvedObject,
                    () -> client.check(request, checkOptions())).getAllowed();
        } catch (OpenFgaEvaluationException e) {
            if (failOpen && isDecisionPointUnavailable(e)) {
                LOG.warn("Could not ask OpenFGA whether {} has {} on {}, allowing the exchange to proceed because"
                         + " failOpen is enabled. Reason: {}",
                        resolvedUser, resolvedRelation, resolvedObject, e.getMessage());
                setDecision(exchange, true);
                // the verdict header alone cannot distinguish "OpenFGA allowed this" from "OpenFGA was never
                // reached and we were told to proceed". An auditor asking which exchanges went through
                // unauthorized needs something to filter on, not a log line
                exchange.getMessage().setHeader(OpenFgaConstants.FAILED_OPEN, true);
                return true;
            }
            if (failOpen) {
                // worth saying out loud: the operator asked to fail open and did not get it, and the reason is that
                // this was not the kind of failure failOpen is for
                LOG.warn("OpenFGA rejected the question about {} having {} on {}, so the exchange is denied even"
                         + " though failOpen is enabled: a rejected request is not an unavailable decision point."
                         + " Reason: {}",
                        resolvedUser, resolvedRelation, resolvedObject, e.getMessage());
            }
            throw e;
        }

        // an answer with no verdict in it is not an answer; treat it as a deny rather than as a failure, so that
        // failOpen cannot turn a malformed response into an allow
        boolean permitted = Boolean.TRUE.equals(allowed);
        setDecision(exchange, permitted);
        if (!permitted) {
            exchange.getMessage().setHeader(OpenFgaConstants.DENY_REASON, "denied");
        }
        return permitted;
    }

    /**
     * Whether a failure means OpenFGA could not answer, which is the only thing {@code failOpen} is for.
     * <p/>
     * An HTTP 4xx is not that. It is OpenFGA telling us the question was malformed (400), that we may not ask it
     * (401/403), or that what we asked about does not exist (404) - and none of those is a decision point that has gone
     * away. Letting {@code failOpen} cover them would be a bypass: an object identifier that gets past
     * {@link OpenFgaIdentifiers} but that the server still rejects - {@code document:a:b} or {@code document:x#y}, both
     * HTTP 400 on OpenFGA 1.21.0 - would turn into an allow for any caller who could influence it.
     * <p/>
     * A 429 is the exception that proves the rule: it is a 4xx by status class, but what it says is "not right now",
     * which is precisely a decision point that could not answer.
     */
    private static boolean isDecisionPointUnavailable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof FgaError error) {
                // 429 says "not right now" and a 5xx says the server broke; both are an absent decision point. Every
                // other 4xx is OpenFGA telling us the question was malformed or may not be asked, which is not
                return error.isRateLimitError() || error.isServerError();
            }
            if (cause instanceof SdkSerializationException) {
                // our own input could not be turned into a request, so OpenFGA was never asked anything. Checked
                // before IOException, which this extends and would otherwise be read as a transport failure
                return false;
            }
            if (cause instanceof IOException || cause instanceof TimeoutException) {
                // a connect failure, a dropped connection, or no answer within the bound: the server never replied
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        // Nothing recognisable, so nothing that says OpenFGA was reached and could not answer: a request the SDK
        // refused to build (FgaInvalidParameterException for an invalid store id, say), an interrupt during shutdown,
        // or a plain bug. The list above is deliberately an allowlist - a failure this method does not recognise must
        // not become an allow just because it is unfamiliar.
        return false;
    }

    /**
     * Resolves the subject, denying the exchange when the expression produced something unusable.
     *
     * @return the resolved subject, or null when the exchange has been denied and the reason recorded on it
     */
    String resolveUser(Exchange exchange) {
        return resolveIdentifier(exchange, user, "user", "missing-user");
    }

    /**
     * Resolves the object, denying the exchange when the expression produced something unusable.
     *
     * @return the resolved object, or null when the exchange has been denied and the reason recorded on it
     */
    String resolveObject(Exchange exchange) {
        return resolveIdentifier(exchange, object, "object", "missing-object");
    }

    /**
     * Resolves the relation, denying the exchange when it is not configured or resolved to blank. A relation is a bare
     * name rather than a {@code type:id} identifier, so it is not put through the identifier guards.
     *
     * @return the resolved relation, or null when the exchange has been denied and the reason recorded on it
     */
    String resolveRelation(Exchange exchange) {
        String value = evaluate(exchange, relation);
        String reason = OpenFgaIdentifiers.validateRelation(value);
        if (reason != null) {
            return deny(exchange, reason, "relation");
        }
        exchange.getMessage().setHeader(OpenFgaConstants.RELATION, value);
        return value;
    }

    private String resolveIdentifier(Exchange exchange, Expression expression, String what, String missingReason) {
        String value = evaluate(exchange, expression);
        if (ObjectHelper.isEmpty(value) || OpenFgaIdentifiers.hasBlankId(value)) {
            // not a failure of the decision point, so failOpen must not reach it: an exchange that carried no
            // identity, or named no resource, has not been authorized by anything.
            // hasBlankId covers the usual shape of that - a configured "user:" prefix whose expression resolved to
            // nothing - which is malformed, but saying so would bury what went wrong
            return deny(exchange, missingReason, what);
        }
        String reason = OpenFgaIdentifiers.validate(value);
        if (reason != null) {
            return deny(exchange, reason, what);
        }
        exchange.getMessage().setHeader("user".equals(what) ? OpenFgaConstants.USER : OpenFgaConstants.OBJECT, value);
        return value;
    }

    private String evaluate(Exchange exchange, Expression expression) {
        if (expression == null) {
            return null;
        }
        String value = expression.evaluate(exchange, String.class);
        return value != null ? value.trim() : null;
    }

    /**
     * Records a deny that OpenFGA was never asked about, and returns null so the caller stops.
     */
    private String deny(Exchange exchange, String reason, String what) {
        LOG.debug("Denying exchange {}: the {} could not be used for an authorization check ({})",
                exchange.getExchangeId(), what, reason);
        setDecision(exchange, false);
        // first failure wins. The subject, the relation and the object are all resolved even once one of them has
        // failed, so that the observability headers show as much as could be worked out - but the reason reported
        // should be the most fundamental problem, not whichever part happened to be checked last. Safe to read back
        // because clearDecisionHeaders ran before any of this
        if (exchange.getMessage().getHeader(OpenFgaConstants.DENY_REASON) == null) {
            exchange.getMessage().setHeader(OpenFgaConstants.DENY_REASON, reason);
        }
        return null;
    }

    private void setDecision(Exchange exchange, boolean allowed) {
        Message message = exchange.getMessage();
        // set unconditionally, so a verdict claimed by an inbound message is always replaced
        message.setHeader(OpenFgaConstants.ALLOWED, allowed);
        if (ObjectHelper.isNotEmpty(storeId)) {
            message.setHeader(OpenFgaConstants.STORE_ID, storeId);
        }
    }

    static void clearDecisionHeaders(Exchange exchange) {
        Message message = exchange.getMessage();
        message.removeHeader(OpenFgaConstants.ALLOWED);
        // as worth spoofing as the verdict itself: left in place, a sender could preload it false and make a
        // fail-open allow read as a verdict OpenFGA actually gave
        message.removeHeader(OpenFgaConstants.FAILED_OPEN);
        message.removeHeader(OpenFgaConstants.DENY_REASON);
        message.removeHeader(OpenFgaConstants.USER);
        message.removeHeader(OpenFgaConstants.OBJECT);
        message.removeHeader(OpenFgaConstants.RELATION);
        message.removeHeader(OpenFgaConstants.STORE_ID);
    }

    ClientCheckOptions checkOptions() {
        ClientCheckOptions options = new ClientCheckOptions();
        if (consistency != null) {
            options.consistency(consistency);
        }
        if (ObjectHelper.isNotEmpty(authorizationModelId)) {
            options.authorizationModelId(authorizationModelId);
        }
        return options;
    }

    /**
     * Issues a call and waits for it, turning every way it can fail into an {@link OpenFgaEvaluationException}.
     * <p/>
     * The wait is bounded rather than open-ended. The SDK times out each request and retries a few times, so the bound
     * only has to be wider than that; what it buys is the guarantee that a future which never completes cannot hold a
     * routing thread for ever.
     */
    <T> T await(Exchange exchange, String what, CallSupplier<T> call) throws OpenFgaEvaluationException {
        CompletableFuture<T> future;
        try {
            future = call.get();
        } catch (Exception e) {
            throw new OpenFgaEvaluationException("Could not build the OpenFGA request to " + what, exchange, e);
        }
        try {
            return future.get(awaitTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // a shutdown, not a failure of the decision point. Restore the flag the interruptible wait cleared and
            // fail: nothing decided that this exchange was permitted, so failOpen must not claim it did either.
            // isDecisionPointUnavailable is what holds that line - an InterruptedException matches nothing on its
            // allowlist, so this fails closed however failOpen is set
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new OpenFgaEvaluationException("Interrupted while waiting for OpenFGA to " + what, exchange, e);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new OpenFgaEvaluationException(
                    "Timed out after " + awaitTimeoutMillis + "ms waiting for OpenFGA to " + what, exchange, e);
        } catch (ExecutionException e) {
            // the SDK reports an unreachable server, an HTTP error and a serialization problem all through the
            // future; every one of them is the same thing to a route - no verdict - so they are reported alike
            throw new OpenFgaEvaluationException(
                    "OpenFGA failed to " + what, exchange, e.getCause() != null ? e.getCause() : e);
        }
    }

    /**
     * Whether the endpoint configures a tuple at all, judged from the options as written rather than from what they
     * evaluate to.
     * <p/>
     * The distinction is the whole point. {@code user=${header.u}} on an exchange with no such header evaluates to
     * null, so deciding "is a tuple configured?" from the evaluated values would conclude that none is - and fall
     * through to taking the tuple from the message body, which is exactly the override that configuring one is meant to
     * prevent. A configured expression that resolves to nothing is an error, not an invitation.
     */
    boolean hasConfiguredTuple() {
        return user != null || relation != null || object != null;
    }

    /**
     * Evaluates the {@code user}, {@code relation} and {@code object} options without the check-path guards and without
     * touching the decision headers, for the operations that write relationship tuples. There the values are not a
     * subject being judged but a tuple the route has decided to write, so a typed wildcard is allowed and the
     * validation that applies is {@link OpenFgaIdentifiers#validateTupleValue}.
     */
    String rawUser(Exchange exchange) {
        return evaluate(exchange, user);
    }

    String rawRelation(Exchange exchange) {
        return evaluate(exchange, relation);
    }

    String rawObject(Exchange exchange) {
        return evaluate(exchange, object);
    }

    ConsistencyPreference getConsistency() {
        return consistency;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    OpenFgaClient getClient() {
        return client;
    }

    /**
     * A call to OpenFGA, separated out only because every SDK method declares a checked exception before it even
     * returns its future.
     */
    @FunctionalInterface
    interface CallSupplier<T> {
        CompletableFuture<T> get() throws Exception;
    }
}
