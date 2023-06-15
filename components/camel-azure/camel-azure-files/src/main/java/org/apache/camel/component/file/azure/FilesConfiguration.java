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
package org.apache.camel.component.file.azure;

import java.net.URI;

import org.apache.camel.component.file.remote.RemoteFileConfiguration;
import org.apache.camel.spi.UriParams;

@UriParams
public class FilesConfiguration extends RemoteFileConfiguration {

    public static final int DEFAULT_HTTPS_PORT = 443;
    public static final String DEFAULT_INTERNET_DOMAIN = "file.core.windows.net";

    private String account;

    public FilesConfiguration() {
        setProtocol(FilesComponent.SCHEME);
    }

    public FilesConfiguration(URI uri) {
        super(uri);
        setSendNoop(false);
        setBinary(true);
        setPassiveMode(true);
    }

    @Override
    protected void setDefaultPort() {
        setPort(DEFAULT_HTTPS_PORT);
    }

    @Override
    public void setDirectory(String path) {
        // strip share from endpoint path
        var dir = "";
        var separator = path.indexOf(FilesPath.PATH_SEPARATOR);
        if (separator > 1) {
            dir = path.substring(separator);
        }
        super.setDirectory(dir);
    }

    @Override
    public String remoteServerInformation() {
        return getProtocol() + "://" + getAccount();
    }

    /**
     * Files service account or &lt;account>.file.core.windows.net hostname.
     */
    @Override
    public void setHost(String accountOrHostname) {
        var dot = accountOrHostname.indexOf('.');
        var hasDot = dot >= 0;
        account = hasDot ? accountOrHostname.substring(0, dot) : accountOrHostname;
        super.setHost(hasDot ? accountOrHostname : account + '.' + DEFAULT_INTERNET_DOMAIN);
    }

    public String getAccount() {
        return account;
    }
}
