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
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class FtpProducer extends RemoteFileProducer<RemoteFileExchange> {
    private static final transient Log LOG = LogFactory.getLog(FtpProducer.class);

    FtpEndpoint endpoint;
    private final FTPClient client;

    public FtpProducer(FtpEndpoint endpoint, FTPClient client) {
        super(endpoint);
        this.endpoint = endpoint;
        this.client = client;
    }

    // TODO: is there a way to avoid copy-pasting the reconnect logic?
    public void process(Exchange exchange) throws Exception {
        connectIfNecessary();
        // If the attempt to connect isn't successful, then the thrown 
        // exception will signify that we couldn't deliver
        try {
            process(endpoint.createExchange(exchange));
        } catch (FTPConnectionClosedException e) {
            // If the server disconnected us, then we must manually disconnect 
            // the client before attempting to reconnect
            LOG.warn("Disconnecting due to exception: " + e.toString());
            disconnect();
            // Rethrow to signify that we didn't deliver
            throw e;
        } catch (RuntimeCamelException e) {
            LOG.warn("Caught RuntimeCamelException: " + e.toString());
            LOG.warn("Hoping an explicit disconnect/reconnect will solve the problem");
            disconnect();
            // Rethrow to signify that we didn't deliver
            throw e;
        }
    }
    
    // TODO: is there a way to avoid copy-pasting the reconnect logic?
    protected void connectIfNecessary() throws IOException {
        if (!client.isConnected()) {
            LOG.warn("FtpProducer's client isn't connected, trying to reconnect...");
            endpoint.connect(client);
            LOG.info("Connected to " + endpoint.getConfiguration());
        }
    }
    
    // TODO: is there a way to avoid copy-pasting the reconnect logic?
    public void disconnect() throws IOException {
        LOG.info("FtpProducer's client is being explicitly disconnected");
        endpoint.disconnect(client);
    }

    public void process(RemoteFileExchange exchange) throws Exception {
        InputStream payload = exchange.getIn().getBody(InputStream.class);
        String fileName = createFileName(exchange.getIn(), endpoint.getConfiguration());
        
        int lastPathIndex = fileName.lastIndexOf('/');
        if (lastPathIndex != -1)
        {
            String directory = fileName.substring(0, lastPathIndex);
            if (!buildDirectory(client, directory)) {
                LOG.warn("Couldn't buildDirectory: " + directory + " (either permissions deny it, or it already exists)");
            }
        }
        
        final boolean success = client.storeFile(fileName, payload);
        if (!success) {
            throw new RuntimeCamelException("error sending file");
        }
        
        RemoteFileConfiguration config = endpoint.getConfiguration();
        LOG.info("Sent: " + fileName + " to " + config.toString().substring(0, config.toString().indexOf(config.getFile())));
    }

    @Override
    protected void doStart() throws Exception {
        LOG.info("Starting");
        try {
            connectIfNecessary();
        } catch (IOException e) {
            LOG.warn("Couldn't connect to " + endpoint.getConfiguration());
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        LOG.info("Stopping");
        disconnect();
        super.doStop();
    }

    protected static boolean buildDirectory(FTPClient ftpClient, String dirName) throws IOException {
        boolean atLeastOneSuccess = false;
        final StringBuilder sb = new StringBuilder(dirName.length());
        final String[] dirs = dirName.split("\\/");
        for (String dir : dirs) {
            sb.append(dir).append('/');
            final boolean success = ftpClient.makeDirectory(sb.toString());
            if (!atLeastOneSuccess && success) {
                atLeastOneSuccess = true;
            }
        }
        return atLeastOneSuccess;
    }
}
