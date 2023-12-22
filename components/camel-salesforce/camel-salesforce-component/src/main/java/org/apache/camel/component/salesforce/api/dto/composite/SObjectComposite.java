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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.camel.component.salesforce.api.dto.AbstractDescribedSObjectBase;
import org.apache.camel.component.salesforce.api.dto.AbstractSObjectBase;
import org.apache.camel.component.salesforce.api.utils.UrlUtils;
import org.apache.camel.component.salesforce.api.utils.Version;
import org.apache.camel.component.salesforce.internal.PayloadFormat;

import static org.apache.camel.util.ObjectHelper.notNull;
import static org.apache.camel.util.StringHelper.notEmpty;

/**
 * Executes a series of REST API requests in a single call. You can use the output of one request as the input to a
 * subsequent request. The response bodies and HTTP statuses of the requests are returned in a single response body. The
 * entire request counts as a single call toward your API limits. The requests in a composite call are called
 * subrequests. All subrequests are executed in the context of the same user. In a subrequest’s body, you specify a
 * reference ID that maps to the subrequest’s response. You can then refer to the ID in the url or body fields of later
 * subrequests by using a JavaScript-like reference notation. Most requests that are supported in the Composite batch
 * API the helper builder methods are provided. For batch requests that do not have their corresponding helper builder
 * method, use {@link #addGeneric(Method, String, String)} or {@link #addGeneric(Method, String, Object, String)}
 * methods. To build the batch use: <blockquote>
 *
 * <pre>
 * {@code
 * composite = new SObjectComposite("41.0", true);
 *
 * // insert operation via an external id
 * final Invoice__c_Lookup invoiceLookup = new Invoice__c_Lookup();
 * invoiceLookup.setInvoice_External_Id__c("0116");
 *
 * final Payment__c payment = new Payment__c();
 * payment.setInvoice__r(invoiceLookup);
 *
 * composite.addCreate(payment, "NewPayment1");
 * composite.addCreate(payment, "NewPayment2");
 * }
 * </pre>
 *
 * </blockquote> This will build a composite of two insert operations.
 */
public final class SObjectComposite implements Serializable {

    public enum Method {
        DELETE,
        GET,
        PATCH,
        POST
    }

    public static final PayloadFormat REQUIRED_PAYLOAD_FORMAT = PayloadFormat.JSON;

    private static final int MAX_COMPOSITE_OPERATIONS = 25;

    private static final long serialVersionUID = 1L;

    private static final String SOBJECT_TYPE_PARAM = "type";

    private final boolean allOrNone;

    private final String apiPrefix;

    private final List<CompositeRequest> compositeRequests = new ArrayList<>();

    private final Version version;

    /**
     * Create new composite request. You must specify the API version of the batch request. The API version cannot be
     * newer than the version configured in the Salesforce Camel component. Some of the batched requests are available
     * only from certain Salesforce API versions, when this is the case it is noted in the documentation of the builder
     * method, if uncertain consult the Salesforce API documentation.
     *
     * @param apiVersion API version for the batch request
     */
    public SObjectComposite(final String apiVersion, final boolean allOrNone) {
        Objects.requireNonNull(apiVersion, "apiVersion");

        version = Version.create(apiVersion);
        this.allOrNone = allOrNone;
        // composite API requires /services/data, in contrast to composite-batch
        apiPrefix = "/services/data/v" + apiVersion;
    }

    /**
     * Add create SObject to the composite request.
     *
     * @param  data object to create
     * @return      this batch builder
     */
    public SObjectComposite addCreate(final AbstractDescribedSObjectBase data, final String referenceId) {
        addCompositeRequest(
                new CompositeRequest(Method.POST, apiPrefix + "/sobjects/" + typeOf(data) + "/", data, referenceId));

        return this;
    }

    /**
     * Add delete SObject with identifier to the composite request.
     *
     * @param  type type of SObject
     * @param  id   identifier of the object
     * @return      this batch builder
     */
    public SObjectComposite addDelete(final String type, final String id, final String referenceId) {
        addCompositeRequest(new CompositeRequest(Method.DELETE, rowBaseUrl(type, id), referenceId));

        return this;
    }

