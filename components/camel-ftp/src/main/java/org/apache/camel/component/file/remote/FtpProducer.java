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

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.commons.net.ftp.FTPClient;

import java.io.IOException;
import java.io.InputStream;

public class FtpProducer extends RemoteFileProducer<RemoteFileExchange> {
    FtpEndpoint endpoint;
    private final FTPClient client;

    public FtpProducer(FtpEndpoint endpoint, FTPClient client) {
        super(endpoint);
        this.endpoint = endpoint;
        this.client = client;
    }

    public void process(Exchange exchange) throws Exception {
        process(endpoint.toExchangeType(exchange));
    }

    public void process(RemoteFileExchange exchange) throws Exception {
        final String fileName;
        InputStream payload = exchange.getIn().getBody(InputStream.class);
        final String endpointFile = endpoint.getConfiguration().getFile();
        client.changeWorkingDirectory(endpointFile); // TODO this line might not be needed... check after finish writing unit tests
        if (endpointFile == null) {
            throw new NullPointerException("Null Endpoint File");
        }
        else {
            if (endpoint.getConfiguration().isDirectory()) {
                fileName = endpointFile + "/" + exchange.getIn().getMessageId();
            }
            else {
                fileName = endpointFile;
            }
        }
        buildDirectory(client, fileName.substring(0, fileName.lastIndexOf('/')));
        final boolean success = client.storeFile(fileName, payload);
        if (success) {

        }
        else {
            throw new RuntimeCamelException("error sending file");
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
//        client.connect(endpoint.getConfiguration().getHost());
//        client.login(endpoint.getConfiguration().getUsername(), endpoint.getConfiguration().getPassword());
//        client.setFileType(endpoint.getConfiguration().isBinary() ? FTPClient.BINARY_FILE_TYPE : FTPClient.ASCII_FILE_TYPE);
    }

    @Override
    protected void doStop() throws Exception {
        client.disconnect();
        super.doStop();
    }

    protected static boolean buildDirectory(FTPClient ftpClient, String dirName) throws IOException {
        boolean atLeastOneSuccess = false;
        final StringBuilder sb = new StringBuilder(dirName.length());
        final String[] dirs = dirName.split("\\/");
        for (String dir : dirs) {
            sb.append('/').append(dir);
            final boolean success = ftpClient.makeDirectory(sb.toString());
            System.out.println(sb.toString() + " = " + success);
            if (!atLeastOneSuccess && success) {
                atLeastOneSuccess = true;
            }
        }
        return atLeastOneSuccess;
    }
}
