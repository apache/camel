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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import dev.openfga.sdk.api.client.model.ClientBatchCheckClientResponse;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientExpandRequest;
import dev.openfga.sdk.api.client.model.ClientListObjectsRequest;
import dev.openfga.sdk.api.client.model.ClientListRelationsRequest;
import dev.openfga.sdk.api.client.model.ClientListUsersRequest;
import dev.openfga.sdk.api.client.model.ClientReadChangesRequest;
import dev.openfga.sdk.api.client.model.ClientReadRequest;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientTupleKeyWithoutCondition;
import dev.openfga.sdk.api.configuration.ClientBatchCheckClientOptions;
import dev.openfga.sdk.api.configuration.ClientExpandOptions;
import dev.openfga.sdk.api.configuration.ClientListObjectsOptions;
import dev.openfga.sdk.api.configuration.ClientListRelationsOptions;
import dev.openfga.sdk.api.configuration.ClientListUsersOptions;
import dev.openfga.sdk.api.configuration.ClientReadChangesOptions;
import dev.openfga.sdk.api.configuration.ClientReadOptions;
import dev.openfga.sdk.api.model.ConsistencyPreference;
import dev.openfga.sdk.api.model.FgaObject;
import dev.openfga.sdk.api.model.TupleChange;
import dev.openfga.sdk.api.model.TupleKey;
import dev.openfga.sdk.api.model.User;
import dev.openfga.sdk.api.model.UserTypeFilter;
import dev.openfga.sdk.api.model.UsersetTree;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenFgaProducer extends DefaultAsyncProducer {

    private OpenFgaAsyncExecutor asyncExecutor;

    private static final Logger LOG = LoggerFactory.getLogger(OpenFgaProducer.class);

    private static final String DEFAULT_USER_FILTER = "user";

    private OpenFgaProducerHealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public OpenFgaProducer(final OpenFgaEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public OpenFgaEndpoint getEndpoint() {
        return (OpenFgaEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        OpenFgaConfiguration configuration = getEndpoint().getConfiguration();
        if (configuration.isAsyncProcessing()) {
            asyncExecutor = new OpenFgaAsyncExecutor(
                    getEndpoint().getCamelContext(), this,
                    configuration.getAsyncPoolSize(), configuration.getAsyncQueueSize(), configuration.getAsyncTimeout());
        }
        // an injected client can point anywhere and the endpoint has no way to ask it where, so there is no server
        // this check would know the address of
        if (configuration.getOpenFgaClient() != null || ObjectHelper.isEmpty(configuration.getApiUrl())) {
            return;
        }

        // health-check is optional so discover and resolve
        healthCheckRepository = HealthCheckHelper.getHealthCheckRepository(
                getEndpoint().getCamelContext(),
                "producers",
                WritableHealthCheckRepository.class);

        if (healthCheckRepository != null) {
            producerHealthCheck = new OpenFgaProducerHealthCheck(
                    configuration.getApiUrl(), configuration.getApiToken(), configuration.getStoreId(),
                    // the endpoint URI is unique within the context, so two endpoints sharing a store but pointing at
                    // different servers get distinct health-check ids instead of colliding
                    getEndpoint().getEndpointUri(), getEndpoint().getSslContext());
            producerHealthCheck.setEnabled(getEndpoint().getComponent().isHealthCheckProducerEnabled());
            healthCheckRepository.addHealthCheck(producerHealthCheck);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (asyncExecutor != null) {
            asyncExecutor.close();
            asyncExecutor = null;
        }
        if (healthCheckRepository != null && producerHealthCheck != null) {
            healthCheckRepository.removeHealthCheck(producerHealthCheck);
            producerHealthCheck.close();
            producerHealthCheck = null;
        }
        super.doStop();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (!getEndpoint().getConfiguration().isAsyncProcessing()) {
            processBlocking(exchange);
            return;
        }
        OpenFgaAsyncExecutor executor = asyncExecutor;
        try {
            super.process(exchange);
        } catch (InterruptedException e) {
            if (executor != null) {
                executor.cancel(exchange);
            }
            Thread.currentThread().interrupt();
            throw e;
        }
        if (exchange.getException() instanceof InterruptedException && executor != null) {
            executor.cancel(exchange, exchange.getException());
        }
        if (exchange.getException() != null) {
            throw exchange.getException();
        }
    }

    @Override
    public java.util.concurrent.CompletableFuture<Exchange> processAsync(Exchange exchange) {
        return OpenFgaAsyncExecutor.processFuture(exchange, this, asyncExecutor);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (!getEndpoint().getConfiguration().isAsyncProcessing()) {
            try {
                processBlocking(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }
            callback.done(true);
            return true;
        }
        OpenFgaAsyncExecutor executor = asyncExecutor;
        if (executor == null) {
            exchange.setException(new java.util.concurrent.CancellationException("Authorization producer is not started"));
            callback.done(true);
            return true;
        }
        switch (getEndpoint().getOperation()) {
            case check, batchCheck, listObjects, listRelations, listUsers, expand ->
                OpenFgaAuthorizer.clearDecisionHeaders(exchange);
            default -> {
            }
        }
        OpenFgaAuthorizer authorizer = getEndpoint().getAuthorizer();
        return executor.process(exchange, callback, snapshot -> {
            if (getEndpoint().getOperation() == OpenFgaOperation.check) {
                return authorizer.prepareCheckAsync(snapshot, executor);
            }
            PreparedOperation operation = prepareOperation(snapshot, authorizer, true);
            return () -> operation.runAsync(executor);
        });
    }

    private static void applyPreparedContext(
            Consumer<List<ClientTupleKey>> tuples, Consumer<Object> context,
            List<ClientTupleKey> resolved, OpenFgaAuthorizer authorizer, boolean async) {
        applyContext(tuples, value -> context.accept(async ? OpenFgaAsyncExecutor.snapshotValue(value) : value), resolved,
                authorizer);
    }

    private void processBlocking(Exchange exchange) throws Exception {
        OpenFgaAuthorizer authorizer = getEndpoint().getAuthorizer();
        if (getEndpoint().getOperation() == OpenFgaOperation.check) {
            authorizer.check(exchange);
        } else {
            prepareOperation(exchange, authorizer, false).runSync();
        }
    }

    /**
     * Checks one subject against many objects, taken from the body, and replaces the body with the ones the check
     * allowed - so a route can filter a collection down to what the caller may see in one step.
     */
    private PreparedOperation prepareBatchCheck(Exchange exchange, OpenFgaAuthorizer authorizer, boolean async)
            throws Exception {

        OpenFgaAuthorizer.clearDecisionHeaders(exchange);
        List<String> objects = bodyAsList(exchange);
        String user = authorizer.resolveUser(exchange);
        String relation = authorizer.resolveRelation(exchange);
        if (user == null || relation == null) {
            // the subject or the permission could not be established, so none of the objects is allowed. The deny
            // reason is already on the exchange
            exchange.getMessage().setBody(List.of());
            return NOOP;
        }

        List<ClientTupleKey> contextualTuples = authorizer.resolveContextualTuples(exchange);
        if (contextualTuples == null) {
            // a configured contextual tuple did not resolve, so no object is allowed; the reason is on the exchange
            exchange.getMessage().setHeader(OpenFgaConstants.ALLOWED, false);
            exchange.getMessage().setBody(List.of());
            return NOOP;
        }

        List<ClientCheckRequest> requests = new ArrayList<>(objects.size());
        for (String object : objects) {
            if (OpenFgaIdentifiers.validate(object) != null) {
                // an entry that cannot be an object of a check is not one the caller may have; leaving it out of the
                // batch leaves it out of the result, which is the fail-closed answer
                LOG.debug("Skipping '{}' in a batchCheck: not usable as an object identifier", object);
                continue;
            }
            ClientCheckRequest item = new ClientCheckRequest().user(user).relation(relation)._object(object);
            applyPreparedContext(item::contextualTuples, item::context, contextualTuples, authorizer, async);
            requests.add(item);
        }
        if (requests.isEmpty()) {
            // nothing in the body could be an object of a check, so nothing is allowed. Say so on the header as
            // every other path does, rather than leaving the route to infer a verdict from an empty body
            exchange.getMessage().setHeader(OpenFgaConstants.ALLOWED, false);
            exchange.getMessage().setBody(List.of());
            return NOOP;
        }

        int maxParallelRequests = getEndpoint().getConfiguration().getMaxParallelRequests();
        ClientBatchCheckClientOptions options = new ClientBatchCheckClientOptions()
                .maxParallelRequests(maxParallelRequests);
        if (ObjectHelper.isNotEmpty(getEndpoint().getConfiguration().getAuthorizationModelId())) {
            options.authorizationModelId(getEndpoint().getConfiguration().getAuthorizationModelId());
        }
        if (authorizer.getConsistency() != null) {
            options.consistency(authorizer.getConsistency());
        }

        return invocation(authorizer, exchange, "batch check " + relation + " for " + user,
                () -> authorizer.getClient().clientBatchCheck(requests, options), responses -> {

                    Set<String> permitted = new HashSet<>();
                    for (ClientBatchCheckClientResponse response : responses) {
                        if (response.getThrowable() != null) {
                            // one check in the batch never got an answer. Returning the rest would quietly present a partial
                            // filter as a complete one, so the whole operation fails - and unlike a single check, there is no
                            // failOpen here, because "proceed" for a filter would mean handing back everything unfiltered
                            throw new java.util.concurrent.CompletionException(
                                    new OpenFgaEvaluationException(
                                            "OpenFGA failed to answer part of a batch check for " + user, exchange,
                                            response.getThrowable()));
                        }
                        if (Boolean.TRUE.equals(response.getAllowed())) {
                            permitted.add(response.getRequest().getObject());
                        }
                    }
                    // filter the body rather than collect the answers: the SDK issues the checks in parallel and hands them back
                    // in whatever order they completed, so building the result from the responses would reorder the caller's list
                    // between runs. A route that filters a list to render it wants the order it asked in
                    List<String> allowed = new ArrayList<>();
                    for (String object : objects) {
                        if (permitted.contains(object)) {
                            allowed.add(object);
                        }
                    }
                    exchange.getMessage().setHeader(OpenFgaConstants.ALLOWED, !allowed.isEmpty());
                    exchange.getMessage().setBody(allowed);
                }, authorizer.batchAwaitTimeoutMillis(requests.size(), maxParallelRequests));
    }

    /**
     * Lists the objects of the configured type the subject can reach through the relation.
     */
    private PreparedOperation prepareListObjects(Exchange exchange, OpenFgaAuthorizer authorizer, boolean async)
            throws Exception {

        OpenFgaAuthorizer.clearDecisionHeaders(exchange);
        String type = getEndpoint().getConfiguration().getType();
        String user = authorizer.resolveUser(exchange);
        String relation = authorizer.resolveRelation(exchange);
        if (user == null || relation == null) {
            exchange.getMessage().setBody(List.of());
            return NOOP;
        }

        ClientListObjectsRequest request = new ClientListObjectsRequest()
                .user(user)
                .relation(relation)
                .type(type);
        List<ClientTupleKey> contextualTuples = authorizer.resolveContextualTuples(exchange);
        if (contextualTuples == null) {
            exchange.getMessage().setBody(List.of());
            return NOOP;
        }
        applyPreparedContext(request::contextualTupleKeys, request::context, contextualTuples, authorizer, async);
        ClientListObjectsOptions options = new ClientListObjectsOptions();
        applyModelAndConsistency(authorizer, options::authorizationModelId, options::consistency);

        return invocation(authorizer, exchange, "list " + type + " objects " + user + " can " + relation,
                () -> authorizer.getClient().listObjects(request, options), result -> {
                    List<String> objects = result.getObjects();
                    exchange.getMessage().setBody(objects != null ? objects : List.of());
                }, null);
    }

    /**
     * Lists which of the configured relations the subject holds on the object.
     */
    private PreparedOperation prepareListRelations(Exchange exchange, OpenFgaAuthorizer authorizer, boolean async)
            throws Exception {

        OpenFgaAuthorizer.clearDecisionHeaders(exchange);
        String user = authorizer.resolveUser(exchange);
        String object = authorizer.resolveObject(exchange);
        if (user == null || object == null) {
            exchange.getMessage().setBody(List.of());
            return NOOP;
        }

        List<String> configuredRelations = splitToList(getEndpoint().getConfiguration().getRelations());
        ClientListRelationsRequest request = new ClientListRelationsRequest()
                .user(user)
                ._object(object)
                .relations(configuredRelations);
        List<ClientTupleKey> contextualTuples = authorizer.resolveContextualTuples(exchange);
        if (contextualTuples == null) {
            exchange.getMessage().setBody(List.of());
            return NOOP;
        }
        applyPreparedContext(request::contextualTupleKeys, request::context, contextualTuples, authorizer, async);
        ClientListRelationsOptions options = new ClientListRelationsOptions();
        applyModelAndConsistency(authorizer, options::authorizationModelId, options::consistency);

        return invocation(authorizer, exchange, "list the relations " + user + " has on " + object,
                () -> authorizer.getClient().listRelations(request, options), result -> {
                    List<String> held = result.getRelations();
                    // the SDK checks the relations in parallel and returns the ones that hold in completion order, which varies
                    // from call to call; return them in the order the relations option lists them so the result is deterministic
                    // (CAMEL-26197)
                    exchange.getMessage().setBody(inConfiguredOrder(configuredRelations, held));
                }, null);
    }

    /**
     * Lists the subjects that hold the relation on the object.
     */
    private PreparedOperation prepareListUsers(Exchange exchange, OpenFgaAuthorizer authorizer, boolean async)
            throws Exception {

        OpenFgaAuthorizer.clearDecisionHeaders(exchange);
        String object = authorizer.resolveObject(exchange);
        String relation = authorizer.resolveRelation(exchange);
        if (object == null || relation == null) {
            exchange.getMessage().setBody(List.of());
            return NOOP;
        }

        ClientListUsersRequest request = new ClientListUsersRequest()
                ._object(toFgaObject(object))
                .relation(relation)
                .userFilters(userFilters());
        List<ClientTupleKey> contextualTuples = authorizer.resolveContextualTuples(exchange);
        if (contextualTuples == null) {
            exchange.getMessage().setBody(List.of());
            return NOOP;
        }
        applyPreparedContext(request::contextualTupleKeys, request::context, contextualTuples, authorizer, async);
        ClientListUsersOptions options = new ClientListUsersOptions();
        applyModelAndConsistency(authorizer, options::authorizationModelId, options::consistency);

        return invocation(authorizer, exchange, "list the users with " + relation + " on " + object,
                () -> authorizer.getClient().listUsers(request, options), result -> {
                    List<User> users = result.getUsers();
                    List<String> identifiers = new ArrayList<>();
                    if (users != null) {
                        for (User user : users) {
                            String identifier = toIdentifier(user);
                            if (identifier != null) {
                                identifiers.add(identifier);
                            }
                        }
                    }
                    exchange.getMessage().setBody(identifiers);
                }, null);
    }

    /**
     * Reads the stored relationship tuples, filtered by whichever of user, relation and object the endpoint configured.
     * <p/>
     * Unlike every other operation these three are optional here: a part the endpoint never set means "do not filter on
     * this", and leaving all three unset reads the whole store a page at a time. That is why the raw resolvers are used
     * rather than the check-path ones, which treat a blank value as a missing identity and deny. A part that WAS set
     * and resolves to nothing is a different thing entirely - see {@link #readFilterPart}.
     */
    private PreparedOperation prepareReadTuples(Exchange exchange, OpenFgaAuthorizer authorizer, boolean async)
            throws Exception {

        String filterUser = readFilterPart(authorizer.hasConfiguredUser(), authorizer.rawUser(exchange), "user");
        String filterRelation
                = readFilterPart(authorizer.hasConfiguredRelation(), authorizer.rawRelation(exchange), "relation");
        String filterObject = readFilterPart(authorizer.hasConfiguredObject(), authorizer.rawObject(exchange), "object");
        validateReadFilter(filterUser, filterRelation, filterObject);

        ClientReadRequest request = new ClientReadRequest();
        if (filterUser != null) {
            request.user(filterUser);
        }
        if (filterRelation != null) {
            request.relation(filterRelation);
        }
        if (filterObject != null) {
            request._object(filterObject);
        }

        ClientReadOptions options = new ClientReadOptions();
        if (getEndpoint().getConfiguration().getPageSize() != null) {
            options.pageSize(getEndpoint().getConfiguration().getPageSize());
        }
        String token = authorizer.rawContinuationToken(exchange);
        if (ObjectHelper.isNotEmpty(token)) {
            options.continuationToken(token);
        }
        if (authorizer.getConsistency() != null) {
            options.consistency(authorizer.getConsistency());
        }

        return invocation(authorizer, exchange, "read the stored tuples",
                () -> authorizer.getClient().read(request, options), response -> {

                    List<Map<String, Object>> tuples = new ArrayList<>();
                    if (response.getTuples() != null) {
                        // qualified because this class already has its own Tuple, the holder the write paths use; that one is
                        // referenced far more often, so it keeps the simple name
                        for (dev.openfga.sdk.api.model.Tuple tuple : response.getTuples()) {
                            TupleKey key = tuple.getKey();
                            if (key == null) {
                                continue;
                            }
                            // the user/relation/object keys are deliberately the ones writeTuples and deleteTuples accept, so a
                            // route can feed what it read straight back into a revoke without reshaping it
                            Map<String, Object> entry = new LinkedHashMap<>();
                            entry.put("user", key.getUser());
                            entry.put("relation", key.getRelation());
                            entry.put("object", key.getObject());
                            entry.put("timestamp", tuple.getTimestamp());
                            tuples.add(entry);
                        }
                    }
                    setContinuationToken(exchange, response.getContinuationToken());
                    exchange.getMessage().setBody(tuples);
                }, null);
    }

    /**
     * Reads the store's change log, so a route can keep something outside OpenFGA in step with the graph.
     */
    private PreparedOperation prepareReadChanges(Exchange exchange, OpenFgaAuthorizer authorizer, boolean async)
            throws Exception {

        ClientReadChangesRequest request = new ClientReadChangesRequest();
        if (ObjectHelper.isNotEmpty(getEndpoint().getConfiguration().getType())) {
            request.type(getEndpoint().getConfiguration().getType());
        }
        if (getEndpoint().getStartTime() != null) {
            request.startTime(getEndpoint().getStartTime());
        }

        ClientReadChangesOptions options = new ClientReadChangesOptions();
        if (getEndpoint().getConfiguration().getPageSize() != null) {
            options.pageSize(getEndpoint().getConfiguration().getPageSize());
        }
        String token = authorizer.rawContinuationToken(exchange);
        if (ObjectHelper.isNotEmpty(token)) {
            options.continuationToken(token);
        }

        return invocation(authorizer, exchange, "read the change log",
                () -> authorizer.getClient().readChanges(request, options), response -> {

                    List<Map<String, Object>> changes = new ArrayList<>();
                    if (response.getChanges() != null) {
                        for (TupleChange change : response.getChanges()) {
                            TupleKey key = change.getTupleKey();
                            if (key == null) {
                                continue;
                            }
                            Map<String, Object> entry = new LinkedHashMap<>();
                            entry.put("user", key.getUser());
                            entry.put("relation", key.getRelation());
                            entry.put("object", key.getObject());
                            // the enum's name rather than the enum, so a route can compare against "WRITE" without importing
                            // an SDK type, and so the body marshals without a custom serializer
                            entry.put("operation", change.getOperation() != null ? change.getOperation().name() : null);
                            entry.put("timestamp", change.getTimestamp());
                            changes.add(entry);
                        }
                    }
                    setContinuationToken(exchange, response.getContinuationToken());
                    exchange.getMessage().setBody(changes);
                }, null);
    }

    /**
     * Expands a relation on an object into its userset tree.
     * <p/>
     * This is the one operation that does not leave a {@code List<String>} on the body, because its answer is not a
     * list: it is a tree of usersets explaining how a relation resolves. The {@link UsersetTree} goes on the body as it
     * comes back from the SDK rather than being flattened, since flattening is exactly what destroys the information
     * the operation exists to provide.
     */
    private PreparedOperation prepareExpand(Exchange exchange, OpenFgaAuthorizer authorizer, boolean async) throws Exception {

        OpenFgaAuthorizer.clearDecisionHeaders(exchange);
        String object = authorizer.resolveObject(exchange);
        String relation = authorizer.resolveRelation(exchange);
        if (object == null || relation == null) {
            exchange.getMessage().setBody(null);
            return NOOP;
        }

        ClientExpandRequest request = new ClientExpandRequest()
                ._object(object)
                .relation(relation);
        ClientExpandOptions options = new ClientExpandOptions();
        applyModelAndConsistency(authorizer, options::authorizationModelId, options::consistency);

        return invocation(authorizer, exchange, "expand " + relation + " on " + object,
                () -> authorizer.getClient().expand(request, options), response -> {
                    exchange.getMessage().setBody(response.getTree());
                }, null);
    }

    /**
     * Writes relationship tuples, granting access.
     */
    private PreparedOperation prepareWriteTuples(Exchange exchange, OpenFgaAuthorizer authorizer, boolean async)
            throws Exception {

        List<ClientTupleKey> tuples = new ArrayList<>();
        for (Tuple tuple : resolveTuples(exchange, authorizer)) {
            tuples.add(new ClientTupleKey().user(tuple.user).relation(tuple.relation)._object(tuple.object));
        }
        // the write options carry no authorizationModelId - unlike every query's options - so the model this writes
        // against is the one pinned on the client, which OpenFgaClientFactory already set from the configuration
        return invocation(authorizer, exchange, "write " + tuples.size() + " tuple(s)",
                () -> authorizer.getClient().writeTuples(tuples), ignored -> {
                    exchange.getMessage().setHeader(OpenFgaConstants.WRITTEN_TUPLES, tuples.size());
                }, null);
    }

    /**
     * Deletes relationship tuples, revoking access.
     */
    private PreparedOperation prepareDeleteTuples(Exchange exchange, OpenFgaAuthorizer authorizer, boolean async)
            throws Exception {

        List<ClientTupleKeyWithoutCondition> tuples = new ArrayList<>();
        for (Tuple tuple : resolveTuples(exchange, authorizer)) {
            tuples.add(new ClientTupleKeyWithoutCondition()
                    .user(tuple.user).relation(tuple.relation)._object(tuple.object));
        }
        return invocation(authorizer, exchange, "delete " + tuples.size() + " tuple(s)",
                () -> authorizer.getClient().deleteTuples(tuples), ignored -> {
                    exchange.getMessage().setHeader(OpenFgaConstants.DELETED_TUPLES, tuples.size());
                }, null);
    }

    /**
     * Returns the relations the subject holds in the order the {@code relations} option lists them, rather than the
     * order the SDK happened to return - which, because it checks the relations in parallel, changes from call to call.
     */
    private static List<String> inConfiguredOrder(List<String> configured, List<String> held) {
        if (held == null || held.isEmpty()) {
            return List.of();
        }
        Set<String> heldSet = new HashSet<>(held);
        List<String> ordered = new ArrayList<>(held.size());
        for (String relation : configured) {
            if (heldSet.contains(relation)) {
                ordered.add(relation);
            }
        }
        return ordered;
    }

    /**
     * Resolves one part of a read filter, keeping "never configured" and "configured but resolved to nothing" apart.
     * <p/>
     * Only an option the endpoint never set means "do not filter on this". An option that IS set and evaluates to blank
     * - {@code user=${header.who}} on an exchange carrying no such header - is a failure to resolve, and dropping it
     * would make the read WIDER than the route asked for: a filter meant to select one subject's tuples would instead
     * return every tuple on the object, and the documented {@code readTuples -> deleteTuples} route would then revoke
     * all of them. Widening a filter on a value that went missing is the read-side form of failing open, so it is
     * refused. Same reasoning as {@link OpenFgaAuthorizer#hasConfiguredTuple()} on the write path.
     */
    private static String readFilterPart(boolean configured, String value, String option) {
        if (!configured) {
            return null;
        }
        String trimmed = trimmedOrNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(
                    option + " is configured for the readTuples operation but resolved to nothing on this exchange:"
                                               + " dropping it would read more than was asked for, so it is refused rather than widened");
        }
        return trimmed;
    }

    private static String trimmedOrNull(String value) {
        if (ObjectHelper.isEmpty(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Checks a read filter against what OpenFGA's Read API actually accepts, which is narrower than "every part is
     * optional".
     * <p/>
     * Measured against OpenFGA 1.21.0: no filter at all reads the whole store, {@code object=document:} with a user or
     * a full {@code object=document:budget} are accepted, and a filter of user alone, relation alone, or object type
     * alone is rejected with an HTTP 400 reading <em>"the 'tuple_key' field was provided but the object type field is
     * required and both the object id and user cannot be empty"</em>. Catching it here turns that into something that
     * names the option to change.
     * <p/>
     * Note that the object is deliberately not put through {@link OpenFgaIdentifiers#validateTupleValue}: a type-only
     * {@code document:} is a legitimate read filter and not a legitimate identifier, so the two have different rules.
     */
    private static void validateReadFilter(String user, String relation, String object) {
        if (user == null && relation == null && object == null) {
            return;
        }
        // only the parts that were actually given: here an unset part means "do not filter on this", which is the
        // opposite of what it means on the write path, where reject() must still fire for a configured part that
        // resolved to nothing
        if (user != null) {
            reject(OpenFgaIdentifiers.validateTupleValue(user), "user", user, false);
        }
        if (relation != null) {
            reject(OpenFgaIdentifiers.validateRelation(relation), "relation", relation, false);
        }
        if (object == null) {
            throw new IllegalArgumentException(
                    "A readTuples filter must include object: OpenFGA requires an object type as soon as any filter is"
                                               + " given. Use object=<type>: to filter on the type alone, or leave"
                                               + " user, relation and object all unset to read the whole store");
        }
        int separator = object.indexOf(':');
        if (separator <= 0) {
            throw new IllegalArgumentException(
                    "The object filter '" + object + "' must name a type, as in document: or document:budget");
        }
        if (separator == object.length() - 1 && user == null) {
            throw new IllegalArgumentException(
                    "A readTuples filter of object type alone is not enough for OpenFGA: give an object id"
                                               + " (object=" + object + "<id>) or a user (user=<type>:<id>) alongside"
                                               + " object=" + object);
        }
    }

    /**
     * Records the page token, and removes a token a previous exchange left behind when the server returned none -
     * otherwise a route looping on the header would read the same final page for ever.
     * <p/>
     * Which of the two happens is the server's choice, not this component's, and the two read operations differ
     * (measured against OpenFGA 1.21.0): the Read API returns an empty token on its last page, so the header really
     * does disappear once readTuples is done; readChanges echoes the request's token back with an empty page instead,
     * because a change log is a tailing cursor rather than a finite list - that token is the bookmark the next poll
     * resumes from, so it is deliberately left in place and an empty body is what signals "up to date". Removing it
     * there would break incremental sync rather than improve it.
     */
    private static void setContinuationToken(Exchange exchange, String token) {
        if (ObjectHelper.isNotEmpty(token)) {
            exchange.getMessage().setHeader(OpenFgaConstants.CONTINUATION_TOKEN, token);
        } else {
            exchange.getMessage().removeHeader(OpenFgaConstants.CONTINUATION_TOKEN);
        }
    }

    /**
     * Collects the tuples to write or delete, from the body when it carries any and from the endpoint's
     * {@code user}/{@code relation}/{@code object} otherwise - so a route that just created a resource can grant access
     * to it without assembling a payload.
     * <p/>
     * A typed wildcard is accepted here, unlike on the check path: {@code user:*} is exactly how a resource is shared
     * with everyone, and writing that tuple is a deliberate act by the route rather than something a caller chose.
     */
    private List<Tuple> resolveTuples(Exchange exchange, OpenFgaAuthorizer authorizer) throws InvalidPayloadException {
        // The endpoint wins whenever it names a tuple at all. This is an authorization component, so the same rule
        // that governs the check governs the write: the configuration decides and the message does not. Reading the
        // body in preference would mean a route that unmarshals an untrusted payload hands the caller the choice of
        // which relationship to grant - and "user:attacker owner document:secret" is a legitimate-looking tuple.
        // asked of the configuration, not of what it evaluated to: user=${header.u} with no such header evaluates to
        // null, and judging by the value would read that as "nothing configured" and go on to take the tuple from the
        // body - the very override configuring a tuple is meant to prevent
        if (authorizer.hasConfiguredTuple()) {
            // a part that is configured but resolved to nothing, like a partly configured triple, is a mistake rather
            // than an invitation to fill the rest in from the message, so validated() reports which part it was
            return List.of(validated(new Tuple(
                    authorizer.rawUser(exchange), authorizer.rawRelation(exchange), authorizer.rawObject(exchange)),
                    true));
        }

        List<Tuple> tuples = new ArrayList<>();
        Object body = exchange.getMessage().getBody();
        if (body instanceof Collection<?> collection) {
            for (Object element : collection) {
                tuples.add(toTuple(element));
            }
        } else if (body instanceof Map || body instanceof ClientTupleKeyWithoutCondition) {
            tuples.add(toTuple(body));
        } else {
            // neither a payload nor a configured triple: there is nothing to write, and guessing is not an option
            throw new InvalidPayloadException(exchange, Collection.class);
        }
        if (tuples.isEmpty()) {
            // an empty collection asks for no tuples at all, which OpenFGA rejects; saying so here names the cause
            throw new InvalidPayloadException(exchange, Collection.class);
        }
        return tuples;
    }

    private Tuple toTuple(Object element) {
        if (element instanceof ClientTupleKeyWithoutCondition key) {
            return validated(new Tuple(key.getUser(), key.getRelation(), key.getObject()), false);
        }
        if (element instanceof Map<?, ?> map) {
            return validated(new Tuple(
                    stringValue(map, "user"), stringValue(map, "relation"), stringValue(map, "object")), false);
        }
        throw new IllegalArgumentException(
                "A relationship tuple must be a Map with user, relation and object entries, or a ClientTupleKey, but was "
                                           + (element == null ? "null" : element.getClass().getName()));
    }

    /**
     * Rejects a tuple the component would otherwise send with a blank or malformed part. OpenFGA would reject it too,
     * but naming the offending part here is the difference between a fixable message and a validation error from the
     * server that does not say which of the tuples in the batch was at fault.
     */
    private Tuple validated(Tuple tuple, boolean fromConfiguration) {
        reject(OpenFgaIdentifiers.validateTupleValue(tuple.user), "user", tuple.user, fromConfiguration);
        reject(OpenFgaIdentifiers.validateRelation(tuple.relation), "relation", tuple.relation, fromConfiguration);
        reject(OpenFgaIdentifiers.validateTupleValue(tuple.object), "object", tuple.object, fromConfiguration);
        return tuple;
    }

    private static void reject(String reason, String what, String value, boolean fromConfiguration) {
        if (reason == null) {
            return;
        }
        if (value == null || value.isEmpty()) {
            // distinct from a malformed value, because the cause and the remedy differ - and distinct between the two
            // sources, because pointing at the configuration when the tuple came from the body sends the reader to
            // the wrong place entirely
            throw new IllegalArgumentException(
                    fromConfiguration
                            ? "The " + what + " configured for this relationship tuple resolved to nothing;"
                              + " a configured tuple is never completed from the message body"
                            : "A relationship tuple in the message body has no " + what);
        }
        throw new IllegalArgumentException(
                "A relationship tuple has an unusable " + what + " (" + reason + "): '" + value + "'");
    }

    private static String stringValue(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString().trim() : null;
    }

    /**
     * Reads the objects a {@code batchCheck} should cover out of the body, accepting a collection or a single
     * comma-separated value.
     */
    private List<String> bodyAsList(Exchange exchange) throws InvalidPayloadException {
        Object body = exchange.getMessage().getBody();
        if (body instanceof Collection<?> collection) {
            List<String> objects = new ArrayList<>(collection.size());
            for (Object element : collection) {
                if (element != null) {
                    objects.add(element.toString().trim());
                }
            }
            return objects;
        }
        if (body instanceof Map) {
            // a Map is the shape of a single relationship tuple (what writeTuples/deleteTuples accept), not a list of
            // objects; converting it to a String would split its toString and send the pieces as objects, which
            // OpenFGA rejects with an HTTP 400 that fails the whole exchange (CAMEL-26197)
            throw new IllegalArgumentException(
                    "batchCheck expects the objects to check as a List or a comma-separated String, but the body was a"
                                               + " Map; pass the object identifiers as a List<String>");
        }
        String text = exchange.getMessage().getBody(String.class);
        if (ObjectHelper.isEmpty(text)) {
            throw new InvalidPayloadException(exchange, Collection.class);
        }
        return splitToList(text);
    }

    private List<UserTypeFilter> userFilters() {
        String configured = getEndpoint().getConfiguration().getUserFilters();
        List<String> entries = splitToList(ObjectHelper.isNotEmpty(configured) ? configured : DEFAULT_USER_FILTER);
        List<UserTypeFilter> filters = new ArrayList<>(entries.size());
        for (String entry : entries) {
            UserTypeFilter filter = new UserTypeFilter();
            int hash = entry.indexOf('#');
            if (hash > 0) {
                filter.type(entry.substring(0, hash)).relation(entry.substring(hash + 1));
            } else {
                filter.type(entry);
            }
            filters.add(filter);
        }
        return filters;
    }

    /**
     * Renders one entry of a {@code listUsers} answer as an identifier, so the body is a list of strings a route can
     * use rather than a list of SDK objects. OpenFGA answers with a concrete subject, a userset or a typed wildcard,
     * and each has its own textual form.
     */
    private static String toIdentifier(User user) {
        if (user.getObject() != null) {
            return user.getObject().getType() + ":" + user.getObject().getId();
        }
        if (user.getUserset() != null) {
            return user.getUserset().getType() + ":" + user.getUserset().getId() + "#" + user.getUserset().getRelation();
        }
        if (user.getWildcard() != null) {
            return user.getWildcard().getType() + ":*";
        }
        return null;
    }

    private static FgaObject toFgaObject(String object) {
        int separator = object.indexOf(':');
        return new FgaObject().type(object.substring(0, separator)).id(object.substring(separator + 1));
    }

    private static List<String> splitToList(String value) {
        List<String> values = new ArrayList<>();
        if (ObjectHelper.isEmpty(value)) {
            return values;
        }
        for (String element : value.split(",")) {
            String trimmed = element.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    /**
     * Applies the resolved contextual tuples and the condition context to a request.
     * <p/>
     * Each request type names these differently - {@code contextualTuples} on a check, {@code contextualTupleKeys} on
     * the list operations - and they share no supertype, so the setters are passed in rather than the request.
     */
    private static void applyContext(
            Consumer<List<ClientTupleKey>> contextualTuples, Consumer<Object> context,
            List<ClientTupleKey> resolved, OpenFgaAuthorizer authorizer) {
        if (!resolved.isEmpty()) {
            contextualTuples.accept(resolved);
        }
        Map<String, Object> conditionContext = authorizer.getConditionContext();
        if (conditionContext != null && !conditionContext.isEmpty()) {
            context.accept(conditionContext);
        }
    }

    /**
     * Applies the pinned authorization model and the configured consistency to whichever options object the operation
     * uses; the SDK gives each operation its own type with no common supertype to set them through.
     */
    private void applyModelAndConsistency(
            OpenFgaAuthorizer authorizer, Consumer<String> model, Consumer<ConsistencyPreference> consistency) {
        if (ObjectHelper.isNotEmpty(getEndpoint().getConfiguration().getAuthorizationModelId())) {
            model.accept(getEndpoint().getConfiguration().getAuthorizationModelId());
        }
        if (authorizer.getConsistency() != null) {
            consistency.accept(authorizer.getConsistency());
        }
    }

    /**
     * A relationship tuple on its way to OpenFGA, before it is turned into whichever SDK type the operation needs.
     */
    private static final class Tuple {
        private final String user;
        private final String relation;
        private final String object;

        private Tuple(String user, String relation, String object) {
            this.user = user;
            this.relation = relation;
            this.object = object;
        }
    }

    private PreparedOperation prepareOperation(Exchange exchange, OpenFgaAuthorizer authorizer, boolean async)
            throws Exception {
        return switch (getEndpoint().getOperation()) {
            case batchCheck -> prepareBatchCheck(exchange, authorizer, async);
            case listObjects -> prepareListObjects(exchange, authorizer, async);
            case listRelations -> prepareListRelations(exchange, authorizer, async);
            case listUsers -> prepareListUsers(exchange, authorizer, async);
            case readTuples -> prepareReadTuples(exchange, authorizer, async);
            case readChanges -> prepareReadChanges(exchange, authorizer, async);
            case expand -> prepareExpand(exchange, authorizer, async);
            case writeTuples -> prepareWriteTuples(exchange, authorizer, async);
            case deleteTuples -> prepareDeleteTuples(exchange, authorizer, async);
            default -> throw new IllegalArgumentException("Unsupported operation " + getEndpoint().getOperation());
        };
    }

    private interface PreparedOperation {
        void runSync() throws Exception;

        CompletableFuture<?> runAsync(OpenFgaAsyncExecutor executor);
    }

    private static final PreparedOperation NOOP = new PreparedOperation() {
        @Override
        public void runSync() {
        }

        @Override
        public CompletableFuture<?> runAsync(OpenFgaAsyncExecutor executor) {
            return CompletableFuture.completedFuture(null);
        }
    };

    private static <T> PreparedOperation invocation(
            OpenFgaAuthorizer authorizer, Exchange exchange, String what,
            OpenFgaAuthorizer.CallSupplier<T> call, Consumer<T> result, Long budget) {
        return new PreparedOperation() {
            @Override
            public void runSync() throws Exception {
                T response = budget == null
                        ? authorizer.await(exchange, what, call) : authorizer.await(exchange, what, call, budget);
                try {
                    result.accept(response);
                } catch (java.util.concurrent.CompletionException e) {
                    if (e.getCause() instanceof Exception cause) {
                        throw cause;
                    }
                    throw e;
                }
            }

            @Override
            public CompletableFuture<?> runAsync(OpenFgaAsyncExecutor executor) {
                CompletableFuture<T> future = budget == null
                        ? authorizer.awaitAsync(executor, exchange, what, call)
                        : authorizer.awaitAsync(executor, exchange, what, call, budget);
                return future.thenAccept(result);
            }
        };
    }
}
