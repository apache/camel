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
package org.apache.camel.component.solr.integration;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.solr.SolrComponent;
import org.apache.camel.component.solr.SolrConstants;
import org.apache.camel.component.solr.SolrEndpoint;
import org.apache.camel.component.solr.SolrOperation;
import org.apache.camel.component.solr.SolrUtils;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.CamelTestSupportHelper;
import org.apache.camel.test.infra.core.api.ConfigurableContext;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.apache.camel.test.infra.solr.services.SolrService;
import org.apache.camel.test.infra.solr.services.SolrServiceFactory;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class SolrTestSupport implements CamelTestSupportHelper, ConfigurableRoute, ConfigurableContext {

    @Order(1)
    @RegisterExtension
    public static final SolrService service = SolrServiceFactory.createService();

    @Order(2)
    @RegisterExtension
    public static final CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    static final Logger LOG = LoggerFactory.getLogger(SolrTestSupport.class);
    static final String DEFAULT_START_ENDPOINT = "direct:start";
    static final String DEFAULT_START_ENDPOINT_AUTO_COMMIT = DEFAULT_START_ENDPOINT + "AutoCommit";
    static final String DEFAULT_START_ENDPOINT_SPLIT_THEN_COMMIT = DEFAULT_START_ENDPOINT + "SplitThenCommit";
    static final String DEFAULT_SOLR_ENDPOINT = "solr:default";
    static final String DEFAULT_MOCK_ENDPOINT = "mock:result";
    static final String TEST_DATA_PATH_URI = Objects.requireNonNull(Path
            .of(Objects.requireNonNull(SolrTestSupport.class.getClassLoader().getResource("data/books.csv")).getFile())
            .getParent().toUri().toString());
    static final String TEST_ID = "test1";
    static final String TEST_ID2 = "test2";

    SolrEndpoint solrEndpoint;
    CamelContext context;
    ProducerTemplate template;
    ConsumerTemplate consumer;

    String prefix;

    @BeforeEach
    public void beforeEach(TestInfo testInfo) {
        // make use of the test method name to avoid collision
        prefix = testInfo.getDisplayName().toLowerCase() + "-";
        context = contextExtension.getContext();
        template = contextExtension.getProducerTemplate();
        consumer = contextExtension.getConsumerTemplate();
        // clean solr endpoints
        executeDeleteAll();
        solrEndpoint = context.getEndpoint(DEFAULT_SOLR_ENDPOINT, SolrEndpoint.class);
        solrEndpoint.getConfiguration().setRequestHandler(null);
        template.setDefaultEndpoint(solrEndpoint);
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return contextExtension;
    }

    protected CamelContext camelContext() {
        return getCamelContextExtension().getContext();
    }

    protected ProducerTemplate template() {
        return getCamelContextExtension().getProducerTemplate();
    }

    @ContextFixture
    @Override
    public void configureContext(CamelContext context) {
        final SolrComponent solrComponent = new SolrComponent();
        solrComponent.setHost(service.getSolrHost());
        solrComponent.setPort(service.getPort());
        solrComponent.setDefaultCollection(SolrConstants.DEFAULT_COLLECTION);
        context.addComponent("solr", solrComponent);
    }

    @RouteFixture
    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {
        final RouteBuilder routeBuilder = createRouteBuilder();
        if (routeBuilder != null) {
            context.addRoutes(routeBuilder);
        }
    }

    protected abstract RouteBuilder createRouteBuilder();

    public Exchange processRequest(String uri, Object body, Map<String, Object> headers) {
        ExchangeBuilder builder = ExchangeBuilder.anExchange(camelContext()).withBody(body);
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            builder.withHeader(entry.getKey(), entry.getValue());
        }
        return processRequest(uri, builder.build());
    }

    public Exchange processRequest(String uri, Exchange exchange) {
        return template.send(uri, exchange);
    }

    protected void solrInsertTestEntry() {
        solrInsertTestEntry(TEST_ID);
    }

    protected void solrInsertTestEntry(String id) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(SolrConstants.PARAM_OPERATION, SolrConstants.OPERATION_INSERT);
        headers.put("SolrField.id", id);
        template.sendBodyAndHeaders(DEFAULT_START_ENDPOINT, "", headers);
    }

    protected void solrCommit() {
        template.sendBodyAndHeaders(
                DEFAULT_START_ENDPOINT,
                null,
                Map.of(
                        SolrConstants.PARAM_OPERATION, SolrOperation.INSERT.name(),
                        SolrConstants.HEADER_PARAM_PREFIX + "commit", "true"));
    }

    public Exchange executeInsertFor(Object body) {
        return executeInsertFor(body, new HashMap<>(), true);
    }

    public Exchange executeInsertFor(Object body, boolean withCommit) {
        return executeInsertFor(body, new HashMap<>(), withCommit);
    }

    public Exchange executeInsertFor(Object body, Map<String, Object> headers) {
        return executeInsertFor(body, headers, true);
    }

    public Exchange executeInsertFor(Object body, Map<String, Object> headers, boolean withCommit) {
        ExchangeBuilder builder = ExchangeBuilder.anExchange(camelContext());
        builder.withBody(body);
        headers.forEach(builder::withHeader);
        return executeInsert(builder.build(), withCommit);
    }

    public Exchange executeInsert(Exchange exchange) {
        return executeInsert(exchange, true);
    }

    public Exchange executeInsert(Exchange exchange, boolean withCommit) {
        return executeInsert(DEFAULT_SOLR_ENDPOINT, exchange, withCommit);
    }

    public Exchange executeInsert(String uri, Exchange exchange, boolean withCommit) {
        exchange.getMessage().getHeaders().put(SolrConstants.PARAM_OPERATION, SolrOperation.INSERT);
        if (withCommit) {
            SolrUtils.addHeadersForCommit(exchange);
        }
        return processRequest(uri, exchange);
    }

    public void executeDeleteAll() {
        Exchange exchange = ExchangeBuilder.anExchange(camelContext())
                .withBody("*:*")
                .withHeader(SolrConstants.PARAM_OPERATION, SolrOperation.DELETE)
                .withHeader(SolrConstants.PARAM_DELETE_BY_QUERY, true)
                .build();
        SolrUtils.addHeadersForCommit(exchange);
        processRequest(DEFAULT_SOLR_ENDPOINT, exchange);
    }

    public QueryResponse executeSolrQuery(String queryString) {
        return executeSolrQuery(DEFAULT_SOLR_ENDPOINT, queryString);
    }

    public QueryResponse executeSolrQuery(String uri, String queryString) {
        Exchange exchange = ExchangeBuilder.anExchange(camelContext())
                .withHeader(SolrConstants.PARAM_OPERATION, SolrOperation.SEARCH)
                .withHeader(SolrConstants.PARAM_QUERY_STRING, queryString)
                .withHeader(SolrConstants.PARAM_REQUEST_HANDLER, null)
                .build();
        Exchange exchange1 = processRequest(uri, exchange);
        return exchange1.getMessage().getBody(QueryResponse.class);
    }

    public Exchange pingInstance(String uri) {
        return pingInstance(uri, SolrConstants.DEFAULT_COLLECTION);
    }

    public Exchange pingInstance(String uri, String collection) {
        return pingInstance(uri, collection == null ? Map.of() : Map.of(SolrConstants.PARAM_COLLECTION, collection));
    }

    public Exchange pingInstance(String uri, Map<String, Object> headers) {
        if (!headers.containsKey(SolrConstants.PARAM_OPERATION)) {
            headers = new HashMap<>(headers);
            headers.put(SolrConstants.PARAM_OPERATION, SolrOperation.PING);
        }
        return processRequest(uri, null, headers);
    }

}
