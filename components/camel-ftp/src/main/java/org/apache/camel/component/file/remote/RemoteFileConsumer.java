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

import java.io.IOException;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.file.GenericFileConsumer;
import org.apache.camel.component.file.GenericFileOperationFailedException;

/**
 * Base class for remote file consumers.
 */
public abstract class RemoteFileConsumer<T> extends GenericFileConsumer<T> {
    protected boolean loggedIn;

    public RemoteFileConsumer(RemoteFileEndpoint<T> endpoint, Processor processor, RemoteFileOperations<T> operations) {
        super(endpoint, processor, operations);
        this.setPollStrategy(new RemoteFilePollingConsumerPollStrategy());
    }

    @Override
    @SuppressWarnings("unchecked")
    public RemoteFileEndpoint<T> getEndpoint() {
        return (RemoteFileEndpoint<T>) super.getEndpoint();
    }

    protected RemoteFileOperations<T> getOperations() {
        return (RemoteFileOperations<T>) operations;
    }

    protected boolean prePollCheck() throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("prePollCheck on " + getEndpoint().getConfiguration().remoteServerInformation());
        }
        try {
            if (getEndpoint().getMaximumReconnectAttempts() > 0) {
                // only use recoverable if we are allowed any re-connect attempts
                recoverableConnectIfNecessary();
            } else {
                connectIfNecessary();
            }
            getOperations().sendSiteCommands( getEndpoint().getConfiguration(), null );
        } catch (Exception e) {
            loggedIn = false;

            // login failed should we thrown exception
            if (getEndpoint().getConfiguration().isThrowExceptionOnConnectFailed()) {
                throw e;
            }
        }

        if (!loggedIn) {
            String message = "Cannot connect/login to: " + remoteServer() + ". Will skip this poll.";
            log.warn(message);
            return false;
        }

        return true;
    }

    @Override
    protected void postPollCheck() {
        if (log.isTraceEnabled()) {
            log.trace("postPollCheck on " + getEndpoint().getConfiguration().remoteServerInformation());
        }
        if (getEndpoint().isDisconnect()) {
            log.trace("postPollCheck disconnect from: {}", getEndpoint());
            disconnect();
        }
    }

    @Override
    protected void processExchange(Exchange exchange) {
        // mark the exchange to be processed synchronously as the ftp client is not thread safe
        // and we must execute the callbacks in the same thread as this consumer
        exchange.setProperty(Exchange.UNIT_OF_WORK_PROCESS_SYNC, Boolean.TRUE);
        super.processExchange(exchange);
    }
    
    @Override
    protected boolean isRetrieveFile() {
        return getEndpoint().isDownload();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        disconnect();
    }

    protected void disconnect() {
        // eager indicate we are no longer logged in
        loggedIn = false;

        // disconnect
        try {
            if (getOperations().isConnected()) {
                if (log.isDebugEnabled()) {
                    log.debug("Disconnecting from: {}", remoteServer());
                }
                getOperations().disconnect();
            }
        } catch (GenericFileOperationFailedException e) {
            // ignore just log a warning
            log.warn("Error occurred while disconnecting from " + remoteServer() + " due: " + e.getMessage() + ". This exception will be ignored.");
        }
    }

    protected void recoverableConnectIfNecessary() throws Exception {
        try {
            connectIfNecessary();
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Could not connect to: " + getEndpoint() + ". Will try to recover.", e);
            }
            loggedIn = false;
        }

        // recover by re-creating operations which should most likely be able to recover
        if (!loggedIn) {
            log.debug("Trying to recover connection to: {} with a fresh client.", getEndpoint());
            setOperations(getEndpoint().createRemoteFileOperations());
            connectIfNecessary();
        }
    }

    protected void connectIfNecessary() throws IOException {
        if (!loggedIn) {
            if (log.isDebugEnabled()) {
                log.debug("Not connected/logged in, connecting to: {}", remoteServer());
            }
            loggedIn = getOperations().connect((RemoteFileConfiguration) endpoint.getConfiguration());
            if (loggedIn) {
                log.info("Connected and logged in to: " + remoteServer());
            }
        }
    }

    /**
     * Returns human readable server information for logging purpose
     */
    protected String remoteServer() {
        return ((RemoteFileEndpoint<?>) endpoint).remoteServerInformation();
    }

}
