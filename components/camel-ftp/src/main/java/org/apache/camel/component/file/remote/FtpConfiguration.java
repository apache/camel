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

import java.net.URI;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * FTP configuration
 */
@UriParams
public class FtpConfiguration extends RemoteFileConfiguration {

    public static final int DEFAULT_FTP_PORT = 21;

    @UriParam(label = "security")
    private String account;

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
}
