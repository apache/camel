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
package org.apache.camel.component.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.SummaryEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import org.apache.camel.component.fhir.internal.FhirApiName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;

/**
 * Component configuration for FHIR component.
 */
@UriParams
public class FhirConfiguration {

    @UriPath(enums = "capabilities,create,delete,history,load-page,meta,patch,read,search,transaction,update,validate")
    @Metadata(required = true)
    private FhirApiName apiName;
    @UriPath
    @Metadata(required = true)
    private String methodName;

    @UriParam(description = "The FHIR server base URL")
    private String serverUrl;

    @UriParam(description = "The FHIR Version to use", defaultValue = "R4", javaType = "java.lang.String")
    private FhirVersionEnum fhirVersion = FhirVersionEnum.R4;

    @UriParam(description = "Pretty print all request")
    private boolean prettyPrint;

    @UriParam(description = "Encoding to use for all request", enums = "JSON, XML", javaType = "java.lang.String")
    private EncodingEnum encoding;

    @UriParam(description = "Username to use for basic authentication", label = "security", secret = true)
    private String username;

    @UriParam(description = "Username to use for basic authentication", label = "security", secret = true)
    private String password;

    @UriParam(description = "OAuth access token", label = "security", secret = true)
    private String accessToken;

    @UriParam(description = "Will log every requests and responses")
    private boolean log;

    @UriParam(description = "Compresses outgoing (POST/PUT) contents to the GZIP format", label = "advanced")
    private boolean compress;

    @UriParam(description = "Request that the server modify the response using the <code>_summary</code> param",
            label = "advanced", javaType = "java.lang.String")
    private SummaryEnum summary;

    @UriParam(description = "HTTP session cookie to add to every request", label = "advanced")
    private String sessionCookie;

    @UriParam(description = "FhirContext is an expensive object to create. To avoid creating multiple instances,"
            + " it can be set directly.", label = "advanced")
    private FhirContext fhirContext;

    @UriParam(description = "Force conformance check", label = "advanced")
    private boolean forceConformanceCheck;

    @UriParam(description = "When should Camel validate the FHIR Server's conformance statement",
            defaultValue = "ONCE", label = "advanced", javaType = "java.lang.String")
    private ServerValidationModeEnum validationMode;

    @UriParam(description = "When this option is set, model classes will not be scanned for children until the"
            + " child list for the given type is actually accessed.", defaultValue = "false", label = "advanced")
    private boolean deferModelScanning;

    @UriParam(description = "How long to try and establish the initial TCP connection (in ms)", label = "advanced", defaultValue = "10000")
    private Integer connectionTimeout;

    @UriParam(description = "How long to block for individual read/write operations (in ms)", label = "advanced", defaultValue = "10000")
    private Integer socketTimeout;

    @UriParam(label = "proxy", description = "The proxy host")
    private String proxyHost;

    @UriParam(label = "proxy", description = "The proxy port")
    private Integer proxyPort;

    @UriParam(label = "proxy", description = "The proxy username")
    private String proxyUser;

    @UriParam(label = "proxy", description = "The proxy password")
    private String proxyPassword;

    @UriParam(label = "advanced", description = "To use the custom client")
    private IGenericClient client;

    @UriParam(label = "advanced", description = "To use the custom client factory")
    private IRestfulClientFactory clientFactory;

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public FhirVersionEnum getFhirVersion() {
        return fhirVersion;
    }

    public void setFhirVersion(String fhirVersion) {
        this.fhirVersion = FhirVersionEnum.valueOf(fhirVersion);
    }

