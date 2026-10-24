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
package org.apache.camel.component.es;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.elasticsearch.client.RestClient;

/**
 * Send requests to Elasticsearch via Java Client API.
 */
@UriEndpoint(firstVersion = "3.19.0", scheme = "elasticsearch", title = "Elasticsearch",
             syntax = "elasticsearch:clusterName", producerOnly = true,
             category = { Category.SEARCH, Category.MONITORING }, headersClass = ElasticsearchConstants.class)
@BodyTypes(producerIn = @BodyType(value = {
        Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.MAP, Carrier.LIST, Carrier.POJO },
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "Index",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.MAP,
                                                                 Carrier.POJO }),
                                          @BodyType.When(option = "operation", value = "Update",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.MAP,
                                                                 Carrier.POJO }),
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
                                  description = "By the operation, set by the operation option or the CamelElasticsearchOperation header."
                                                + " Index and Update take the document as JSON text, bytes or a stream, a Map"
                                                + " or a POJO (written as JSON), or the request builder of the Elasticsearch"
                                                + " client; text that is not JSON, a List, a java.io.File or no body fail."
                                                + " Update expects the Update API form, {\"doc\": {...}}, unless enableDocumentOnlyMode is set. GetById and Delete take the id as text, Bulk and MultiGet a List"
                                                + " of documents or ids, Search a query as JSON text or a Map (only its query"
                                                + " member when it has one), and MultiSearch only an MsearchRequest.Builder;"
                                                + " each also takes its request builder. DeleteIndex needs a text body but"
                                                + " deletes the index of the CamelElasticsearchIndexName header or indexName, not the one"
                                                + " in the body. Exists and Ping do not use the body"),
           producerOut = @BodyType(value = { Carrier.TEXT, Carrier.VALUE, Carrier.POJO, Carrier.LIST, Carrier.ITERATOR },
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "Index", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "Update", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "Bulk", carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "GetById", carriers = Carrier.POJO,
                                                          javaType = "co.elastic.clients.elasticsearch.core.GetResponse"),
                                           @BodyType.When(option = "operation", value = "MultiGet", carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "MultiSearch",
                                                          carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "Delete", carriers = Carrier.VALUE,
                                                          javaType = "co.elastic.clients.elasticsearch._types.Result"),
                                           @BodyType.When(option = "operation", value = "DeleteIndex",
                                                          carriers = Carrier.VALUE, javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "Search",
                                                          carriers = { Carrier.POJO, Carrier.ITERATOR }),
                                           @BodyType.When(option = "operation", value = "Exists", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "Ping", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean") },
                                   description = "The id of the document as text for Index and Update. GetById gives the"
                                                 + " GetResponse of the Elasticsearch client (found() is false for no document),"
                                                 + " Search the HitsMetadata of the hits, or with useScroll an"
                                                 + " ElasticsearchScrollRequestIterator of the hits to close after use; the source"
                                                 + " of a hit is a Jackson ObjectNode unless documentClass is set. MultiGet,"
                                                 + " MultiSearch and Bulk give a List of the response item of each document or"
                                                 + " search, Delete the Result enum (Deleted or NotFound), and DeleteIndex,"
                                                 + " Exists and Ping a Boolean"))
public class ElasticsearchEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @UriParam
    private final ElasticsearchConfiguration configuration;

    private final RestClient client;

    public ElasticsearchEndpoint(String uri, ElasticsearchComponent component, ElasticsearchConfiguration config,
                                 RestClient client) {
        super(uri, component);
        this.configuration = config;
        this.client = client;
    }

    public ElasticsearchConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public String getServiceUrl() {
        return getConfiguration().getHostAddresses();
    }

    @Override
    public String getServiceProtocol() {
        return "elasticsearch";
    }

    @Override
    public Producer createProducer() {
        return new ElasticsearchProducer(this, configuration);
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException("Cannot consume from an ElasticsearchEndpoint: " + getEndpointUri());
    }

    public RestClient getClient() {
        return client;
    }
}
