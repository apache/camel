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
package org.apache.camel.component.opensearch;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.opensearch.client.RestClient;
import org.opensearch.client.opensearch.OpenSearchClient;

/**
 * Send requests to OpenSearch via Java Client API.
 */
@UriEndpoint(firstVersion = "4.0.0", scheme = "opensearch", title = "OpenSearch",
             syntax = "opensearch:clusterName", producerOnly = true,
             category = { Category.SEARCH, Category.MONITORING }, headersClass = OpensearchConstants.class)
@BodyTypes(producerIn = @BodyType(value = {
        Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.MAP, Carrier.LIST, Carrier.POJO },
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "Index",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE,
                                                                 Carrier.MAP, Carrier.POJO }),
                                          @BodyType.When(option = "operation", value = "Update",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE,
                                                                 Carrier.MAP, Carrier.POJO }),
                                          @BodyType.When(option = "operation", value = "Bulk",
                                                         carriers = { Carrier.LIST, Carrier.POJO }),
                                          @BodyType.When(option = "operation", value = "GetById",
                                                         carriers = { Carrier.TEXT, Carrier.POJO }),
                                          @BodyType.When(option = "operation", value = "MultiGet",
                                                         carriers = { Carrier.LIST, Carrier.POJO }),
                                          @BodyType.When(option = "operation", value = "MultiSearch",
                                                         carriers = Carrier.POJO),
                                          @BodyType.When(option = "operation", value = "Delete",
                                                         carriers = { Carrier.TEXT, Carrier.POJO }),
                                          @BodyType.When(option = "operation", value = "DeleteIndex",
                                                         carriers = { Carrier.TEXT, Carrier.POJO }),
                                          @BodyType.When(option = "operation", value = "Search",
                                                         carriers = { Carrier.TEXT, Carrier.MAP, Carrier.POJO }),
                                          @BodyType.When(option = "operation", value = "Exists", carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "Ping", carriers = Carrier.ANY) },
                                  description = "By the operation, set by the operation option or the CamelOpensearchOperation header,"
                                                + " or by the request of the OpenSearch client in the body. Index and Update"
                                                + " take the document as JSON text, bytes, a stream or a file of it, or a Map"
                                                + " or a POJO (written as JSON); text that is not JSON, a List or no body fail."
                                                + " Update takes the partial document, or the Update API form {\"doc\": {...}}. GetById and Delete take the id as text, Bulk and MultiGet a List"
                                                + " of documents or ids, Search the search request as JSON text or a Map, with"
                                                + " its query, size, sort, aggregations and the rest (an object with a single"
                                                + " member other than query is the query alone), and DeleteIndex the name of"
                                                + " the index as text, used when neither the CamelOpensearchIndexName header"
                                                + " nor indexName is set. Each also takes its request of the OpenSearch"
                                                + " client, or the builder of it, which is sent as it is; MultiSearch takes"
                                                + " only those. Exists and Ping do not use the body"),
           producerOut = @BodyType(value = { Carrier.TEXT, Carrier.VALUE, Carrier.POJO, Carrier.LIST, Carrier.ITERATOR },
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "Index", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "Update", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "Bulk", carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "GetById", carriers = Carrier.POJO,
                                                          javaType = "org.opensearch.client.opensearch.core.GetResponse"),
                                           @BodyType.When(option = "operation", value = "MultiGet", carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "MultiSearch",
                                                          carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "Delete", carriers = Carrier.VALUE,
                                                          javaType = "org.opensearch.client.opensearch._types.Result"),
                                           @BodyType.When(option = "operation", value = "DeleteIndex",
                                                          carriers = Carrier.VALUE, javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "Search",
                                                          carriers = { Carrier.POJO, Carrier.ITERATOR }),
                                           @BodyType.When(option = "operation", value = "Exists", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "Ping", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean") },
                                   description = "The id of the document as text for Index and Update. GetById gives the"
                                                 + " GetResponse of the OpenSearch client (found() is false for no document),"
                                                 + " Search the HitsMetadata of the hits, or with useScroll an"
                                                 + " OpensearchScrollRequestIterator of the hits to close after use; the source"
                                                 + " of a hit is a Jackson ObjectNode unless documentClass is set. MultiGet,"
                                                 + " MultiSearch and Bulk give a List of the response item of each document or"
                                                 + " search, Delete the Result enum (Deleted or NotFound), and DeleteIndex,"
                                                 + " Exists and Ping a Boolean"))
public class OpensearchEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @UriParam
    private final OpensearchConfiguration configuration;

    private final RestClient client;

    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private OpenSearchClient openSearchClient;

    public OpensearchEndpoint(String uri, OpensearchComponent component, OpensearchConfiguration config,
                              RestClient client, OpenSearchClient openSearchClient) {
        super(uri, component);
        this.configuration = config;
        this.client = client;
        this.openSearchClient = openSearchClient;
    }

    public OpensearchConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public Producer createProducer() {
        return new OpensearchProducer(this, configuration);
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException("Cannot consume from an OpenSearch: " + getEndpointUri());
    }

    public RestClient getClient() {
        return client;
    }

    public OpenSearchClient getOpenSearchClient() {
        return openSearchClient;
    }

    /**
     * To use a custom configured OpenSearchClient instance for this endpoint. This takes precedence over the
     * component's OpenSearchClient and RestClient options. The application is responsible for closing the client.
     */
    public void setOpenSearchClient(OpenSearchClient openSearchClient) {
        this.openSearchClient = openSearchClient;
    }

    @Override
    public String getServiceUrl() {
        return configuration.getHostAddresses();
    }

    @Override
    public String getServiceProtocol() {
        return "opensearch";
    }
}
