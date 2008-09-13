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

import java.io.InputStream;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import org.apache.camel.Exchange;

public class SftpProducer extends RemoteFileProducer<RemoteFileExchange> {
    private SftpEndpoint endpoint;
    private ChannelSftp channel;
    private Session session;

    public SftpProducer(SftpEndpoint endpoint, Session session) {
        super(endpoint);
        this.endpoint = endpoint;
        this.session = session;
    }

    public void process(Exchange exchange) throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("Processing " + endpoint.getConfiguration());
        }
        connectIfNecessary();
        // If the attempt to connect isn't successful, then the thrown
        // exception will signify that we couldn't deliver
        try {
            process(endpoint.createExchange(exchange));
        } catch (Exception e) {
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

    protected void connectIfNecessary() throws JSchException {
        if (channel == null || !channel.isConnected()) {
            if (session == null || !session.isConnected()) {
                log.trace("Session isn't connected, trying to recreate and connect.");
                session = endpoint.createSession();
                session.connect();
            }
            log.trace("Channel isn't connected, trying to recreate and connect.");
            channel = endpoint.createChannelSftp(session);
            channel.connect();
            log.info("Connected to " + endpoint.getConfiguration().remoteServerInformation());
        }
    }

    protected void disconnect() throws JSchException {
        if (log.isDebugEnabled()) {
            log.debug("Disconnecting from " + remoteServer());
        }
        if (session != null) {
            session.disconnect();
        }
        if (channel != null) {
            channel.disconnect();
        }
    }

    public void process(RemoteFileExchange exchange) throws Exception {
        InputStream payload = exchange.getIn().getBody(InputStream.class);
        try {
            String remoteServer = endpoint.getConfiguration().remoteServerInformation();
            String fileName = createFileName(exchange.getIn(), endpoint.getConfiguration());

            int lastPathIndex = fileName.lastIndexOf('/');
            if (lastPathIndex != -1) {
                String directory = fileName.substring(0, lastPathIndex);
                boolean success = SftpUtils.buildDirectory(channel, directory);
                if (!success) {
                    log.warn("Couldn't build directory: " + directory + " (could be because of denied permissions)");
                }
            }

            channel.put(payload, fileName);

            log.info("Sent: " + fileName + " to: " + remoteServer);
        } finally {
            if (payload != null) {
                payload.close();
            }
        }
    }

}
