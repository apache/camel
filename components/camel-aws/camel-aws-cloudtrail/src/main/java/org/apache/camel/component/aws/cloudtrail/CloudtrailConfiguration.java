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
package org.apache.camel.component.aws.cloudtrail;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.cloudtrail.CloudTrailClient;

@UriParams
public class CloudtrailConfiguration implements Cloneable {

    @UriPath(description = "A label for indexing cloudtrail endpoints")
    @Metadata(required = true)
    private String label;
    @UriParam(label = "security", secret = true, description = "Amazon AWS Access Key")
    private String accessKey;
    @UriParam(label = "security", secret = true, description = "Amazon AWS Secret Key")
    private String secretKey;
    @UriParam(description = "The region in which Cloudtrail client needs to work. When using this parameter, the configuration will expect the lowercase name of the "
                            + "region (for example ap-east-1) You'll need to use the name Region.EU_WEST_1.id()")
    private String region;
    @UriParam(description = "Amazon Cloudtrail client to use for all requests for this endpoint")
    @Metadata(autowired = true)
    private CloudTrailClient cloudTrailClient;
    @UriParam(description = "Maximum number of records that will be fetched in each poll",
              defaultValue = "1")
    private int maxResults = 1;
    @UriParam(description = "Specify an event source to select events")
    private String eventSource;
    @UriParam(enums = "HTTP,HTTPS", defaultValue = "HTTPS",
              description = "To define a proxy protocol when instantiating the Cloudtrail client")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam(description = "To define a proxy host when instantiating the Cloudtrail client")
    private String proxyHost;
    @UriParam(description = "To define a proxy port when instantiating the Cloudtrail client")
    private Integer proxyPort;
    @UriParam(defaultValue = "false", description = "If we want to trust all certificates in case of overriding the endpoint")
    private boolean trustAllCertificates;
    @UriParam(defaultValue = "false",
              description = "Set the need for overidding the endpoint. This option needs to be used in combination with uriEndpointOverride"
                            + " option")
    private boolean overrideEndpoint;
    @UriParam(
              description = "Set the overriding uri endpoint. This option needs to be used in combination with overrideEndpoint option")
    private String uriEndpointOverride;
    @UriParam(defaultValue = "false",
              description = "Set whether the Cloudtrail client should expect to load credentials through a default credentials provider or to expect "
                            +
                            "static credentials to be passed in.")
    private boolean useDefaultCredentialsProvider;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public CloudTrailClient getCloudTrailClient() {
        return cloudTrailClient;
    }

    public void setCloudTrailClient(CloudTrailClient cloudTrailClient) {
        this.cloudTrailClient = cloudTrailClient;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public Protocol getProxyProtocol() {
        return proxyProtocol;
    }

    public void setProxyProtocol(Protocol proxyProtocol) {
        this.proxyProtocol = proxyProtocol;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public boolean isTrustAllCertificates() {
        return trustAllCertificates;
    }

    public void setTrustAllCertificates(boolean trustAllCertificates) {
        this.trustAllCertificates = trustAllCertificates;
    }

    public boolean isOverrideEndpoint() {
        return overrideEndpoint;
    }

    public void setOverrideEndpoint(boolean overrideEndpoint) {
        this.overrideEndpoint = overrideEndpoint;
    }

    public String getUriEndpointOverride() {
        return uriEndpointOverride;
    }

    public void setUriEndpointOverride(String uriEndpointOverride) {
        this.uriEndpointOverride = uriEndpointOverride;
    }

    public boolean isUseDefaultCredentialsProvider() {
        return useDefaultCredentialsProvider;
    }

    public void setUseDefaultCredentialsProvider(boolean useDefaultCredentialsProvider) {
        this.useDefaultCredentialsProvider = useDefaultCredentialsProvider;
    }

    public String getEventSource() {
        return eventSource;
    }

    public void setEventSource(String eventSource) {
        this.eventSource = eventSource;
    }

    // *************************************************
    //
    // *************************************************
    public CloudtrailConfiguration copy() {
        try {
            return (CloudtrailConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
