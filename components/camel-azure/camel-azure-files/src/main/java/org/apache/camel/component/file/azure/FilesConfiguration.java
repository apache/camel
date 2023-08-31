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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class FilesConfiguration extends RemoteFileConfiguration {

    public static final int DEFAULT_HTTPS_PORT = 443;
    public static final String DEFAULT_INTERNET_DOMAIN = "file.core.windows.net";

    @UriParam(label = "both", description = "Shared key (storage account key)", secret = true)
    private String sharedKey;

    @UriPath(name = "account", description = "The account to use")
    @Metadata(required = true)
    private String account;

    @UriPath(name = "share", description = "The share to use")
    @Metadata(required = true)
    private String share;

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
        // split URI path to share and starting directory
        if (path == null || path.isBlank() || path.contains(FilesPath.PATH_SEPARATOR + "" + FilesPath.PATH_SEPARATOR)
                || path.equals(FilesPath.SHARE_ROOT)) {
            throw new IllegalArgumentException("Illegal endpoint URI path (expected share[/dir]): " + path);
        }
        var dir = FilesPath.trimTrailingSeparator(path);
        dir = FilesPath.trimLeadingSeparator(dir);
        var separator = dir.indexOf(FilesPath.PATH_SEPARATOR);
        if (separator == -1) {
            share = dir;
            dir = FilesPath.SHARE_ROOT;
        } else {
            share = dir.substring(0, separator);
            dir = dir.substring(separator);
        }
        super.setDirectory(dir);
    }

    public String getShare() {
        return share;
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
        super.setHost(hasDot ? accountOrHostname : account + '.' + DEFAULT_INTERNET_DOMAIN);
    }

    public String getAccount() {
        return account;
    }

    public String getSharedKey() {
        return sharedKey;
    }

    public void setSharedKey(String sharedKey) {
        this.sharedKey = sharedKey;
    }

}
