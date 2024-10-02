/*
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
package org.apache.camel.component.smb;

import java.io.IOException;

import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.camel.util.IOHelper;

public class SmbConsumer extends ScheduledPollConsumer {
    private final SmbEndpoint endpoint;
    private final SmbConfiguration configuration;

    private final SMBClient smbClient;
    private Connection connection;
    private Session session;
    private DiskShare share;

    public SmbConsumer(SmbEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.configuration = endpoint.getConfiguration();

        if (this.configuration.getSmbConfig() != null) {
            smbClient = new SMBClient(this.configuration.getSmbConfig());
        } else {
            smbClient = new SMBClient();
        }
    }

    private int pollDirectory(DiskShare share, SmbConfiguration configuration, int polledCount, String path) throws Exception {
        SmbIOBean smbIOBean = configuration.getSmbIoBean();
        String searchPattern = configuration.getSearchPattern();
        IdempotentRepository repository = configuration.getIdempotentRepository();

        path = (path == null) ? "" : path;

        for (FileIdBothDirectoryInformation f : share.list(path, searchPattern)) {
            if (f.getFileName().equals(".") || f.getFileName().equals("..")) {
                continue;
            }

            String fullFilePath = "";
            if (!"".equals(path)) {
                fullFilePath = new String(path + java.io.File.separator + f.getFileName());
            }

            if (share.folderExists(fullFilePath)) {
                if (configuration.isRecursive()) {
                    polledCount = pollDirectory(share, configuration, polledCount, new String(fullFilePath));
                }
                continue;
            }

            if (!repository.contains(fullFilePath)) {
                polledCount++;
                final Exchange exchange = createExchange(true);

                final File file = share.openFile(fullFilePath,
                        smbIOBean.accessMask(),
                        smbIOBean.attributes(),
                        smbIOBean.shareAccesses(),
                        smbIOBean.createDisposition(),
                        smbIOBean.createOptions());

                repository.add(fullFilePath);
                exchange.getMessage().setHeader(SmbConstants.SMB_FILE_PATH, file.getPath());
                exchange.getMessage().setHeader(SmbConstants.SMB_UNC_PATH, file.getUncPath());
                exchange.getMessage().setHeader(Exchange.FILE_NAME, file.getFileInformation().getNameInformation().toString());
                exchange.getMessage().setBody(file);
                try {
                    getProcessor().process(exchange);
                } catch (Exception e) {
                    exchange.setException(e);
                }
                if (exchange.getException() != null) {
                    Exception e = exchange.getException();
                    String msg = "Error processing file " + fullFilePath + " due to " + e.getMessage();
                    handleException(msg, exchange, e);
                }
            }
        }
        return polledCount;
    }

    @Override
    protected int poll() throws Exception {
        int polledCount = 0;

        polledCount = pollDirectory(share, configuration, polledCount, configuration.getPath());

        return polledCount;
    }

    private void refreshConnection() throws IOException {
        if (connection != null && connection.isConnected()) {
            return;
        }

        connection = smbClient.connect(endpoint.getHostname(), endpoint.getPort());

        // start a single threaded pool to monitor events
        AuthenticationContext ac = new AuthenticationContext(
                configuration.getUsername(), configuration.getPassword().toCharArray(),
                configuration.getDomain());
        session = connection.authenticate(ac);

        // Connect to the share
        share = (DiskShare) session.connectShare(endpoint.getShareName());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        refreshConnection();
    }

    @Override
    protected void doStop() throws Exception {
        if (connection != null) {
            IOHelper.close(connection);
        }

        super.doStop();
    }
}
