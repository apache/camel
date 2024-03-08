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
package org.apache.camel.component.azure.storage.datalake;

import java.nio.file.OpenOption;
import java.time.Duration;
import java.util.Set;

import com.azure.core.credential.AzureSasCredential;
import com.azure.identity.ClientSecretCredential;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.file.datalake.DataLakeServiceClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

import static org.apache.camel.component.azure.storage.datalake.CredentialType.CLIENT_SECRET;

@UriParams
public class DataLakeConfiguration implements Cloneable {

    @UriPath(description = "name of the azure account")
    private String accountName;
    @UriPath(description = "name of filesystem to be used")
    private String fileSystemName;
    @UriParam(description = "shared key credential for azure data lake gen2")
    private StorageSharedKeyCredential sharedKeyCredential;
    @UriParam(description = "directory of the file to be handled in component")
    private String directoryName;
    @UriParam(description = "name of file to be handled in component")
    private String fileName;
    @UriParam(label = "security", secret = true, description = "client secret credential for authentication")
    private ClientSecretCredential clientSecretCredential;
    @UriParam(description = "data lake service client for azure storage data lake")
    @Metadata(autowired = true)
    private DataLakeServiceClient serviceClient;
    @UriParam(label = "security", secret = true, description = "account key for authentication")
    private String accountKey;
    @UriParam(description = "client id for azure account")
    private String clientId;
    @UriParam(label = "security", secret = true, description = "client secret for azure account")
    private String clientSecret;
    @UriParam(description = "tenant id for azure account")
    private String tenantId;
    @UriParam(description = "Timeout for operation")
    private Duration timeout;
    @UriParam(description = "path in azure data lake for operations")
    private String path = "/";
    @UriParam(description = "recursively include all paths")
    private Boolean recursive = false;
    @UriParam(description = "maximum number of results to show at a time")
    private Integer maxResults = 1000;
    @UriParam(description = "whether or not to use upn")
    private Boolean userPrincipalNameReturned = false;
    @UriParam(description = "regular expression for matching file names")
    private String regex;
    @UriParam(description = "directory of file to do operations in the local system")
    private String fileDir;
    @UriParam(description = "offset position in file for different operations")
    private Long fileOffset;
    @UriParam(description = "count number of bytes to download")
    private Long dataCount;
    @UriParam(description = "no of retries to a given request")
    private int maxRetryRequests;
    @UriParam(description = "check for closing stream after read")
    private Boolean closeStreamAfterRead = true;
    @UriParam(description = "download link expiration time")
    private Long downloadLinkExpiration;
    @UriParam(description = "Whether or not uncommitted data is to be retained after the operation")
    private Boolean retainUncommitedData = false;
    @UriParam(description = "Whether or not a file changed event raised indicates completion (true) or modification (false)")
    private Boolean close = false;
    @UriParam(description = "This parameter allows the caller to upload data in parallel and control the order in which it is appended to the file.")
    private Long position;
    @UriParam(description = "expression for queryInputStream")
    private String expression;
    @UriParam(description = "permission string for the file")
    private String permission;
    @UriParam(description = "umask permission for file")
    private String umask;
    @UriParam(description = "set open options for creating file")
    private Set<OpenOption> openOptions;
    @UriParam(label = "security", secret = true, description = "SAS token signature")
    private String sasSignature;
    @UriParam(label = "security", secret = true, description = "SAS token credential")
    private AzureSasCredential sasCredential;

    @UriParam(label = "producer", enums = "listFileSystem, listFiles", defaultValue = "listFileSystem",
              description = "operation to be performed")
    private DataLakeOperationsDefinition operation = DataLakeOperationsDefinition.listFileSystem;

    @UriParam(label = "common", enums = "CLIENT_SECRET,SHARED_KEY_CREDENTIAL,AZURE_IDENTITY,AZURE_SAS,SERVICE_CLIENT_INSTANCE",
              defaultValue = "CLIENT_SECRET")
    private CredentialType credentialType = CLIENT_SECRET;

    public DataLakeOperationsDefinition getOperation() {
        return operation;
    }

    public void setOperation(DataLakeOperationsDefinition operation) {
        this.operation = operation;
    }

    public String getUmask() {
        return umask;
    }

