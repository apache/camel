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
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Ordered;
import org.apache.camel.Processor;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileConsumer;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.support.SynchronizationAdapter;

/**
 * Base class for remote file consumers.
 */
public abstract class RemoteFileConsumer<T> extends GenericFileConsumer<T> {
    protected transient boolean loggedIn;
    protected transient boolean loggedInWarning;

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
                log.warn(message);
                loggedInWarning = true;
            }
            return false;
        } else {
            // need to log the failed log again
            loggedInWarning = false;
        }

        return true;
    }

    @Override
    protected void postPollCheck(int polledMessages) {
        if (log.isTraceEnabled()) {
            log.trace("postPollCheck on " + getEndpoint().getConfiguration().remoteServerInformation());
        }

        // if we did not poll any messages, but are configured to disconnect then we need to do this now
        // as there is no exchanges to be routed that otherwise will disconnect from the last UoW
        if (polledMessages == 0) {
            if (getEndpoint().isDisconnect()) {
                log.trace("postPollCheck disconnect from: {}", getEndpoint());
                disconnect();
            }
        }
    }

    @Override
    protected boolean processExchange(Exchange exchange) {
        // mark the exchange to be processed synchronously as the ftp client is not thread safe
        // and we must execute the callbacks in the same thread as this consumer
        exchange.setProperty(Exchange.UNIT_OF_WORK_PROCESS_SYNC, Boolean.TRUE);

        // defer disconnect til the UoW is complete - but only the last exchange from the batch should do that
        boolean isLast = exchange.getProperty(Exchange.BATCH_COMPLETE, true, Boolean.class);
        if (isLast && getEndpoint().isDisconnect()) {
            exchange.addOnCompletion(new SynchronizationAdapter() {
                @Override
                public void onDone(Exchange exchange) {
                    log.trace("postPollCheck disconnect from: {}", getEndpoint());
                    disconnect();
                }

                @Override
                public boolean allowHandover() {
                    // do not allow handover as we must execute the callbacks in the same thread as this consumer
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

    protected void forceDisconnect() {
        // eager indicate we are no longer logged in
        loggedIn = false;

        // disconnect
        try {
            if (log.isDebugEnabled()) {
                log.debug("Force disconnecting from: {}", remoteServer());
            }
            getOperations().forceDisconnect();
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

    protected void connectIfNecessary() throws IOException {
        // We need to send a noop first to check if the connection is still open 
        boolean isConnected = false;
        try {
            isConnected = getOperations().sendNoop();
        } catch (Exception ex) {
            // here we just ignore the exception and try to reconnect
            if (log.isDebugEnabled()) {
                log.debug("Exception checking connection status: " + ex.getMessage());
            }
        }

        if (!loggedIn || !isConnected) {
            if (log.isDebugEnabled()) {
                log.debug("Not connected/logged in, connecting to: {}", remoteServer());
            }
            loggedIn = getOperations().connect((RemoteFileConfiguration) endpoint.getConfiguration());
            if (loggedIn) {
                log.debug("Connected and logged in to: " + remoteServer());
            }
        }
    }

    /**
     * Returns human readable server information for logging purpose
     */
    protected String remoteServer() {
        return ((RemoteFileEndpoint<?>) endpoint).remoteServerInformation();
    }

    /**
     * Executes doPollDirectory and on exception checks if it can be ignored by calling ignoreCannotRetrieveFile.
     *
     * @param absolutePath  the path of the directory to poll
     * @param dirName       the name of the directory to poll
     * @param fileList      current list of files gathered
     * @param depth         the current depth of the directory
     * @return whether or not to continue polling, <tt>false</tt> means the maxMessagesPerPoll limit has been hit
     * @throws GenericFileOperationFailedException if the exception during doPollDirectory can not be ignored
     */
    protected boolean doSafePollSubDirectory(String absolutePath, String dirName, List<GenericFile<T>> fileList, int depth) {
        try {
            log.trace("Polling sub directory: {} from: {}", absolutePath, endpoint);
            //Try to poll the directory
            return doPollDirectory(absolutePath, dirName, fileList, depth);
        } catch (Exception e) {
            log.debug("Caught exception {}", e.getMessage());
            if (ignoreCannotRetrieveFile(absolutePath, null, e)) {
                log.trace("Ignoring file error {} for {}", e.getMessage(), absolutePath);
                //indicate no files in this directory to poll, continue with fileList
                return true;
            } else {
                log.trace("Not ignoring file error {} for {}", e.getMessage(), absolutePath);
                if (e instanceof GenericFileOperationFailedException) {
                    throw (GenericFileOperationFailedException) e;
                } else {
                    throw new GenericFileOperationFailedException("Cannot poll sub-directory: " + absolutePath + " from: " + endpoint, e);
                }
            }
        }
    }

    /**
     * Poll directory given by dirName or absolutePath
     *
     * @param absolutePath The path of the directory to poll
     * @param dirName The name of the directory to poll
     * @param fileList current list of files gathered
     * @param depth the current depth of the directory
     * @return whether or not to continue polling, <tt>false</tt> means the maxMessagesPerPoll limit has been hit
     */
    protected abstract boolean doPollDirectory(String absolutePath, String dirName, List<GenericFile<T>> fileList, int depth);
}