    public EncodingEnum getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = EncodingEnum.valueOf(encoding);
    }


    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public SummaryEnum getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = SummaryEnum.valueOf(summary);
    }

    public FhirApiName getApiName() {
        return apiName;
    }

    /**
     * What kind of operation to perform
     */
    public void setApiName(FhirApiName apiName) {
        this.apiName = apiName;
    }

    public String getMethodName() {
        return methodName;
    }

    /**
     * What sub operation to use for the selected operation
     */
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public FhirContext getFhirContext() {
        return fhirContext;
    }

    public void setFhirContext(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
    }

    public boolean isForceConformanceCheck() {
        return forceConformanceCheck;
    }

    public void setForceConformanceCheck(boolean forceConformanceCheck) {
        this.forceConformanceCheck = forceConformanceCheck;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public boolean isLog() {
        return log;
    }

    public void setLog(boolean log) {
        this.log = log;
    }

    public boolean isCompress() {
        return compress;
    }

    public void setCompress(boolean compress) {
        this.compress = compress;
    }

    public String getSessionCookie() {
        return sessionCookie;
    }

    public void setSessionCookie(String sessionCookie) {
        this.sessionCookie = sessionCookie;
    }

    public ServerValidationModeEnum getValidationMode() {
        return validationMode;
    }

    public void setValidationMode(String validationMode) {
        this.validationMode = ServerValidationModeEnum.valueOf(validationMode);
    }

    public boolean isDeferModelScanning() {
        return deferModelScanning;
    }

    public void setDeferModelScanning(boolean deferModelScanning) {
        this.deferModelScanning = deferModelScanning;
    }

    public Integer getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Integer connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public Integer getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(Integer socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getProxyUser() {
        return proxyUser;
    }

    public void setProxyUser(String proxyUser) {
        this.proxyUser = proxyUser;
    }

    public IGenericClient getClient() {
        return client;
    }

    public void setClient(IGenericClient client) {
        this.client = client;
    }

    public IRestfulClientFactory getClientFactory() {
        return clientFactory;
    }

    public void setClientFactory(IRestfulClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof FhirConfiguration) {
            FhirConfiguration otherFhirConfiguration = (FhirConfiguration) other;
            return ObjectHelper.equal(prettyPrint, otherFhirConfiguration.isPrettyPrint())
                    && ObjectHelper.equal(log, otherFhirConfiguration.isLog())
                    && ObjectHelper.equal(compress, otherFhirConfiguration.isCompress())
                    && ObjectHelper.equal(forceConformanceCheck, otherFhirConfiguration.isForceConformanceCheck())
                    && ObjectHelper.equal(fhirVersion, otherFhirConfiguration.getFhirVersion())
                    && ObjectHelper.equal(deferModelScanning, otherFhirConfiguration.isDeferModelScanning())
                    && ObjectHelper.equal(encoding, otherFhirConfiguration.getEncoding())
                    && ObjectHelper.equal(username, otherFhirConfiguration.getUsername())
                    && ObjectHelper.equal(password, otherFhirConfiguration.getPassword())
                    && ObjectHelper.equal(accessToken, otherFhirConfiguration.getAccessToken())
                    && ObjectHelper.equal(summary, otherFhirConfiguration.getSummary())
                    && ObjectHelper.equal(sessionCookie, otherFhirConfiguration.getSessionCookie())
                    && ObjectHelper.equal(validationMode, otherFhirConfiguration.getValidationMode())
                    && ObjectHelper.equal(connectionTimeout, otherFhirConfiguration.getConnectionTimeout())
                    && ObjectHelper.equal(socketTimeout, otherFhirConfiguration.getSocketTimeout())
                    && ObjectHelper.equal(proxyHost, otherFhirConfiguration.getProxyHost())
                    && ObjectHelper.equal(proxyPort, otherFhirConfiguration.getProxyPort())
                    && ObjectHelper.equal(proxyUser, otherFhirConfiguration.getProxyUser())
                    && ObjectHelper.equal(proxyPassword, otherFhirConfiguration.getProxyPassword())
                    && ObjectHelper.equal(client, otherFhirConfiguration.getClient())
                    && ObjectHelper.equal(clientFactory, otherFhirConfiguration.getClientFactory())
                    && ObjectHelper.equal(serverUrl, otherFhirConfiguration.getServerUrl());
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = serverUrl != null ? serverUrl.hashCode() : 0;
        result = 31 * result + (fhirVersion != null ? fhirVersion.hashCode() : 0);
        result = 31 * result + (prettyPrint ? 1 : 0);
        result = 31 * result + (encoding != null ? encoding.hashCode() : 0);
        result = 31 * result + (username != null ? username.hashCode() : 0);
        result = 31 * result + (password != null ? password.hashCode() : 0);
        result = 31 * result + (accessToken != null ? accessToken.hashCode() : 0);
        result = 31 * result + (log ? 1 : 0);
        result = 31 * result + (compress ? 1 : 0);
        result = 31 * result + (summary != null ? summary.hashCode() : 0);
        result = 31 * result + (sessionCookie != null ? sessionCookie.hashCode() : 0);
        result = 31 * result + (forceConformanceCheck ? 1 : 0);
        result = 31 * result + (validationMode != null ? validationMode.hashCode() : 0);
        result = 31 * result + (deferModelScanning ? 1 : 0);
        result = 31 * result + (connectionTimeout != null ? connectionTimeout.hashCode() : 0);
        result = 31 * result + (socketTimeout != null ? socketTimeout.hashCode() : 0);
        result = 31 * result + (proxyHost != null ? proxyHost.hashCode() : 0);
        result = 31 * result + (proxyPort != null ? proxyPort.hashCode() : 0);
        result = 31 * result + (proxyUser != null ? proxyUser.hashCode() : 0);
        result = 31 * result + (proxyPassword != null ? proxyPassword.hashCode() : 0);
        return result;
    }
}
