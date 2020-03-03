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
package org.apache.camel.component.salesforce.api.dto.composite;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import org.apache.camel.component.salesforce.api.dto.AbstractDescribedSObjectBase;
import org.apache.camel.component.salesforce.api.dto.AbstractSObjectBase;
import org.apache.camel.component.salesforce.api.utils.Version;

import static org.apache.camel.util.ObjectHelper.notNull;
import static org.apache.camel.util.StringHelper.notEmpty;

/**
 * Builder for Composite API batch request. Composite API is available from
 * Salesforce API version 34.0 onwards its a way to combine multiple requests in
 * a batch and submit them in one HTTP request. This object help to build the
 * payload of the batch request. Most requests that are supported in the
 * Composite batch API the helper builder methods are provided. For batch
 * requests that do not have their corresponding helper builder method, use
 * {@link #addGeneric(Method, String)} or
 * {@link #addGeneric(Method, String, Object)} methods. To build the batch use:
 * <blockquote>
 *
 * <pre>
 * {
 *     &#64;code
 *     SObjectBatch batch = new SObjectBatch("37.0");
 *
 *     final Account account = new Account();
 *     account.setName("NewAccountName");
 *     account.setIndustry(Account_IndustryEnum.ENVIRONMENTAL);
 *     batch.addCreate(account);
 *
 *     batch.addDelete("Account", "001D000000K0fXOIAZ");
 *
 *     batch.addGet("Account", "0010Y00000Arwt6QAB", "Name", "BillingPostalCode");
 * }
 *
 * </pre>
 *
 * </blockquote> This will build a batch of three operations, one to create new
 * Account, one to delete an Account, and one to get two fields from an Account.
 */
@XStreamAlias("batch")
public final class SObjectBatch implements Serializable {

    private static final String SOBJECT_TYPE_PARAM = "type";

    public enum Method {
        DELETE, GET, PATCH, POST
    }

    private static final int MAX_BATCH = 25;

    private static final long serialVersionUID = 1L;

    @XStreamOmitField
    private final String apiPrefix;

    private final List<BatchRequest> batchRequests = new ArrayList<>();

    @XStreamOmitField
    private final Version version;

    /**
     * Create new batch request. You must specify the API version of the batch
     * request. The API version cannot be newer than the version configured in
     * the Salesforce Camel component. Some of the batched requests are
     * available only from certain Salesforce API versions, when this is the
     * case it is noted in the documentation of the builder method, if uncertain
     * consult the Salesforce API documentation.
     *
     * @param apiVersion API version for the batch request
     */
    public SObjectBatch(final String apiVersion) {
        final String givenApiVersion = Objects.requireNonNull(apiVersion, "apiVersion");

        version = Version.create(apiVersion);

        version.requireAtLeast(34, 0);

        this.apiPrefix = "v" + givenApiVersion;
    }

    static String composeFieldsParameter(final String... fields) {
        if (fields != null && fields.length > 0) {
            return "?fields=" + Arrays.stream(fields).collect(Collectors.joining(","));
        } else {
            return "";
        }
    }

    /**
     * Add create SObject to the batch request.
     *
     * @param data object to create
     * @return this batch builder
     */
    public SObjectBatch addCreate(final AbstractDescribedSObjectBase data) {
        addBatchRequest(new BatchRequest(Method.POST, apiPrefix + "/sobjects/" + typeOf(data) + "/", data));

        return this;
    }

    /**
     * Add delete SObject with identifier to the batch request.
     *
     * @param type type of SObject
     * @param id identifier of the object
     * @return this batch builder
     */
    public SObjectBatch addDelete(final String type, final String id) {
        addBatchRequest(new BatchRequest(Method.DELETE, rowBaseUrl(type, id)));

        return this;
    }