    /**
     * Generic way to add requests to composite with {@code richInput} payload. Given URL starts from the version, so in
     * order to update SObject specify just {@code /sobjects/Account/identifier} which results in
     * {@code /services/data/v37.0/sobjects/Account/identifier}. Note the leading slash.
     *
     * @param  method    HTTP method
     * @param  url       URL starting from the version
     * @param  richInput body of the request, to be placed in richInput
     * @return           this batch builder
     */
    public SObjectComposite addGeneric(
            final Method method, final String url, final Object richInput, final String referenceId) {
        addCompositeRequest(new CompositeRequest(method, apiPrefix + url, richInput, referenceId));

        return this;
    }

    /**
     * Generic way to add requests to composite. Given URL starts from the version, so in order to retrieve SObject
     * specify just {@code /sobjects/Account/identifier} which results in
     * {@code /services/data/v37.0/sobjects/Account/identifier}. Note the leading slash.
     *
     * @param  method HTTP method
     * @param  url    URL starting from the version
     * @return        this batch builder
     */
    public SObjectComposite addGeneric(final Method method, final String url, final String referenceId) {
        addGeneric(method, url, null, referenceId);

        return this;
    }

    /**
     * Add field retrieval of an SObject by identifier to the composite request.
     *
     * @param  type   type of SObject
     * @param  id     identifier of SObject
     * @param  fields to return
     * @return        this batch builder
     */
    public SObjectComposite addGet(final String type, final String id, final String referenceId, final String... fields) {
        final String fieldsParameter = composeFieldsParameter(fields);

        addCompositeRequest(new CompositeRequest(Method.GET, rowBaseUrl(type, id) + fieldsParameter, referenceId));

        return this;
    }

    /**
     * Add field retrieval of an SObject by external identifier to the composite request.
     *
     * @param  type       type of SObject
     * @param  fieldName  external identifier field name
     * @param  fieldValue external identifier field value
     * @return            this batch builder
     */
    public SObjectComposite addGetByExternalId(
            final String type, final String fieldName, final String fieldValue, final String referenceId) {
        addCompositeRequest(new CompositeRequest(Method.GET, rowBaseUrl(type, fieldName, fieldValue), referenceId));

        return this;
    }

    /**
     * Add retrieval of related SObject fields by identifier. For example {@code Account} has a relation to
     * {@code CreatedBy}. To fetch fields from that related object ({@code User} SObject) use: <blockquote>
     *
     * <pre>
     * {@code
     * batch.addGetRelated("Account", identifier, "CreatedBy", "Name", "Id")
     * }
     * </pre>
     *
     * </blockquote>
     *
     * @param  type     type of SObject
     * @param  id       identifier of SObject
     * @param  relation name of the related SObject field
     * @param  fields   to return
     * @return          this batch builder
     */
    public SObjectComposite addGetRelated(
            final String type, final String id, final String relation, final String referenceId, final String... fields) {
        version.requireAtLeast(36, 0);

        final String fieldsParameter = composeFieldsParameter(fields);

        addCompositeRequest(new CompositeRequest(
                Method.GET, rowBaseUrl(type, id) + "/" + notEmpty(relation, "relation") + fieldsParameter, referenceId));

        return this;
    }

    /**
     * Add retrieval of SObject records by query to the composite.
     *
     * @param  query SOQL query to execute
     * @return       this batch builder
     */
    public SObjectComposite addQuery(final String query, final String referenceId) {
        addCompositeRequest(new CompositeRequest(Method.GET, apiPrefix + "/query/?q=" + notEmpty(query, "query"), referenceId));

        return this;
    }

    /**
     * Add retrieval of all SObject records by query to the composite.
     *
     * @param  query SOQL query to execute
     * @return       this batch builder
     */
    public SObjectComposite addQueryAll(final String query, final String referenceId) {
        addCompositeRequest(
                new CompositeRequest(Method.GET, apiPrefix + "/queryAll/?q=" + notEmpty(query, "query"), referenceId));

        return this;
    }

