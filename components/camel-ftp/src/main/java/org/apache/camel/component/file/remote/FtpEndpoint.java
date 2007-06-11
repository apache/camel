/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.file.remote;

import org.apache.camel.Processor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.net.ftp.FTPClient;

import java.io.IOException;

public class FtpEndpoint extends RemoteFileEndpoint<RemoteFileExchange> {
    private static final transient Log log = LogFactory.getLog(FtpEndpoint.class);

    public FtpEndpoint(String uri, RemoteFileComponent remoteFileComponent, RemoteFileConfiguration configuration) {
        super(uri, remoteFileComponent, configuration);
    }

    public FtpProducer createProducer() throws Exception {
        return new FtpProducer(this, createFtpClient());
    }

    public FtpConsumer createConsumer(Processor processor) throws Exception {
        final FtpConsumer consumer = new FtpConsumer(this, processor, createFtpClient());
        configureConsumer(consumer);
        return consumer;
    }

    protected FTPClient createFtpClient() throws IOException {
        final FTPClient client = new FTPClient();
        String host = getConfiguration().getHost();
        int port = getConfiguration().getPort();
        log.debug("Connecting to host: " + host + " port: " + port);

        client.connect(host, port);
        client.login(getConfiguration().getUsername(), getConfiguration().getPassword());
        client.setFileType(getConfiguration().isBinary() ? FTPClient.BINARY_FILE_TYPE : FTPClient.ASCII_FILE_TYPE);
        return client;
    }
}
