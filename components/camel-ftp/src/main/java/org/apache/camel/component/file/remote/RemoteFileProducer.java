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
package org.apache.camel.component.file.remote;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileProducer;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic remote file producer for all the FTP variations.
 */
public class RemoteFileProducer<T> extends GenericFileProducer<T> {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteFileProducer.class);
    private boolean loggedIn;

    private transient String remoteFileProducerToString;

    protected RemoteFileProducer(RemoteFileEndpoint<T> endpoint, RemoteFileOperations<T> operations) {
        super(endpoint, operations);
    }

    @Override
    public String getFileSeparator() {
        return "/";
    }

    @Override
    public String normalizePath(String name) {
        return name;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // store any existing file header which we want to keep and propagate
        final String existing = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);

        // create the target file name
        String target = createFileName(exchange);

        try {
            processExchange(exchange, target);
        } finally {
            // remove the write file name header as we only want to use it once
            // (by design)
            exchange.getIn().removeHeader(Exchange.OVERRULE_FILE_NAME);
            // and restore existing file name
            exchange.getIn().setHeader(Exchange.FILE_NAME, existing);
        }
    }

    protected RemoteFileOperations<T> getOperations() {
        return (RemoteFileOperations<T>)operations;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RemoteFileEndpoint<T> getEndpoint() {
        return (RemoteFileEndpoint<T>)super.getEndpoint();
    }

    /**
     * The file could not be written. We need to disconnect from the remote
     * server.
     */
    @Override
    public void handleFailedWrite(Exchange exchange, Exception exception) throws Exception {
        loggedIn = false;
        if (isStopping() || isStopped()) {
            // if we are stopping then ignore any exception during a poll
            LOG.debug("Exception occurred during stopping: {}", exception.getMessage());
        } else {
            LOG.warn("Writing file failed with: {}", exception.getMessage());
            try {
                disconnect();
            } catch (Exception e) {
                // ignore exception
                LOG.debug("Ignored exception during disconnect: {}", e.getMessage());
            }
            // rethrow the original exception*/
            throw exception;
        }
    }

    public void disconnect() throws GenericFileOperationFailedException {
        loggedIn = false;
        if (getOperations().isConnected()) {
            LOG.debug("Disconnecting from: {}", getEndpoint());
            getOperations().disconnect();
        }
    }

    @Override
    public void preWriteCheck(Exchange exchange) throws Exception {
        // before writing send a noop to see if the connection is alive and
        // works
        boolean noop = false;
        if (loggedIn) {
            if (getEndpoint().getConfiguration().isSendNoop()) {
                try {
                    noop = getOperations().sendNoop();
                } catch (Exception e) {
                    // ignore as we will try to recover connection
                    noop = false;
                    // mark as not logged in, since the noop failed
                    loggedIn = false;
                }
                LOG.trace("preWriteCheck send noop success: {}", noop);
            } else {
                // okay send noop is disabled then we would regard the op as
                // success
                noop = true;
                LOG.trace("preWriteCheck send noop disabled");
            }
        }

        // if not alive then reconnect
        if (!noop) {
            try {
                connectIfNecessary(exchange);
            } catch (Exception e) {
                loggedIn = false;

                // must be logged in to be able to upload the file
                throw e;
            }
        }
    }

    @Override
    public void postWriteCheck(Exchange exchange) {
        try {
            boolean isLast = exchange.getProperty(Exchange.BATCH_COMPLETE, false, Boolean.class);
            if (isLast && getEndpoint().isDisconnectOnBatchComplete()) {
                LOG.trace("postWriteCheck disconnect on batch complete from: {}", getEndpoint());
                disconnect();
            }
            if (getEndpoint().isDisconnect()) {
                LOG.trace("postWriteCheck disconnect from: {}", getEndpoint());
                disconnect();
            }
        } catch (GenericFileOperationFailedException e) {
            // ignore just log a warning
            LOG.warn("Exception occurred during disconnecting from: " + getEndpoint() + " " + e.getMessage());
        }
    }

    @Override
    protected void doStart() throws Exception {
        LOG.debug("Starting");
        // do not connect when component starts, just wait until we process as
        // we will
        // connect at that time if needed
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        try {
            disconnect();
        } catch (Exception e) {
            LOG.debug("Exception occurred during disconnecting from: " + getEndpoint() + " " + e.getMessage());
        }
        super.doStop();
    }

    protected void connectIfNecessary(Exchange exchange) throws GenericFileOperationFailedException {
        if (!loggedIn || !getOperations().isConnected()) {
            LOG.debug("Not already connected/logged in. Connecting to: {}", getEndpoint());
            RemoteFileConfiguration config = getEndpoint().getConfiguration();
            loggedIn = getOperations().connect(config, exchange);
            if (!loggedIn) {
                return;
            }
            LOG.debug("Connected and logged in to: {}", getEndpoint());
        }
    }

    @Override
    public boolean isSingleton() {
        // this producer is stateful because the remote file operations is not
        // thread safe
        return false;
    }

    @Override
    public String toString() {
        if (remoteFileProducerToString == null) {
            remoteFileProducerToString = "RemoteFileProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return remoteFileProducerToString;
    }
}
