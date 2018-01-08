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
package org.apache.camel.component.file.remote;

import org.apache.camel.Exchange;
import org.apache.camel.ServicePoolAware;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

/**
 * Generic remote file producer for all the FTP variations.
 */
public class RemoteFileProducer<T> extends GenericFileProducer<T> implements ServicePoolAware {

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
            // remove the write file name header as we only want to use it once (by design)
            exchange.getIn().removeHeader(Exchange.OVERRULE_FILE_NAME);
            // and restore existing file name
            exchange.getIn().setHeader(Exchange.FILE_NAME, existing);
        }
    }

    protected RemoteFileOperations<T> getOperations() {
        return (RemoteFileOperations<T>) operations;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RemoteFileEndpoint<T> getEndpoint() {
        return (RemoteFileEndpoint<T>) super.getEndpoint();
    }

    /**
     * The file could not be written. We need to disconnect from the remote server.
     */
    public void handleFailedWrite(Exchange exchange, Exception exception) throws Exception {
        loggedIn = false;
        if (isStopping() || isStopped()) {
            // if we are stopping then ignore any exception during a poll
            log.debug("Exception occurred during stopping: " + exception.getMessage());
        } else {
            log.warn("Writing file failed with: " + exception.getMessage());
            try {
                disconnect();
            } catch (Exception e) {
                // ignore exception
                log.debug("Ignored exception during disconnect: " + e.getMessage());
            }
            // rethrow the original exception*/
            throw exception;
        }
    }

    public void disconnect() throws GenericFileOperationFailedException {
        loggedIn = false;
        if (getOperations().isConnected()) {
            log.debug("Disconnecting from: {}", getEndpoint());
            getOperations().disconnect();
        }
    }

    @Override
    public void preWriteCheck() throws Exception {
        // before writing send a noop to see if the connection is alive and works
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
                log.trace("preWriteCheck send noop success: {}", noop);
            } else {
                // okay send noop is disabled then we would regard the op as success
                noop = true;
                log.trace("preWriteCheck send noop disabled");
            }
        }

        // if not alive then reconnect
        if (!noop) {
            try {
                if (getEndpoint().getMaximumReconnectAttempts() > 0) {
                    // only use recoverable if we are allowed any re-connect attempts
                    recoverableConnectIfNecessary();
                } else {
                    connectIfNecessary();
                }
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
                log.trace("postWriteCheck disconnect on batch complete from: {}", getEndpoint());
                disconnect();
            }
            if (getEndpoint().isDisconnect()) {
                log.trace("postWriteCheck disconnect from: {}", getEndpoint());
                disconnect();
            }
        } catch (GenericFileOperationFailedException e) {
            // ignore just log a warning
            log.warn("Exception occurred during disconnecting from: " + getEndpoint() + " " + e.getMessage());
        }
    }

    @Override
    protected void doStart() throws Exception {
        log.debug("Starting");
        // do not connect when component starts, just wait until we process as we will
        // connect at that time if needed
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        try {
            disconnect();
        } catch (Exception e) {
            log.debug("Exception occurred during disconnecting from: " + getEndpoint() + " " + e.getMessage());
        }
        super.doStop();
    }

    protected void recoverableConnectIfNecessary() throws Exception {
        try {
            connectIfNecessary();
        } catch (Exception e) {
            loggedIn = false;

            // are we interrupted
            InterruptedException ie = ObjectHelper.getException(InterruptedException.class, e);
            if (ie != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Interrupted during connect to: " + getEndpoint(), ie);
                }
                throw ie;
            }

            if (log.isDebugEnabled()) {
                log.debug("Could not connect to: " + getEndpoint() + ". Will try to recover.", e);
            }
        }

        // recover by re-creating operations which should most likely be able to recover
        if (!loggedIn) {
            log.debug("Trying to recover connection to: {} with a new FTP client.", getEndpoint());
            // we want to preserve last FTP activity listener when we set a new operations
            if (operations instanceof FtpOperations) {
                FtpOperations ftpOperations = (FtpOperations) operations;
                FtpClientActivityListener listener = ftpOperations.getClientActivityListener();
                setOperations(getEndpoint().createRemoteFileOperations());
                getOperations().setEndpoint(getEndpoint());
                if (listener != null) {
                    ftpOperations = (FtpOperations) getOperations();
                    ftpOperations.setClientActivityListener(listener);
                }
            } else {
                setOperations(getEndpoint().createRemoteFileOperations());
                getOperations().setEndpoint(getEndpoint());
            }
            connectIfNecessary();
        }
    }

    protected void connectIfNecessary() throws GenericFileOperationFailedException {
        if (!loggedIn || !getOperations().isConnected()) {
            log.debug("Not already connected/logged in. Connecting to: {}", getEndpoint());
            RemoteFileConfiguration config = getEndpoint().getConfiguration();
            loggedIn = getOperations().connect(config);
            if (!loggedIn) {
                return;
            }
            log.debug("Connected and logged in to: " + getEndpoint());
        }
    }

    public boolean isSingleton() {
        // this producer is stateful because the remote file operations is not thread safe
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
