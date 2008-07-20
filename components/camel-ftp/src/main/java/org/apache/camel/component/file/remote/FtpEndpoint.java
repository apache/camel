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

import org.apache.camel.Processor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.net.ftp.FTPClient;

public class FtpEndpoint extends RemoteFileEndpoint<RemoteFileExchange> {
    private static final transient Log LOG = LogFactory.getLog(FtpEndpoint.class);

    public FtpEndpoint(String uri, RemoteFileComponent remoteFileComponent, RemoteFileConfiguration configuration) {
        super(uri, remoteFileComponent, configuration);
    }

    public FtpEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public FtpProducer createProducer() throws Exception {
        return new FtpProducer(this, createFtpClient());
    }

    public FtpConsumer createConsumer(Processor processor) throws Exception {
        final FtpConsumer consumer = new FtpConsumer(this, processor, createFtpClient());
        configureConsumer(consumer);
        return consumer;
    }

    protected FTPClient createFtpClient() {
        return new FTPClient();
    }

    public void connect(FTPClient client) throws IOException {
        // TODO: connect and disconnect. createFtpClient should be moved to another class they don't
        // belong on this endpoint class that is only for Camel related stuff 
        RemoteFileConfiguration config = getConfiguration();
        String host = config.getHost();
        int port = config.getPort();
        String username = config.getUsername();

        client.connect(host, port);
        if (username != null) {
            client.login(username, config.getPassword());
        } else {
            client.login("anonymous", null);
        }
        client.setFileType(config.isBinary() ? FTPClient.BINARY_FILE_TYPE : FTPClient.ASCII_FILE_TYPE);
    }

    public void disconnect(FTPClient client) throws IOException {
        client.disconnect();
    }
}