    public void setUmask(String umask) {
        this.umask = umask;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public Long getPosition() {
        return position;
    }

    public void setPosition(Long position) {
        this.position = position;
    }

    public Boolean getClose() {
        return close;
    }

    public void setClose(Boolean close) {
        this.close = close;
    }

    public Boolean getRetainUncommitedData() {
        return retainUncommitedData;
    }

    public void setRetainUncommitedData(Boolean retainUncommitedData) {
        this.retainUncommitedData = retainUncommitedData;
    }

    public Long getDownloadLinkExpiration() {
        return downloadLinkExpiration;
    }

    public void setDownloadLinkExpiration(Long downloadLinkExpiration) {
        this.downloadLinkExpiration = downloadLinkExpiration;
    }

    public Boolean getCloseStreamAfterRead() {
        return closeStreamAfterRead;
    }

    public void setCloseStreamAfterRead(Boolean closeStreamAfterRead) {
        this.closeStreamAfterRead = closeStreamAfterRead;
    }

    public int getMaxRetryRequests() {
        return maxRetryRequests;
    }

    public void setMaxRetryRequests(int maxRetryRequests) {
        this.maxRetryRequests = maxRetryRequests;
    }

    public Long getFileOffset() {
        return fileOffset;
    }

    public void setFileOffset(Long fileOffset) {
        this.fileOffset = fileOffset;
    }

    public Long getDataCount() {
        return dataCount;
    }

    public void setDataCount(Long dataCount) {
        this.dataCount = dataCount;
    }

    public String getFileDir() {
        return fileDir;
    }

    public void setFileDir(String fileDir) {
        this.fileDir = fileDir;
    }

    public String getRegex() {
        return regex;
    }

    public void setRegex(String regex) {
        this.regex = regex;
    }

    public Boolean getUserPrincipalNameReturned() {
        return userPrincipalNameReturned;
    }

    public void setUserPrincipalNameReturned(Boolean userPrincipalNameReturned) {
        this.userPrincipalNameReturned = userPrincipalNameReturned;
    }

    public Integer getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(Integer maxResults) {
        this.maxResults = maxResults;
    }

    public Boolean getRecursive() {
        return recursive;
    }

    public void setRecursive(Boolean recursive) {
        this.recursive = recursive;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getFileSystemName() {
        return fileSystemName;
    }

    public void setFileSystemName(String fileSystemName) {
        this.fileSystemName = fileSystemName;
    }

    public StorageSharedKeyCredential getSharedKeyCredential() {
        return sharedKeyCredential;
    }

    public void setSharedKeyCredential(StorageSharedKeyCredential sharedKeyCredential) {
        this.sharedKeyCredential = sharedKeyCredential;
    }

    public String getDirectoryName() {
        return directoryName;
    }

    public void setDirectoryName(String directoryName) {
        this.directoryName = directoryName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public ClientSecretCredential getClientSecretCredential() {
        return clientSecretCredential;
    }

    public void setClientSecretCredential(ClientSecretCredential clientSecretCredential) {
        this.clientSecretCredential = clientSecretCredential;
    }

    public DataLakeServiceClient getServiceClient() {
        return serviceClient;
    }

    public void setServiceClient(DataLakeServiceClient serviceClient) {
        this.serviceClient = serviceClient;
    }

    public String getAccountKey() {
        return accountKey;
    }

    public void setAccountKey(String accountKey) {
        this.accountKey = accountKey;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Set<OpenOption> getOpenOptions() {
        return openOptions;
    }

    public void setOpenOptions(Set<OpenOption> openOptions) {
        this.openOptions = openOptions;
    }

    public String getSasSignature() {
        return sasSignature;
    }

    public void setSasSignature(String sasSignature) {
        this.sasSignature = sasSignature;
    }

    public AzureSasCredential getSasCredential() {
        return sasCredential;
    }

    public void setSasCredential(AzureSasCredential sasCredential) {
        this.sasCredential = sasCredential;
    }

    public CredentialType getCredentialType() {
        return credentialType;
    }

    /**
     * Determines the credential strategy to adopt
     */
    public void setCredentialType(CredentialType credentialType) {
        this.credentialType = credentialType;
    }

    public DataLakeConfiguration copy() {
        try {
            return (DataLakeConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
