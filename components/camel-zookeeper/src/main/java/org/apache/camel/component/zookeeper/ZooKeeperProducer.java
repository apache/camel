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
package org.apache.camel.component.zookeeper;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.zookeeper.operations.CreateOperation;
import org.apache.camel.component.zookeeper.operations.DeleteOperation;
import org.apache.camel.component.zookeeper.operations.GetChildrenOperation;
import org.apache.camel.component.zookeeper.operations.OperationResult;
import org.apache.camel.component.zookeeper.operations.SetDataOperation;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.ExchangeHelper;
import org.apache.zookeeper.AsyncCallback.StatCallback;
import org.apache.zookeeper.AsyncCallback.VoidCallback;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.zookeeper.ZooKeeperUtils.getAclListFromMessage;
import static org.apache.camel.component.zookeeper.ZooKeeperUtils.getCreateMode;
import static org.apache.camel.component.zookeeper.ZooKeeperUtils.getCreateModeFromString;
import static org.apache.camel.component.zookeeper.ZooKeeperUtils.getNodeFromMessage;
import static org.apache.camel.component.zookeeper.ZooKeeperUtils.getPayloadFromExchange;
import static org.apache.camel.component.zookeeper.ZooKeeperUtils.getVersionFromMessage;

/**
 * <code>ZooKeeperProducer</code> attempts to set the content of nodes in the {@link ZooKeeper} cluster with the
 * payloads of the of the exchanges it receives.
 */
@SuppressWarnings("rawtypes")
public class ZooKeeperProducer extends DefaultProducer {

