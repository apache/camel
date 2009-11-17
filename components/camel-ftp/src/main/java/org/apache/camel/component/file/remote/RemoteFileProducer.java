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
import org.apache.camel.ServicePoolAware;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileProducer;
import org.apache.camel.util.ExchangeHelper;

/**
 * Remote file producer. Handles connecting and disconnecting if we are not.
 * Generic type F is the remote system implementation of a file.
 */
public class RemoteFileProducer<T> extends GenericFileProducer<T> implements ServicePoolAware {

    private boolean loggedIn;
    
    protected RemoteFileProducer(RemoteFileEndpoint<T> endpoint, RemoteFileOperations<T> operations) {
        super(endpoint, operations);
    }
    
    @Override
    protected String getFileSeparator() {
        return "/";
    }
    
    @Override
    protected String normalizePath(String name) {        
        return name;
    }

    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) throws Exception {
        Exchange remoteExchange = getEndpoint().createExchange(exchange);
        processExchange(remoteExchange);
        ExchangeHelper.copyResults(exchange, remoteExchange);
    }

    protected RemoteFileOperations getOperations() {
        return (RemoteFileOperations) operations;
    }

    /**
     * The file could not be written. We need to disconnect from the remote server.
     */
    protected void handleFailedWrite(Exchange exchange, Exception exception) throws Exception {
        loggedIn = false;
        if (isStopping() || isStopped()) {
            // if we are stopping then ignore any exception during a poll
            log.debug("Exception occurred during stopping: " + exception.getMessage());
        } else {
            log.debug("Exception occurred during processing. ", exception);
            try {
                disconnect();
            } catch (Exception e) {
                // ignore exception
                log.debug("Ignored exception during disconnect", e);
            }
            // Rethrow the original exception
            throw exception;
        }
    }

    public void disconnect() throws IOException {
        loggedIn = false;
        if (getOperations().isConnected()) {
            if (log.isDebugEnabled()) {
                log.debug("Disconnecting from: " + getEndpoint());
            }
            getOperations().disconnect();
        }
    }

    @Override
    protected void preWriteCheck() throws Exception {
        // before writing send a noop to see if the connection is alive and works
        boolean noop = false;
        try {
            connectIfNecessary();
            if (loggedIn) {
                noop = getOperations().sendNoop();
            }
        } catch (Exception e) {
            log.error(e);
            noop = false;
        }
        if (log.isDebugEnabled()) {
            log.debug("preWriteCheck send noop success: " + noop);
        }

        // if not alive then force a disconnect so we reconnect again
        if (!noop) {
            try {
                if (log.isDebugEnabled()) {
                    log.debug("preWriteCheck forcing a disconnect as noop failed");
                }
                disconnect();
            } catch (Exception e) {
                // ignore for now as we will reconnect below
            }
        }

        connectIfNecessary();
        if (!loggedIn) {
            // must be logged in to be able to upload the file
            String message = "Cannot connect/login to: " + ((RemoteFileEndpoint) getEndpoint()).remoteServerInformation();
            throw new GenericFileOperationFailedException(message);
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

    protected void connectIfNecessary() throws IOException {
        if (!loggedIn) {
            if (log.isDebugEnabled()) {
                log.debug("Not already connected/logged in. Connecting to: " + getEndpoint());
            }
            RemoteFileEndpoint rfe = (RemoteFileEndpoint) getEndpoint();
            RemoteFileConfiguration conf = (RemoteFileConfiguration) rfe.getConfiguration();
            loggedIn = getOperations().connect(conf);
            if (!loggedIn) {
                return;
            }
            log.info("Connected and logged in to: " + getEndpoint());
        }
    }

    public boolean isSingleton() {
        // this producer is stateful because the remote file operations is not thread safe
        return false;
    }
}
