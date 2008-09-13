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
import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.commons.net.ftp.FTPClient;

public class FtpProducer extends RemoteFileProducer<RemoteFileExchange> {

    private FtpEndpoint endpoint;
    private FTPClient client;
    private boolean loggedIn;

    public FtpProducer(FtpEndpoint endpoint, FTPClient client) {
        super(endpoint);
        this.endpoint = endpoint;
        this.client = client;
    }

    public void process(Exchange exchange) throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("Processing " + endpoint.getConfiguration());
        }

        try {
            connectIfNecessary();

            if (!loggedIn) {
                String message = "Could not connect/login to " + endpoint.getConfiguration();
                log.warn(message);
                throw new FtpOperationFailedException(client.getReplyCode(), client.getReplyString(), message);
            }

            process(endpoint.createExchange(exchange));
        } catch (Exception e) {
            loggedIn = false;
            if (isStopping() || isStopped()) {
                // if we are stopping then ignore any exception during a poll
                log.warn("Producer is stopping. Ignoring caught exception: "
                         + e.getClass().getCanonicalName() + " message: " + e.getMessage());
            } else {
                log.warn("Exception occured during processing: "
                         + e.getClass().getCanonicalName() + " message: " + e.getMessage());
                disconnect();
                // Rethrow to signify that we didn't poll
                throw e;
            }
        }
    }

    protected void connectIfNecessary() throws IOException {
        if (!client.isConnected() || !loggedIn) {
            if (log.isDebugEnabled()) {
                log.debug("Not connected/logged in, connecting to " + remoteServer());
            }
            loggedIn = FtpUtils.connect(client, endpoint.getConfiguration());
            if (!loggedIn) {
                return;
            }
        }

        log.info("Connected and logged in to " + remoteServer());
    }

    public void disconnect() throws IOException {
        loggedIn = false;
        if (log.isDebugEnabled()) {
            log.debug("Disconnecting from " + remoteServer());
        }
        FtpUtils.disconnect(client);
    }

    public void process(RemoteFileExchange exchange) throws Exception {
        InputStream payload = exchange.getIn().getBody(InputStream.class);
        try {
            String fileName = createFileName(exchange.getIn(), endpoint.getConfiguration());

            int lastPathIndex = fileName.lastIndexOf('/');
            if (lastPathIndex != -1) {
                String directory = fileName.substring(0, lastPathIndex);
                if (!FtpUtils.buildDirectory(client, directory)) {
                    log.warn("Couldn't build directory: " + directory + " (could be because of denied permissions)");
                }
            }

            boolean success = client.storeFile(fileName, payload);
            if (!success) {
                String message = "Error sending file: " + fileName + " to: " + remoteServer();
                throw new FtpOperationFailedException(client.getReplyCode(), client.getReplyString(), message);
            }

            log.info("Sent: " + fileName + " to: " + remoteServer());
        } finally {
            if (payload != null) {
                payload.close();
            }
        }
    }

}
