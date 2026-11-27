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

import org.apache.camel.component.azure.common.CredentialType;
import org.apache.camel.component.file.remote.RemoteFileConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

import static org.apache.camel.component.azure.common.CredentialType.SHARED_ACCOUNT_KEY;

@UriParams
public class FilesConfiguration extends RemoteFileConfiguration {

    public static final int DEFAULT_HTTPS_PORT = 443;
    public static final String DEFAULT_INTERNET_DOMAIN = "file.core.windows.net";

    @UriParam(label = "common", security = "secret",
              description = "The access key of the storage account, for credentialType=SHARED_ACCOUNT_KEY. The component"
                            + " signs the requests with this key and the account option as the account name, so the endpoint"
                            + " URI must give the account name, not the host name. A + in the key can stay unencoded in the"
                            + " endpoint URI.")
    private String sharedKey;

    @UriPath(name = "account",
             description = "The name of the storage account, for example myaccount, or the host name of its file service,"
                           + " for example myaccount.file.core.windows.net. With a name alone, the component connects to"
                           + " that name with the .file.core.windows.net suffix, always over HTTPS.")
    @Metadata(required = true)
    private String account;

    @UriPath(name = "share",
             description = "The name of the file share in the storage account. A path after the share name in the endpoint"
                           + " URI is the directory, from the share root, that the consumer polls and that the producer"
                           + " writes to. Without it, the share root is used.")
    @Metadata(required = true)
    private String share;

    @UriParam(label = "common", enums = "SHARED_ACCOUNT_KEY,SHARED_KEY_CREDENTIAL,AZURE_IDENTITY,AZURE_SAS",
              defaultValue = "SHARED_ACCOUNT_KEY",
              description = "How the component authenticates: SHARED_ACCOUNT_KEY with the sharedKey option, AZURE_SAS with"
                            + " the SAS token options (sv, sig, se and the others), or AZURE_IDENTITY with the"
                            + " DefaultAzureCredential of the Azure Identity library and the backup intent, which needs the"
                            + " Storage File Data Privileged Reader or Contributor role. SHARED_KEY_CREDENTIAL is not"
                            + " supported: with it, or with SHARED_ACCOUNT_KEY and no sharedKey, the requests carry no"
                            + " credentials.")
    private CredentialType credentialType = SHARED_ACCOUNT_KEY;

    public FilesConfiguration() {
        setProtocol(FilesComponent.SCHEME);
    }

    public FilesConfiguration(URI uri) {
        super(uri);
        setSendNoop(false);
        setBinary(true);
        setPassiveMode(true);
        if (account == null) {
            // URI host maps to the account option
            String host = uri.getHost();
            if (host != null) {
                // reset host as it requires to know the account name also
                setAccount(host);
                setHost(host);
            }
        }
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

    public void setShare(String share) {
        this.share = share;
    }

    @Override
    public String remoteServerInformation() {
        return getProtocol() + "://" + getAccount();
    }

    /**
     * Files service account or &lt;account>.file.core.windows.net hostname.
     */
    @Override
    public void setHost(String host) {
        var dot = host.indexOf('.');
        var hasDot = dot >= 0;
        super.setHost(hasDot ? host : account + '.' + DEFAULT_INTERNET_DOMAIN);
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getSharedKey() {
        return sharedKey;
    }

    public void setSharedKey(String sharedKey) {
        this.sharedKey = sharedKey;
    }

    public CredentialType getCredentialType() {
        return credentialType;
    }

    public void setCredentialType(CredentialType credentialType) {
        this.credentialType = credentialType;
    }
}
