/**
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
package org.apache.camel.component.olingo4.api;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.olingo4.api.batch.Olingo4BatchResponse;
import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.http.HttpStatusCode;

/**
 * Olingo4 Client Api Interface.
 */
public interface Olingo4App {

    /**
     * Sets Service base URI.
     * @param serviceUri
     */
    void setServiceUri(String serviceUri);

    /**
     * Returns Service base URI.
     * @return service base URI.
     */
    String getServiceUri();

    /**
     * Sets custom Http headers to add to every service request.
     * @param httpHeaders custom Http headers.
     */
    void setHttpHeaders(Map<String, String> httpHeaders);

    /**
     * Returns custom Http headers.
     * @return custom Http headers.
     */
    Map<String, String> getHttpHeaders();

    /**
     * Returns content type for service calls. Defaults to <code>application/json;charset=utf-8</code>.
     * @return content type.
     */
    String getContentType();

    /**
     * Set default service call content type.
     * @param contentType content type.
     */
    void setContentType(String contentType);

    /**
     * Closes resources.
     */
    void close();

    /**
     * Reads an OData resource and invokes callback with appropriate result.
     * @param edm Service Edm, read from calling <code>read(null, "$metdata", null, responseHandler)</code>
     * @param resourcePath OData Resource path
     * @param queryParams OData query params
     *                    http://docs.oasis-open.org/odata/odata/v4.0/odata-v4.0-part1-protocol.html#_Toc453752288
     * @param endpointHttpHeaders HTTP Headers to add/override the component versions
     * @param responseHandler callback handler
     */
    <T> void read(Edm edm, String resourcePath, Map<String, String> queryParams, Map<String, String> endpointHttpHeaders, Olingo4ResponseHandler<T> responseHandler);

    /**
     * Reads an OData resource and invokes callback with the unparsed input stream.
     * @param edm Service Edm, read from calling <code>read(null, "$metdata", null, responseHandler)</code>
     * @param resourcePath OData Resource path
     * @param queryParams OData query params
     *                    http://docs.oasis-open.org/odata/odata/v4.0/odata-v4.0-part1-protocol.html#_Toc453752288
     * @param endpointHttpHeaders HTTP Headers to add/override the component versions
     * @param responseHandler callback handler
     */
    void uread(Edm edm, String resourcePath, Map<String, String> queryParams, Map<String, String> endpointHttpHeaders, Olingo4ResponseHandler<InputStream> responseHandler);

    /**
     * Deletes an OData resource and invokes callback
     * with {@link org.apache.olingo.commons.api.http.HttpStatusCode} on success, or with exception on failure.
     * @param resourcePath resource path for Entry
     * @param endpointHttpHeaders HTTP Headers to add/override the component versions
     * @param responseHandler {@link org.apache.olingo.commons.api.http.HttpStatusCode} callback handler
     */
    void delete(String resourcePath, Map<String, String> endpointHttpHeaders, Olingo4ResponseHandler<HttpStatusCode> responseHandler);

    /**
     * Creates a new OData resource.
     * @param edm service Edm
     * @param resourcePath resource path to create
     * @param endpointHttpHeaders HTTP Headers to add/override the component versions
     * @param data request data
     * @param responseHandler callback handler
     */
    <T> void create(Edm edm, String resourcePath, Map<String, String> endpointHttpHeaders, Object data, Olingo4ResponseHandler<T> responseHandler);

    /**
     * Updates an OData resource.
     * @param edm service Edm
     * @param resourcePath resource path to update
     * @param endpointHttpHeaders HTTP Headers to add/override the component versions
     * @param data updated data
     * @param responseHandler {@link org.apache.olingo.client.api.domain.ClientEntity} callback handler
     */
    <T> void update(Edm edm, String resourcePath, Map<String, String> endpointHttpHeaders, Object data, Olingo4ResponseHandler<T> responseHandler);

    /**
     * Patches/merges an OData resource using HTTP PATCH.
     * @param edm service Edm
     * @param resourcePath resource path to update
     * @param endpointHttpHeaders HTTP Headers to add/override the component versions
     * @param data patch/merge data
     * @param responseHandler {@link org.apache.olingo.client.api.domain.ClientEntity} callback handler
     */
    <T> void patch(Edm edm, String resourcePath, Map<String, String> endpointHttpHeaders, Object data, Olingo4ResponseHandler<T> responseHandler);

    /**
     * Patches/merges an OData resource using HTTP MERGE.
     * @param edm service Edm
     * @param resourcePath resource path to update
     * @param endpointHttpHeaders HTTP Headers to add/override the component versions
     * @param data patch/merge data
     * @param responseHandler {@link org.apache.olingo.client.api.domain.ClientEntity} callback handler
     */
    <T> void merge(Edm edm, String resourcePath, Map<String, String> endpointHttpHeaders, Object data, Olingo4ResponseHandler<T> responseHandler);

    /**
     * Executes a batch request.
     * @param edm service Edm
     * @param endpointHttpHeaders HTTP Headers to add/override the component versions
     * @param data ordered {@link org.apache.camel.component.olingo4.api.batch.Olingo4BatchRequest} list
     * @param responseHandler callback handler
     */
    void batch(Edm edm, Map<String, String> endpointHttpHeaders, Object data, Olingo4ResponseHandler<List<Olingo4BatchResponse>> responseHandler);
}
