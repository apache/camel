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
package org.apache.camel.component.file.remote;

import java.net.URI;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * FTP configuration
 */
@UriParams
public class FtpConfiguration extends RemoteFileConfiguration {

    public static final int DEFAULT_FTP_PORT = 21;

    @UriParam(label = "security", secret = true)
    private String account;
    @UriParam(label = "advanced")
    private String activePortRange;
    @UriParam(label = "producer,advanced")
    private String chmod;
    @UriParam(label = "consumer,advanced")
    private boolean handleDirectoryParserAbsoluteResult;

    public FtpConfiguration() {
        setProtocol("ftp");
    }

    public FtpConfiguration(URI uri) {
        super(uri);
    }

    @Override
    protected void setDefaultPort() {
        setPort(DEFAULT_FTP_PORT);
    }

    public String getAccount() {
        return account;
    }

    /**
     * Account to use for login
     */
    public void setAccount(String account) {
        this.account = account;
    }

    public String getActivePortRange() {
        return activePortRange;
    }

    /**
     * Set the client side port range in active mode. The syntax is:
     * minPort-maxPort Both port numbers are inclusive, eg 10000-19999 to
     * include all 1xxxx ports.
     */
    public void setActivePortRange(String activePortRange) {
        this.activePortRange = activePortRange;
    }

    /**
     * Allows you to set chmod on the stored file. For example chmod=640.
     */
    public void setChmod(String chmod) {
        this.chmod = chmod;
    }

    public String getChmod() {
        return chmod;
    }

    public boolean isHandleDirectoryParserAbsoluteResult() {
        return handleDirectoryParserAbsoluteResult;
    }

    /**
     * Allows you to set how the consumer will handle subfolders and files in
     * the path if the directory parser results in with absolute paths The
     * reason for this is that some FTP servers may return file names with
     * absolute paths, and if so then the FTP component needs to handle this by
     * converting the returned path into a relative path.
     */
    public void setHandleDirectoryParserAbsoluteResult(boolean handleDirectoryParserAbsoluteResult) {
        this.handleDirectoryParserAbsoluteResult = handleDirectoryParserAbsoluteResult;
    }
}