    /**
     * Generic way to add requests to batch. Given URL starts from the version,
     * so in order to retrieve SObject specify just
     * {@code /sobjects/Account/identifier} which results in
     * {@code /services/data/v37.0/sobjects/Account/identifier}. Note the
     * leading slash.
     *
     * @param method HTTP method
     * @param url URL starting from the version
     * @return this batch builder
     */
    public SObjectBatch addGeneric(final Method method, final String url) {
        addGeneric(method, url, null);

        return this;
    }

    /**
     * Generic way to add requests to batch with {@code richInput} payload.
     * Given URL starts from the version, so in order to update SObject specify
     * just {@code /sobjects/Account/identifier} which results in
     * {@code /services/data/v37.0/sobjects/Account/identifier}. Note the
     * leading slash.
     *
     * @param method HTTP method
     * @param url URL starting from the version
     * @param richInput body of the request, to be placed in richInput
     * @return this batch builder
     */
    public SObjectBatch addGeneric(final Method method, final String url, final Object richInput) {
        addBatchRequest(new BatchRequest(method, apiPrefix + url, richInput));

        return this;
    }

    /**
     * Add field retrieval of an SObject by identifier to the batch request.
     *
     * @param type type of SObject
     * @param id identifier of SObject
     * @param fields to return
     * @return this batch builder
     */
    public SObjectBatch addGet(final String type, final String id, final String... fields) {
        final String fieldsParameter = composeFieldsParameter(fields);

        addBatchRequest(new BatchRequest(Method.GET, rowBaseUrl(type, id) + fieldsParameter));

        return this;
    }

    /**
     * Add field retrieval of an SObject by external identifier to the batch
     * request.
     *
     * @param type type of SObject
     * @param fieldName external identifier field name
     * @param fieldValue external identifier field value
     * @return this batch builder
     */
    public SObjectBatch addGetByExternalId(final String type, final String fieldName, final String fieldValue) {
        addBatchRequest(new BatchRequest(Method.GET, rowBaseUrl(type, fieldName, fieldValue)));

        return this;
    }

    /**
     * Add retrieval of related SObject fields by identifier. For example
     * {@code Account} has a relation to {@code CreatedBy}. To fetch fields from
     * that related object ({@code User} SObject) use: <blockquote>
     *
     * <pre>
     * {@code batch.addGetRelated("Account", identifier, "CreatedBy", "Name", "Id")}
     * </pre>
     *
     * </blockquote>
     *
     * @param type type of SObject
     * @param id identifier of SObject
     * @param relation name of the related SObject field
     * @param fields to return
     * @return this batch builder
     */
    public SObjectBatch addGetRelated(final String type, final String id, final String relation, final String... fields) {
        version.requireAtLeast(36, 0);

        final String fieldsParameter = composeFieldsParameter(fields);

        addBatchRequest(new BatchRequest(Method.GET, rowBaseUrl(type, id) + "/" + notEmpty(relation, "relation") + fieldsParameter));

        return this;
    }

    /**
     * Add retrieval of limits to the batch.
     *
     * @return this batch builder
     */
    public SObjectBatch addLimits() {
        addBatchRequest(new BatchRequest(Method.GET, apiPrefix + "/limits/"));

        return this;
    }

    /**
     * Add retrieval of SObject records by query to the batch.
     *
     * @param query SOQL query to execute
     * @return this batch builder
     */
    public SObjectBatch addQuery(final String query) {
        addBatchRequest(new BatchRequest(Method.GET, apiPrefix + "/query/?q=" + notEmpty(query, "query")));

        return this;
    }

    /**
     * Add retrieval of all SObject records by query to the batch.
     *
     * @param query SOQL query to execute
     * @return this batch builder
     */
    public SObjectBatch addQueryAll(final String query) {
        addBatchRequest(new BatchRequest(Method.GET, apiPrefix + "/queryAll/?q=" + notEmpty(query, "query")));

        return this;
    }

    /**
     * Add retrieval of SObject records by search to the batch.
     *
     * @param searchString SOSL search to execute
     * @return this batch builder
     */
    public SObjectBatch addSearch(final String searchString) {
        addBatchRequest(new BatchRequest(Method.GET, apiPrefix + "/search/?q=" + notEmpty(searchString, "searchString")));

        return this;
    }