    /**
     * Add update of SObject record to the composite. The given {@code data} parameter must contain only the fields that
     * need updating and must not contain the {@code Id} field. So set any fields to {@code null} that you do not want
     * changed along with {@code Id} field.
     *
     * @param  type type of SObject
     * @param  id   identifier of SObject
     * @param  data SObject with fields to change
     * @return      this batch builder
     */
    public SObjectComposite addUpdate(
            final String type, final String id, final AbstractSObjectBase data, final String referenceId) {
        addCompositeRequest(new CompositeRequest(Method.PATCH, rowBaseUrl(type, notEmpty(id, "data.Id")), data, referenceId));

        return this;
    }

    /**
     * Add update of SObject record by external identifier to the composite. The given {@code data} parameter must
     * contain only the fields that need updating and must not contain the {@code Id} field. So set any fields to
     * {@code null} that you do not want changed along with {@code Id} field.
     *
     * @param  type       type of SObject
     * @param  fieldName  name of the field holding the external identifier
     * @param  fieldValue external identifier field value
     * @param  data       SObject with fields to change
     * @return            this batch builder
     */
    public SObjectComposite addUpdateByExternalId(
            final String type, final String fieldName, final String fieldValue, final AbstractSObjectBase data,
            final String referenceId) {

        addCompositeRequest(new CompositeRequest(Method.PATCH, rowBaseUrl(type, fieldName, fieldValue), data, referenceId));

        return this;
    }

    /**
     * Add insert or update of SObject record by external identifier to the composite. The given {@code data} parameter
     * must contain only the fields that need updating and must not contain the {@code Id} field. So set any fields to
     * {@code null} that you do not want changed along with {@code Id} field.
     *
     * @param  type       type of SObject
     * @param  fieldName  name of the field holding the external identifier
     * @param  fieldValue external identifier field value
     * @param  data       SObject with fields to change
     * @return            this batch builder
     */
    public SObjectComposite addUpsertByExternalId(
            final String type, final String fieldName, final String fieldValue, final AbstractSObjectBase data,
            final String referenceId) {

        return addUpdateByExternalId(type, fieldName, fieldValue, data, referenceId);
    }

    public boolean getAllOrNone() {
        return allOrNone;
    }

    /**
     * Fetches compose requests contained in this compose request.
     *
     * @return all requests
     */
    @JsonProperty("compositeRequest")
    public List<CompositeRequest> getCompositeRequests() {
        return Collections.unmodifiableList(compositeRequests);
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
     * Returns all object types nested within this composite request, needed for serialization.
     *
     * @return all object types in this composite request
     */
    @SuppressWarnings("rawtypes")
    public Class[] objectTypes() {

        return Stream
                .concat(Stream.of(SObjectComposite.class, BatchRequest.class),
                        compositeRequests.stream().map(CompositeRequest::getBody).filter(Objects::nonNull)
                                .map(Object::getClass))
                .distinct().toArray(Class[]::new);
    }

    void addCompositeRequest(final CompositeRequest compositeRequest) {
        if (compositeRequests.size() >= MAX_COMPOSITE_OPERATIONS) {
            throw new IllegalArgumentException(
                    "You can add up to " + MAX_COMPOSITE_OPERATIONS
                                               + " requests in a single composite request. Split your requests across multiple composite request.");
        }
        compositeRequests.add(compositeRequest);
    }

    String rowBaseUrl(final String type, final String id) {
        return apiPrefix + "/sobjects/" + notEmpty(type, SOBJECT_TYPE_PARAM) + "/" + notEmpty(id, "id");
    }

    String rowBaseUrl(final String type, final String fieldName, final String fieldValue) {
        try {
            return apiPrefix + "/sobjects/" + notEmpty(type, SOBJECT_TYPE_PARAM) + "/" + notEmpty(fieldName, "fieldName") + "/"
                   + UrlUtils.encodePath(notEmpty(fieldValue, "fieldValue"));
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    static String typeOf(final AbstractDescribedSObjectBase data) {
        return notNull(data, "data").description().getName();
    }

    static String composeFieldsParameter(final String... fields) {
        if (fields != null && fields.length > 0) {
            try {
                return "?fields=" + URLEncoder.encode(String.join(",", fields), StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        }

        return "";
    }
}
