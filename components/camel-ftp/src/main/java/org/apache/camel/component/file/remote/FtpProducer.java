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
import org.apache.camel.RuntimeCamelException;
import org.apache.commons.net.ftp.FTPClient;

public class FtpProducer extends RemoteFileProducer<RemoteFileExchange> {

    private FtpEndpoint endpoint;
    private FTPClient client;

    public FtpProducer(FtpEndpoint endpoint, FTPClient client) {
        super(endpoint);
        this.endpoint = endpoint;
        this.client = client;
    }

    public void process(Exchange exchange) throws Exception {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Processing " + endpoint.getConfiguration());
        }
        connectIfNecessary();
        // If the attempt to connect isn't successful, then the thrown
        // exception will signify that we couldn't deliver
        try {
            process(endpoint.createExchange(exchange));
        } catch (Exception e) {
            if (isStopping() || isStopped()) {
                // if we are stopping then ignore any exception during a poll
                LOG.warn("Producer is stopping. Ignoring caught exception: "
                         + e.getClass().getCanonicalName() + " message: " + e.getMessage());
            } else {
                LOG.warn("Exception occured during processing: "
                         + e.getClass().getCanonicalName() + " message: " + e.getMessage());
                disconnect();
                // Rethrow to signify that we didn't poll
                throw e;
            }
        }
    }

    protected void connectIfNecessary() throws IOException {
        if (!client.isConnected()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Not connected, connecting to " + remoteServer());
            }
            FtpUtils.connect(client, endpoint.getConfiguration());
            LOG.info("Connected to " + remoteServer());
        }
    }

    public void disconnect() throws IOException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Disconnecting from " + remoteServer());
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
                    LOG.warn("Couldn't build directory: " + directory + " (could be because of denied permissions)");
                }
            }

            boolean success = client.storeFile(fileName, payload);
            if (!success) {
                throw new RuntimeCamelException("Error sending file: " + fileName + " to: " + remoteServer());
            }

            LOG.info("Sent: " + fileName + " to: " + remoteServer());
        } finally {
            if (payload != null) {
                payload.close();
            }
        }
    }

}
