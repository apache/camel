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
package org.apache.camel.component.solr;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.WrappedFile;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClientBase;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.request.SolrPing;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Solr producer.
 */
public class SolrProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SolrProducer.class);

    protected final SolrConfiguration configuration;

    public SolrProducer(SolrEndpoint endpoint, SolrConfiguration config) {
        super(endpoint);
        this.configuration = config;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        Message message = exchange.getMessage();

        // Retrieve values from header or from config
        SolrClient solrClient = getSolrClient();
        String collection = Optional.ofNullable(message.getHeader(SolrConstants.PARAM_COLLECTION, String.class))
                .orElse(configuration.getCollection());
        String requestHandler = message.getHeaders().containsKey(SolrConstants.PARAM_REQUEST_HANDLER)
                ? message.getHeader(SolrConstants.PARAM_REQUEST_HANDLER, String.class)
                : configuration.getRequestHandler();

        // Retrieve all SolrParams: SolrParams header and SolrParam.xxx headers
        ModifiableSolrParams modifiableSolrParams = getAndGroupedSolrParams(message);
        boolean hasSolrParams = modifiableSolrParams.size() > 0;

        // add commit when autocommit is set
        if (configuration.isAutoCommit()) {
            SolrUtils.addHeadersForCommit(modifiableSolrParams);
        }

        // SolrRequest<?> are generated via SolrRequestConverter when not provided in exchange body:
        // The code below ensures the body is not null in order to ensure conversion via SolrRequestConverter is run
        // if conversion is not valid, an InvalidPayloadException will be thrown by the converter
        if (message.getBody() == null) {
            message.setBody(new Object());
        }

        // Determine solr operation
        final SolrOperation operation = resolveSolrOperation(exchange, hasSolrParams);

        // set action context and add to message to be accessible for converter methods
        ActionContext ctx = new ActionContext(
                configuration, exchange, callback, solrClient, operation, collection, requestHandler, modifiableSolrParams);
        exchange.setProperty(SolrConstants.PROPERTY_ACTION_CONTEXT, ctx);

        // perform solr operation
        boolean doneSync;
        try {
            SolrRequest<?> solrRequest = operation == null
                    ? exchange.getMessage().getMandatoryBody(SolrRequest.class)
                    : operation.getSolrRequest(ctx);
            doneSync = SolrEndpoint.isProcessAsync(ctx.solrClient(), ctx.configuration())
                    ? processSolrActionAsync(ctx, solrRequest)
                    : processSolrAction(ctx, solrRequest);
        } catch (Exception e) {
            exchange.setException(e);
            ctx.callback.done(true);
            return true;
        } finally {
            exchange.removeProperty(SolrConstants.PROPERTY_ACTION_CONTEXT);
        }
        return doneSync;
    }

    private boolean processSolrActionAsync(ActionContext ctx, final SolrRequest<?> solrRequest) {
        onComplete(ctx.getAsyncSolrClient().requestAsync(solrRequest, ctx.collection()), ctx);
        return false;
    }

    private boolean processSolrAction(ActionContext ctx, final SolrRequest<?> solrRequest)
            throws SolrServerException, IOException {
        ctx.exchange().getMessage().setBody(ctx.solrClient().request(solrRequest, ctx.collection()));
        ctx.callback.done(true);
        return true;
    }

    private static ModifiableSolrParams getAndGroupedSolrParams(Message message) {
        // use 'solrParams' header to initiate the ModifiableSolrParams
        SolrParams solrParams = message.getHeader(SolrConstants.PARAM_SOLR_PARAMS, SolrParams.class);
        ModifiableSolrParams modifiableSolrParams = solrParams instanceof ModifiableSolrParams
                ? (ModifiableSolrParams) solrParams
                : new ModifiableSolrParams(solrParams);
        // add possible headers that start with "SolrParam." prefix
        message.getHeaders().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(SolrConstants.HEADER_PARAM_PREFIX))
                .forEach(entry -> {
                    String paramName = entry.getKey().substring(SolrConstants.HEADER_PARAM_PREFIX.length());
                    modifiableSolrParams.add(paramName, entry.getValue().toString());
                });
        return modifiableSolrParams;
    }

    /**
     * Add actions to perform once the given future is complete.
     *
     * @param future the future to complete with specific actions.
     * @param ctx    the context of the asynchronous task.
     * @param <T>    the result type returned by the future.
     */
    private <T> void onComplete(CompletableFuture<T> future, ActionContext ctx) {
        final Exchange exchange = ctx.exchange();
        future.thenAccept(r -> exchange.getMessage().setBody(r))
                .whenComplete(
                        (r, e) -> {
                            try {
                                if (e != null) {
                                    exchange.setException(new CamelExchangeException(
                                            "An error occurred while executing the action", exchange, e));
                                }
                            } finally {
                                ctx.callback().done(false);
                            }
                        });
    }

    public SolrClient getSolrClient() {
        return ((SolrEndpoint) super.getEndpoint()).getSolrClient();
    }

    private SolrOperation resolveSolrOperation(Exchange exchange, boolean hasSolrParams) {
        // Operation can be driven by either (in order of preference):
        // a. If the body is a specific class (SolrQuery, QueryRequest, UpdateRequest, SolrInputDocument(=Map)).
        // b. If the body is not one of the specific classes, the operation is set by the
        // header if it exists.
        // c. If neither the operation can not be derived from the body or
        // header, the configuration is used.
        // In the event we can't discover the operation from a, b or c we throw
        // an error.

        Object body = exchange.getMessage().getBody();
        if (ObjectHelper.isNotEmpty(body)) {
            LOG.debug("Operation request body: {}", body);
        }
        if (body instanceof SolrPing) {
            return SolrOperation.PING;
        }
        if (body instanceof SolrQuery
                || body instanceof QueryRequest
                || exchange.getMessage().getHeader(SolrConstants.PARAM_QUERY_STRING) != null) {
            return SolrOperation.SEARCH;
        }
        if (body instanceof Map
                || body instanceof WrappedFile
                || body instanceof File
                || body instanceof UpdateRequest) {
            return SolrOperation.INSERT;
        }
        if (body instanceof SolrRequest) {
            return null;
        }
        // collection: if strings then delete else insert
        if (body instanceof Collection<?> collection) {
            return SolrUtils.isCollectionOfType(collection, String.class)
                    ? SolrOperation.DELETE
                    : SolrOperation.INSERT;
        }

        SolrOperation operation;
        String actionString = exchange.getMessage().getHeader(SolrConstants.PARAM_OPERATION, String.class);
        if (ObjectHelper.isNotEmpty(actionString)) {
            operation = SolrOperation.getSolrOperationFrom(actionString);
            if (operation != null && !operation.name().equalsIgnoreCase(actionString)) {
                LOG.warn(operation.createFutureDeprecationMessage(actionString,
                        operation.getActionParameter(actionString)));
            }
            LOG.debug("Operation obtained from header '{}': {}", SolrConstants.PARAM_OPERATION, actionString);
            return operation;
        }
        if (configuration.getOperation() != null) {
            operation = configuration.getOperation();
            LOG.debug("Operation obtained from config: {}", operation);
            return operation;
        }
        // when "invalid" body with solr params (e.g. commit),
        // assume insert request and allow processing without "body"
        if (hasSolrParams) {
            return SolrOperation.INSERT;
        }
        throw new IllegalArgumentException(
                SolrConstants.PARAM_OPERATION + " value is mandatory");
    }

    /**
     * An inner class providing all the information that an asynchronous action could need.
     */
    public record ActionContext(SolrConfiguration configuration,
            Exchange exchange,
            AsyncCallback callback,
            SolrClient solrClient,
            SolrOperation operation,
            String collection,
            String requestHandler,
            ModifiableSolrParams solrParams) {

        public ActionContext {
            ObjectHelper.notNull(solrClient, "SolrClient");
            ObjectHelper.notNull(collection, SolrConstants.PARAM_COLLECTION);
            ObjectHelper.notNull(solrParams, SolrConstants.PARAM_SOLR_PARAMS);
        }

        public HttpSolrClientBase getAsyncSolrClient() {
            if (solrClient instanceof HttpSolrClientBase httpSolrClientBase) {
                return httpSolrClientBase;
            }
            throw new UnsupportedOperationException(getAsyncProcessingErrorDetails(solrClient));
        }

        private static String getAsyncProcessingErrorDetails(SolrClient solrClient) {
            return String.format(
                    "Async processing requires a solr client instance of HttpSolrClientBase. This solr client is of type %s.",
                    solrClient.getClass().getCanonicalName());
        }

    }

}
