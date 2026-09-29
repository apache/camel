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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import dev.openfga.sdk.api.client.model.ClientBatchCheckClientResponse;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientListObjectsRequest;
import dev.openfga.sdk.api.client.model.ClientListRelationsRequest;
import dev.openfga.sdk.api.client.model.ClientListUsersRequest;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientTupleKeyWithoutCondition;
import dev.openfga.sdk.api.configuration.ClientBatchCheckClientOptions;
import dev.openfga.sdk.api.configuration.ClientListObjectsOptions;
import dev.openfga.sdk.api.configuration.ClientListRelationsOptions;
import dev.openfga.sdk.api.configuration.ClientListUsersOptions;
import dev.openfga.sdk.api.model.ConsistencyPreference;
import dev.openfga.sdk.api.model.FgaObject;
import dev.openfga.sdk.api.model.User;
import dev.openfga.sdk.api.model.UserTypeFilter;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenFgaProducer extends DefaultProducer {

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
        if (healthCheckRepository != null && producerHealthCheck != null) {
            healthCheckRepository.removeHealthCheck(producerHealthCheck);
            producerHealthCheck = null;
        }
        super.doStop();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        OpenFgaAuthorizer authorizer = getEndpoint().getAuthorizer();
        switch (getEndpoint().getOperation()) {
            case check -> authorizer.check(exchange);
            case batchCheck -> batchCheck(exchange, authorizer);
            case listObjects -> listObjects(exchange, authorizer);
            case listRelations -> listRelations(exchange, authorizer);
            case listUsers -> listUsers(exchange, authorizer);
            case writeTuples -> writeTuples(exchange, authorizer);
            case deleteTuples -> deleteTuples(exchange, authorizer);
            // unreachable today; here so that adding an operation to the enum without wiring it up fails loudly
            // instead of silently letting the exchange through an endpoint that was asked to authorize it
            default -> throw new IllegalArgumentException("Unsupported operation " + getEndpoint().getOperation());
        }
    }

    /**
     * Checks one subject against many objects, taken from the body, and replaces the body with the ones the check
     * allowed - so a route can filter a collection down to what the caller may see in one step.
     */
    private void batchCheck(Exchange exchange, OpenFgaAuthorizer authorizer) throws Exception {
        OpenFgaAuthorizer.clearDecisionHeaders(exchange);
        List<String> objects = bodyAsList(exchange);
        String user = authorizer.resolveUser(exchange);
        String relation = authorizer.resolveRelation(exchange);
        if (user == null || relation == null) {
            // the subject or the permission could not be established, so none of the objects is allowed. The deny
            // reason is already on the exchange
            exchange.getMessage().setBody(List.of());
            return;
        }

        List<ClientCheckRequest> requests = new ArrayList<>(objects.size());
        for (String object : objects) {
            if (OpenFgaIdentifiers.validate(object) != null) {
                // an entry that cannot be an object of a check is not one the caller may have; leaving it out of the
                // batch leaves it out of the result, which is the fail-closed answer
                LOG.debug("Skipping '{}' in a batchCheck: not usable as an object identifier", object);
                continue;
            }
            requests.add(new ClientCheckRequest().user(user).relation(relation)._object(object));
        }
        if (requests.isEmpty()) {
            // nothing in the body could be an object of a check, so nothing is allowed. Say so on the header as
            // every other path does, rather than leaving the route to infer a verdict from an empty body
            exchange.getMessage().setHeader(OpenFgaConstants.ALLOWED, false);
            exchange.getMessage().setBody(List.of());
            return;
        }

        ClientBatchCheckClientOptions options = new ClientBatchCheckClientOptions()
                .maxParallelRequests(getEndpoint().getConfiguration().getMaxParallelRequests());
        if (ObjectHelper.isNotEmpty(getEndpoint().getConfiguration().getAuthorizationModelId())) {
            options.authorizationModelId(getEndpoint().getConfiguration().getAuthorizationModelId());
        }
        if (authorizer.getConsistency() != null) {
            options.consistency(authorizer.getConsistency());
        }

        List<ClientBatchCheckClientResponse> responses = authorizer.await(
                exchange, "batch check " + relation + " for " + user,
                () -> authorizer.getClient().clientBatchCheck(requests, options));

        Set<String> permitted = new HashSet<>();
        for (ClientBatchCheckClientResponse response : responses) {
            if (response.getThrowable() != null) {
                // one check in the batch never got an answer. Returning the rest would quietly present a partial
                // filter as a complete one, so the whole operation fails - and unlike a single check, there is no
                // failOpen here, because "proceed" for a filter would mean handing back everything unfiltered
                throw new OpenFgaEvaluationException(
                        "OpenFGA failed to answer part of a batch check for " + user, exchange,
                        response.getThrowable());
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
    }

    /**
     * Lists the objects of the configured type the subject can reach through the relation.
     */
    private void listObjects(Exchange exchange, OpenFgaAuthorizer authorizer) throws Exception {
        OpenFgaAuthorizer.clearDecisionHeaders(exchange);
        String type = getEndpoint().getConfiguration().getType();
        String user = authorizer.resolveUser(exchange);
        String relation = authorizer.resolveRelation(exchange);
        if (user == null || relation == null) {
            exchange.getMessage().setBody(List.of());
            return;
        }

        ClientListObjectsRequest request = new ClientListObjectsRequest()
                .user(user)
                .relation(relation)
                .type(type);
        ClientListObjectsOptions options = new ClientListObjectsOptions();
        applyModelAndConsistency(authorizer, options::authorizationModelId, options::consistency);

        List<String> objects = authorizer.await(exchange, "list " + type + " objects " + user + " can " + relation,
                () -> authorizer.getClient().listObjects(request, options)).getObjects();
        exchange.getMessage().setBody(objects != null ? objects : List.of());
    }

    /**
     * Lists which of the configured relations the subject holds on the object.
     */
    private void listRelations(Exchange exchange, OpenFgaAuthorizer authorizer) throws Exception {
        OpenFgaAuthorizer.clearDecisionHeaders(exchange);
        String user = authorizer.resolveUser(exchange);
        String object = authorizer.resolveObject(exchange);
        if (user == null || object == null) {
            exchange.getMessage().setBody(List.of());
            return;
        }

        ClientListRelationsRequest request = new ClientListRelationsRequest()
                .user(user)
                ._object(object)
                .relations(splitToList(getEndpoint().getConfiguration().getRelations()));
        ClientListRelationsOptions options = new ClientListRelationsOptions();
        applyModelAndConsistency(authorizer, options::authorizationModelId, options::consistency);

        List<String> relations = authorizer.await(exchange, "list the relations " + user + " has on " + object,
                () -> authorizer.getClient().listRelations(request, options)).getRelations();
        exchange.getMessage().setBody(relations != null ? relations : List.of());
    }

    /**
     * Lists the subjects that hold the relation on the object.
     */
    private void listUsers(Exchange exchange, OpenFgaAuthorizer authorizer) throws Exception {
        OpenFgaAuthorizer.clearDecisionHeaders(exchange);
        String object = authorizer.resolveObject(exchange);
        String relation = authorizer.resolveRelation(exchange);
        if (object == null || relation == null) {
            exchange.getMessage().setBody(List.of());
            return;
        }

        ClientListUsersRequest request = new ClientListUsersRequest()
                ._object(toFgaObject(object))
                .relation(relation)
                .userFilters(userFilters());
        ClientListUsersOptions options = new ClientListUsersOptions();
        applyModelAndConsistency(authorizer, options::authorizationModelId, options::consistency);

        List<User> users = authorizer.await(exchange, "list the users with " + relation + " on " + object,
                () -> authorizer.getClient().listUsers(request, options)).getUsers();
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
    }

    /**
     * Writes relationship tuples, granting access.
     */
    private void writeTuples(Exchange exchange, OpenFgaAuthorizer authorizer) throws Exception {
        List<ClientTupleKey> tuples = new ArrayList<>();
        for (Tuple tuple : resolveTuples(exchange, authorizer)) {
            tuples.add(new ClientTupleKey().user(tuple.user).relation(tuple.relation)._object(tuple.object));
        }
        // the write options carry no authorizationModelId - unlike every query's options - so the model this writes
        // against is the one pinned on the client, which OpenFgaClientFactory already set from the configuration
        authorizer.await(exchange, "write " + tuples.size() + " tuple(s)",
                () -> authorizer.getClient().writeTuples(tuples));
        exchange.getMessage().setHeader(OpenFgaConstants.WRITTEN_TUPLES, tuples.size());
    }

    /**
     * Deletes relationship tuples, revoking access.
     */
    private void deleteTuples(Exchange exchange, OpenFgaAuthorizer authorizer) throws Exception {
        List<ClientTupleKeyWithoutCondition> tuples = new ArrayList<>();
        for (Tuple tuple : resolveTuples(exchange, authorizer)) {
            tuples.add(new ClientTupleKeyWithoutCondition()
                    .user(tuple.user).relation(tuple.relation)._object(tuple.object));
        }
        authorizer.await(exchange, "delete " + tuples.size() + " tuple(s)",
                () -> authorizer.getClient().deleteTuples(tuples));
        exchange.getMessage().setHeader(OpenFgaConstants.DELETED_TUPLES, tuples.size());
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
                    authorizer.rawUser(exchange), authorizer.rawRelation(exchange), authorizer.rawObject(exchange))));
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
            return validated(new Tuple(key.getUser(), key.getRelation(), key.getObject()));
        }
        if (element instanceof Map<?, ?> map) {
            return validated(new Tuple(
                    stringValue(map, "user"), stringValue(map, "relation"), stringValue(map, "object")));
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
    private Tuple validated(Tuple tuple) {
        reject(OpenFgaIdentifiers.validateTupleValue(tuple.user), "user", tuple.user);
        reject(OpenFgaIdentifiers.validateRelation(tuple.relation), "relation", tuple.relation);
        reject(OpenFgaIdentifiers.validateTupleValue(tuple.object), "object", tuple.object);
        return tuple;
    }

    private static void reject(String reason, String what, String value) {
        if (reason == null) {
            return;
        }
        if (value == null || value.isEmpty()) {
            // distinct from a malformed value, because the cause and the remedy are different: the endpoint asked for
            // this part and the exchange did not carry it
            throw new IllegalArgumentException(
                    "The " + what + " configured for this relationship tuple resolved to nothing; a configured tuple"
                                               + " is never completed from the message body");
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
}