    /**
     * Add update of SObject record to the batch. The given {@code data}
     * parameter must contain only the fields that need updating and must not
     * contain the {@code Id} field. So set any fields to {@code null} that you
     * do not want changed along with {@code Id} field.
     *
     * @param type type of SObject
     * @param id identifier of SObject
     * @param data SObject with fields to change
     * @return this batch builder
     */
    public SObjectBatch addUpdate(final String type, final String id, final AbstractSObjectBase data) {
        addBatchRequest(new BatchRequest(Method.PATCH, rowBaseUrl(type, notEmpty(id, "data.Id")), data));

        return this;
    }

    /**
     * Add update of SObject record by external identifier to the batch. The
     * given {@code data} parameter must contain only the fields that need
     * updating and must not contain the {@code Id} field. So set any fields to
     * {@code null} that you do not want changed along with {@code Id} field.
     *
     * @param type type of SObject
     * @param fieldName name of the field holding the external identifier
     * @param fieldValue external identifier value
     * @param data SObject with fields to change
     * @return this batch builder
     */
    public SObjectBatch addUpdateByExternalId(final String type, final String fieldName, final String fieldValue, final AbstractSObjectBase data) {

        addBatchRequest(new BatchRequest(Method.PATCH, rowBaseUrl(type, fieldName, fieldValue), data));

        return this;
    }

    /**
     * Add insert or update of SObject record by external identifier to the
     * batch. The given {@code data} parameter must contain only the fields that
     * need updating and must not contain the {@code Id} field. So set any
     * fields to {@code null} that you do not want changed along with {@code Id}
     * field.
     *
     * @param type type of SObject
     * @param fieldName name of the field holding the external identifier
     * @param fieldValue external identifier value
     * @param data SObject with fields to change
     * @return this batch builder
     */
    public SObjectBatch addUpsertByExternalId(final String type, final String fieldName, final String fieldValue, final AbstractSObjectBase data) {

        return addUpdateByExternalId(type, fieldName, fieldValue, data);
    }

    /**
     * Fetches batch requests contained in this batch.
     *
     * @return all requests
     */
    public List<BatchRequest> getBatchRequests() {
        return Collections.unmodifiableList(batchRequests);
    }

    /**
     * Version of Salesforce API for this batch request.
     *
     * @return the version
     */
    @JsonIgnore
    public Version getVersion() {
        return version;
    }

    /**
     * Returns all object types nested within this batch, needed for
     * serialization.
     *
     * @return all object types in this batch
     */
    public Class[] objectTypes() {
        final Set<Class<?>> types = Stream
            .concat(Stream.of(SObjectBatch.class, BatchRequest.class), batchRequests.stream().map(BatchRequest::getRichInput).filter(Objects::nonNull).map(Object::getClass))
            .collect(Collectors.toSet());

        return types.toArray(new Class[types.size()]);
    }

    void addBatchRequest(final BatchRequest batchRequest) {
        if (batchRequests.size() >= MAX_BATCH) {
            throw new IllegalArgumentException("You can add up to " + MAX_BATCH + " requests in a single batch. Split your requests across multiple batches.");
        }
        batchRequests.add(batchRequest);
    }

    String rowBaseUrl(final String type, final String id) {
        return apiPrefix + "/sobjects/" + notEmpty(type, SOBJECT_TYPE_PARAM) + "/" + notEmpty(id, "id");
    }

    String rowBaseUrl(final String type, final String fieldName, final String fieldValue) {
        try {
            return apiPrefix + "/sobjects/" + notEmpty(type, SOBJECT_TYPE_PARAM) + "/" + notEmpty(fieldName, "fieldName") + "/"
                   + URLEncoder.encode(notEmpty(fieldValue, "fieldValue"), StandardCharsets.UTF_8.name());
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    String typeOf(final AbstractDescribedSObjectBase data) {
        return notNull(data, "data").description().getName();
    }
}
