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

package org.apache.camel.component.milvus;

import java.util.concurrent.ExecutorService;

import io.milvus.client.MilvusClient;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.QueryResults;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.UpsertParam;
import io.milvus.param.highlevel.dml.SearchSimpleParam;
import io.milvus.param.highlevel.dml.response.SearchResponse;
import io.milvus.param.index.CreateIndexParam;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.support.DefaultProducer;

public class MilvusProducer extends DefaultProducer {
    private MilvusClient client;
    private ExecutorService executor;

    public MilvusProducer(MilvusEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public MilvusEndpoint getEndpoint() {
        return (MilvusEndpoint) super.getEndpoint();
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        this.client = getEndpoint().getClient();
    }

    @Override
    public void process(Exchange exchange) {
        final Message in = exchange.getMessage();
        final MilvusAction action = in.getHeader(MilvusHeaders.ACTION, MilvusAction.class);

        try {
            if (action == null) {
                throw new NoSuchHeaderException("The action is a required header", exchange, MilvusHeaders.ACTION);
            }

            switch (action) {
                case CREATE_COLLECTION:
                    createCollection(exchange);
                    break;
                case CREATE_INDEX:
                    createIndex(exchange);
                    break;
                case UPSERT:
                    upsert(exchange);
                    break;
                case INSERT:
                    insert(exchange);
                    break;
                case SEARCH:
                    search(exchange);
                    break;
                case QUERY:
                    query(exchange);
                    break;
                case DELETE:
                    delete(exchange);
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported action: " + action.name());
            }
        } catch (Exception e) {
            exchange.setException(e);
        }
    }

    // ***************************************
    //
    // Actions
    //
    // ***************************************

    @SuppressWarnings({"unchecked"})
    private void upsert(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        final UpsertParam upsert = in.getMandatoryBody(UpsertParam.class);

        R<MutationResult> result = this.client.upsert(upsert);

        handleResponseStatus(result);
        populateResponse(result, exchange);
    }

    @SuppressWarnings({"unchecked"})
    private void insert(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        final InsertParam insert = in.getMandatoryBody(InsertParam.class);

        R<MutationResult> result = this.client.insert(insert);

        handleResponseStatus(result);
        populateResponse(result, exchange);
    }

    private void createCollection(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        final CreateCollectionParam body = in.getMandatoryBody(CreateCollectionParam.class);

        R<RpcStatus> result = this.client.createCollection(body);

        handleResponseStatus(result);
        populateResponse(result, exchange);
    }

    private void createIndex(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        final CreateIndexParam body = in.getMandatoryBody(CreateIndexParam.class);

        R<RpcStatus> result = this.client.createIndex(body);

        handleResponseStatus(result);
        populateResponse(result, exchange);
    }

    private void search(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        final SearchSimpleParam body = in.getMandatoryBody(SearchSimpleParam.class);

        this.client.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(getEndpoint().getCollection())
                .withSyncLoad(true)
                .build());
        R<SearchResponse> result = this.client.search(body);

        handleResponseStatus(result);
        populateResponse(result, exchange);
    }

    private void query(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        final QueryParam body = in.getMandatoryBody(QueryParam.class);

        this.client.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(getEndpoint().getCollection())
                .withSyncLoad(true)
                .build());
        R<QueryResults> result = this.client.query(body);

        handleResponseStatus(result);
        populateResponse(result, exchange);
    }

    private void delete(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        final DeleteParam body = in.getMandatoryBody(DeleteParam.class);

        R<MutationResult> result = this.client.delete(body);

        handleResponseStatus(result);
        populateResponse(result, exchange);
    }

    // ***************************************
    //
    // Helpers
    //
    // ***************************************

    private CamelContext getCamelContext() {
        return getEndpoint().getCamelContext();
    }

    private void handleResponseStatus(R<?> r) {
        if (r.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException(r.getMessage());
        }
    }

    private void populateResponse(R<?> r, Exchange exchange) {
        Message out = exchange.getMessage();
        out.setHeader(MilvusHeaders.OPERATION_STATUS, r.getStatus());
        out.setHeader(MilvusHeaders.OPERATION_STATUS_VALUE, r.getStatus().intValue());
        out.setBody(r.getData());
    }
}
