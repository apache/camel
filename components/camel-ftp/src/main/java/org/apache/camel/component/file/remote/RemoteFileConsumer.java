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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Ordered;
import org.apache.camel.Processor;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileConsumer;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for remote file consumers.
 */
public abstract class RemoteFileConsumer<T> extends GenericFileConsumer<T> {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteFileConsumer.class);

    protected transient boolean loggedIn;
    protected transient boolean loggedInWarning;

    protected RemoteFileConsumer(RemoteFileEndpoint<T> endpoint, Processor processor, RemoteFileOperations<T> operations,
                                 GenericFileProcessStrategy processStrategy) {
        super(endpoint, processor, operations, processStrategy);
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

    @Override
    protected Exchange createExchange(GenericFile<T> file) {
        Exchange answer = createExchange(true);
        if (file != null) {
            file.bindToExchange(answer);
        }
        return answer;
    }

    @Override
    protected boolean prePollCheck() throws Exception {
        if (LOG.isTraceEnabled()) {
            LOG.trace("prePollCheck on {}", getEndpoint().getConfiguration().remoteServerInformation());
        }
        try {
            connectIfNecessary();
        } catch (Exception e) {
            loggedIn = false;

            // login failed should we thrown exception
            if (getEndpoint().getConfiguration().isThrowExceptionOnConnectFailed()) {
                throw e;
            }
        }

        if (!loggedIn) {
            String message = "Cannot connect/login to: " + remoteServer() + ". Will skip this poll.";
            if (!loggedInWarning) {
                LOG.warn(message);
                loggedInWarning = true;
            }
            return false;
        } else {
            // need to log the failed log again
            loggedInWarning = false;
        }

        // we are logged in so lets mark the consumer as ready
        forceConsumerAsReady();

        return true;
    }

    @Override
    protected void postPollCheck(int polledMessages) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("postPollCheck on {}", getEndpoint().getConfiguration().remoteServerInformation());
        }

        // if we did not poll any messages, but are configured to disconnect
        // then we need to do this now
        // as there is no exchanges to be routed that otherwise will disconnect
        // from the last UoW
        if (polledMessages == 0) {
            if (getEndpoint().isDisconnect()) {
                LOG.trace("postPollCheck disconnect from: {}", getEndpoint());
                disconnect();
            }
        }
    }

    @Override
    protected boolean processExchange(Exchange exchange) {
        // defer disconnect til the UoW is complete - but only the last exchange
        // from the batch should do that
        boolean isLast = exchange.getProperty(ExchangePropertyKey.BATCH_COMPLETE, true, Boolean.class);
        if (isLast && getEndpoint().isDisconnect()) {
            exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                @Override
                public void onDone(Exchange exchange) {
                    LOG.trace("processExchange disconnect from: {}", getEndpoint());
                    disconnect();
                }

                @Override
                public boolean allowHandover() {
                    // do not allow handover as we must execute the callbacks in
                    // the same thread as this consumer
                    return false;
                }

                @Override
                public int getOrder() {
                    // we want to disconnect last
                    return Ordered.LOWEST;
                }

                public String toString() {
                    return "Disconnect";
                }
            });
        }

        return super.processExchange(exchange);
    }

    @Override
    protected boolean isRetrieveFile() {
        return getEndpoint().isDownload();
    }

    /**
     * Whether there is a starting directory configured.
     */
    protected boolean hasStartingDirectory() {
        String dir = endpoint.getConfiguration().getDirectory();
        if (ObjectHelper.isEmpty(dir)) {
            return false;
        }
        // should not be a empty separator
        return !dir.equals("/") && !dir.equals("\\");
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
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Disconnecting from: {}", remoteServer());
                }
                getOperations().disconnect();
            }
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Error occurred while disconnecting from {} due: {} This exception will be ignored.",
                        remoteServer(), e.getMessage(), e);
            }
        }
    }

    protected void forceDisconnect() {
        // eager indicate we are no longer logged in
        loggedIn = false;

        // disconnect
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Force disconnecting from: {}", remoteServer());
            }
            getOperations().forceDisconnect();
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Error occurred while disconnecting from {} due: {} This exception will be ignored.",
                        remoteServer(), e.getMessage(), e);
            }
        }
    }

    protected void connectIfNecessary() {
        // We need to send a noop first to check if the connection is still open
        boolean isConnected = false;
        try {
            isConnected = getOperations().sendNoop();
        } catch (Exception ex) {
            // here we just ignore the exception and try to reconnect
            if (LOG.isDebugEnabled()) {
                LOG.debug("Exception checking connection status: {}", ex.getMessage());
            }
        }

        if (!loggedIn || !isConnected) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Not connected/logged in, connecting to: {}", remoteServer());
            }
            loggedIn = getOperations().connect((RemoteFileConfiguration) endpoint.getConfiguration(), null);
            if (loggedIn && LOG.isDebugEnabled()) {
                LOG.debug("Connected and logged in to: {}", remoteServer());
            }
        }
    }

    /**
     * Returns human-readable server information for logging purpose
     */
    protected String remoteServer() {
        return ((RemoteFileEndpoint<?>) endpoint).remoteServerInformation();
    }

    /**
     * Executes doPollDirectory and on exception checks if it can be ignored by calling ignoreCannotRetrieveFile.
     *
     * @param  absolutePath                        the path of the directory to poll
     * @param  dirName                             the name of the directory to poll
     * @param  fileList                            current list of files gathered
     * @param  depth                               the current depth of the directory
     * @return                                     whether or not to continue polling, <tt>false</tt> means the
     *                                             maxMessagesPerPoll limit has been hit
     * @throws GenericFileOperationFailedException if the exception during doPollDirectory can not be ignored
     */
    protected boolean doSafePollSubDirectory(String absolutePath, String dirName, List<GenericFile<T>> fileList, int depth) {
        try {
            LOG.trace("Polling sub directory: {} from: {}", absolutePath, endpoint);
            // Try to poll the directory
            return doPollDirectory(absolutePath, dirName, fileList, depth);
        } catch (Exception e) {
            LOG.debug("Caught exception {}", e.getMessage());
            if (ignoreCannotRetrieveFile(absolutePath, null, e)) {
                LOG.trace("Ignoring file error {} for {}", e.getMessage(), absolutePath);
                // indicate no files in this directory to poll, continue with
                // fileList
                return true;
            } else {
                LOG.trace("Not ignoring file error {} for {}", e.getMessage(), absolutePath);
                if (e instanceof GenericFileOperationFailedException) {
                    throw (GenericFileOperationFailedException) e;
                } else {
                    throw new GenericFileOperationFailedException(
                            "Cannot poll sub-directory: " + absolutePath + " from: " + endpoint, e);
                }
            }
        }
    }

    /**
     * Poll directory given by dirName or absolutePath
     *
     * @param  absolutePath The path of the directory to poll
     * @param  dirName      The name of the directory to poll
     * @param  fileList     current list of files gathered
     * @param  depth        the current depth of the directory
     * @return              whether or not to continue polling, <tt>false</tt> means the maxMessagesPerPoll limit has
     *                      been hit
     */
    protected abstract boolean doPollDirectory(String absolutePath, String dirName, List<GenericFile<T>> fileList, int depth);
}