    public static final String ZK_OPERATION_WRITE = "WRITE";
    public static final String ZK_OPERATION_DELETE = "DELETE";

    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperProducer.class);

    private final ZooKeeperConfiguration configuration;
    private ZooKeeperConnectionManager zkm;
    private ZooKeeper connection;

    public ZooKeeperProducer(ZooKeeperEndpoint endpoint) {
        super(endpoint);
        this.configuration = endpoint.getConfiguration();
        this.zkm = endpoint.getConnectionManager();
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        if (connection == null) {
            connection = this.zkm.getConnection();
        }
        ProductionContext context = new ProductionContext(connection, exchange);

        String operation = exchange.getIn().getHeader(ZooKeeperMessage.ZOOKEEPER_OPERATION, String.class);
        boolean isDelete = ZK_OPERATION_DELETE.equals(operation);

        if (ExchangeHelper.isOutCapable(exchange)) {
            if (isDelete) {
                LOG.debug("Deleting znode '{}', waiting for confirmation", context.node);

                OperationResult result = synchronouslyDelete(context);
                if (configuration.isListChildren()) {
                    result = listChildren(context);
                }
                updateExchangeWithResult(context, result);
            } else {
                LOG.debug("Storing data to znode '{}', waiting for confirmation", context.node);

                OperationResult result = synchronouslySetData(context);
                if (configuration.isListChildren()) {
                    result = listChildren(context);
                }
                updateExchangeWithResult(context, result);
            }
        } else {
            if (isDelete) {
                asynchronouslyDeleteNode(connection, context);
            } else {
                asynchronouslySetDataOnNode(connection, context);
            }
        }

    }

    @Override
    protected void doStart() throws Exception {
        connection = zkm.getConnection();
        if (LOG.isTraceEnabled()) {
            LOG.trace("Starting zookeeper producer of '{}'", configuration.getPath());
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (LOG.isTraceEnabled()) {
            LOG.trace("Shutting down zookeeper producer of '{}'", configuration.getPath());
        }
        zkm.shutdown();
    }

    private void asynchronouslyDeleteNode(ZooKeeper connection, ProductionContext context) {
        LOG.debug("Deleting node '{}', not waiting for confirmation", context.node);

        connection.delete(context.node, context.version, new AsyncDeleteCallback(), context);

    }

    private void asynchronouslySetDataOnNode(ZooKeeper connection, ProductionContext context) {
        LOG.debug("Storing data to node '{}', not waiting for confirmation", context.node);

        connection.setData(context.node, context.payload, context.version, new AsyncSetDataCallback(), context);
    }

    private void updateExchangeWithResult(ProductionContext context, OperationResult result) {
        ZooKeeperMessage out = new ZooKeeperMessage(
                getEndpoint().getCamelContext(), context.node, result.getStatistics(), context.in.getHeaders());
        if (result.isOk()) {
            out.setBody(result.getResult());
        } else {
            context.exchange.setException(result.getException());
        }

        context.exchange.setMessage(out);
    }

    private OperationResult listChildren(ProductionContext context) throws Exception {
        return new GetChildrenOperation(context.connection, configuration.getPath()).get();
    }

    /** Simple container to avoid passing all these around as parameters */
    private class ProductionContext {
        ZooKeeper connection;
        Exchange exchange;
        Message in;
        byte[] payload;
        int version;
        String node;

        ProductionContext(ZooKeeper connection, Exchange exchange) {
            this.connection = connection;
            this.exchange = exchange;
            this.in = exchange.getIn();
            this.node = getNodeFromMessage(in, configuration.getPath());
            this.version = getVersionFromMessage(in);
            this.payload = getPayloadFromExchange(exchange);
        }
    }

    private class AsyncSetDataCallback implements StatCallback {

        @Override
        public void processResult(int rc, String node, Object ctx, Stat statistics) {
            if (Code.NONODE.equals(Code.get(rc))) {
                if (configuration.isCreate()) {
                    LOG.warn("Node '{}' did not exist, creating it...", node);
                    ProductionContext context = (ProductionContext) ctx;
                    OperationResult<String> result = null;
                    try {
                        result = createNode(context);
                    } catch (Exception e) {
                        LOG.error("Error trying to create node '{}'", node, e);
                    }

                    if (result == null || !result.isOk()) {
                        LOG.error("Error creating node '{}'", node, result.getException());
                    }
                }
            } else {
                logStoreComplete(node, statistics);
            }
        }
    }

    private class AsyncDeleteCallback implements VoidCallback {
        @Override
        public void processResult(int rc, String path, Object ctx) {
            LOG.debug("Removed data node '{}'", path);
        }
    }

    private OperationResult<String> createNode(ProductionContext ctx) throws Exception {
        CreateOperation create = new CreateOperation(ctx.connection, ctx.node);
        create.setPermissions(getAclListFromMessage(ctx.exchange.getIn()));

        CreateMode mode = null;
        String modeString = configuration.getCreateMode();
        if (modeString != null) {
            try {
                mode = getCreateModeFromString(modeString, CreateMode.EPHEMERAL);
            } catch (Exception e) {
            }
        } else {
            mode = getCreateMode(ctx.exchange.getIn(), CreateMode.EPHEMERAL);
        }
        create.setCreateMode(mode == null ? CreateMode.EPHEMERAL : mode);
        create.setData(ctx.payload);
        return create.get();
    }

    /**
     * Tries to set the data first and if a no node error is received then an attempt will be made to create it instead.
     */
    private OperationResult synchronouslySetData(ProductionContext ctx) throws Exception {

        SetDataOperation setData = new SetDataOperation(ctx.connection, ctx.node, ctx.payload);
        setData.setVersion(ctx.version);

        OperationResult result = setData.get();

        if (!result.isOk() && configuration.isCreate() && result.failedDueTo(Code.NONODE)) {
            LOG.warn("Node '{}' did not exist, creating it.", ctx.node);
            result = createNode(ctx);
        }
        return result;
    }

    private OperationResult synchronouslyDelete(ProductionContext ctx) throws Exception {
        DeleteOperation setData = new DeleteOperation(ctx.connection, ctx.node);
        setData.setVersion(ctx.version);

        OperationResult result = setData.get();

        if (!result.isOk() && configuration.isCreate() && result.failedDueTo(Code.NONODE)) {
            LOG.warn("Node '{}' did not exist, creating it.", ctx.node);
            result = createNode(ctx);
        }
        return result;
    }

    private void logStoreComplete(String path, Stat statistics) {
        if (LOG.isDebugEnabled()) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Stored data to node '{}', and receive statistics {}", path, statistics);
            } else {
                LOG.debug("Stored data to node '{}'", path);
            }
        }
    }
}
