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
import org.apache.camel.component.file.GenericFileExchange;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileProducer;
import org.apache.camel.util.ExchangeHelper;

/**
 * Remote file producer. Handles connecting and disconnecting if we are not.
 * Generic type F is the remote system implementation of a file.
 */
public class RemoteFileProducer<F> extends GenericFileProducer<F> {

    private boolean loggedIn;

    protected RemoteFileProducer(RemoteFileEndpoint<F> endpoint, RemoteFileOperations<F> operations) {
        super(endpoint, operations);
    }

    public void process(Exchange exchange) throws Exception {
        GenericFileExchange remoteExchange = (GenericFileExchange) getEndpoint().createExchange(exchange);
        processExchange(remoteExchange);
        ExchangeHelper.copyResults(exchange, remoteExchange);
    }

    /**
     * The file could not be written. We need to disconnect from the remote server.
     */
    protected void handleFailedWrite(GenericFileExchange exchange, Exception exception) throws Exception {
        loggedIn = false;
        if (isStopping() || isStopped()) {
            // if we are stopping then ignore any exception during a poll
            log.debug("Exception occured during stopping. " + exception.getMessage());
        } else {
            log.debug("Exception occured during processing.", exception);
            disconnect();
            // Rethrow to signify that we didn't poll
            throw exception;
        }
    }

    public void disconnect() throws IOException {
        loggedIn = false;
        if (log.isDebugEnabled()) {
            log.debug("Disconnecting from " + getEndpoint());
        }
        ((RemoteFileOperations) getOperations()).disconnect();
    }

    @Override
    protected void preWriteCheck() throws Exception {
        connectIfNecessary();
        if (!loggedIn) {
            // must be logged in to be able to upload the file
            String message = "Could not connect/login to: " + ((RemoteFileEndpoint) getEndpoint()).remoteServerInformation();
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
            log.debug("Exception occured during disconnecting from " + getEndpoint() + " " + e.getMessage());
        }
        super.doStop();
    }

    protected void connectIfNecessary() throws IOException {
        if (!((RemoteFileOperations) getOperations()).isConnected() || !loggedIn) {
            if (log.isDebugEnabled()) {
                log.debug("Not connected/logged in, connecting to " + getEndpoint());
            }
            RemoteFileOperations rfo = (RemoteFileOperations) getOperations();
            RemoteFileConfiguration conf = (RemoteFileConfiguration) getGenericFileEndpoint().getConfiguration();
            loggedIn = rfo.connect(conf);
            if (!loggedIn) {
                return;
            }
            log.info("Connected and logged in to " + getEndpoint());
        }
    }

}
