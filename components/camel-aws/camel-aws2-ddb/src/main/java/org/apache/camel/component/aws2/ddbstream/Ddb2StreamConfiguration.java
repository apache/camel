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
package org.apache.camel.component.aws2.ddbstream;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;

@UriParams
public class Ddb2StreamConfiguration implements Cloneable {

    @UriPath(label = "consumer", description = "Name of the dynamodb table")
    @Metadata(required = true)
    private String tableName;

    @UriParam(label = "security", secret = true, description = "Amazon AWS Access Key")
    private String accessKey;
    @UriParam(label = "security", secret = true, description = "Amazon AWS Secret Key")
    private String secretKey;
    @UriParam(description = "The region in which DDBStreams client needs to work")
    private String region;

    @UriParam(label = "consumer,advanced", description = "Amazon DynamoDB client to use for all requests for this endpoint")
    @Metadata(autowired = true)
    private DynamoDbStreamsClient amazonDynamoDbStreamsClient;

    @UriParam(label = "consumer", description = "Maximum number of records that will be fetched in each poll")
    private int maxResultsPerRequest = 100;

    @UriParam(label = "consumer",
              description = "Defines where in the DynamoDB stream"
                            + " to start getting records. Note that using FROM_START can cause a"
                            + " significant delay before the stream has caught up to real-time.",
              defaultValue = "FROM_LATEST")
    private StreamIteratorType streamIteratorType = StreamIteratorType.FROM_LATEST;

    @UriParam(label = "proxy", enums = "HTTP,HTTPS", defaultValue = "HTTPS",
              description = "To define a proxy protocol when instantiating the DDBStreams client")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam(label = "proxy", description = "To define a proxy host when instantiating the DDBStreams client")
    private String proxyHost;
    @UriParam(label = "proxy", description = "To define a proxy port when instantiating the DDBStreams client")
    private Integer proxyPort;
    @UriParam(label = "security", description = "If we want to trust all certificates in case of overriding the endpoint")
    private boolean trustAllCertificates;
    @UriParam(defaultValue = "false",
              description = "Set the need for overidding the endpoint. This option needs to be used in combination with uriEndpointOverride option")
    private boolean overrideEndpoint;
    @UriParam(description = " Set the overriding uri endpoint. This option needs to be used in combination with overrideEndpoint option")
    private String uriEndpointOverride;
    @UriParam(label = "security",
              description = "Set whether the DynamoDB Streams client should expect to load credentials through a default credentials provider or to expect"
                            + " static credentials to be passed in.")
    private boolean useDefaultCredentialsProvider;
    @UriParam(label = "security",
              description = "Set whether the Cloudtrail client should expect to load credentials through a profile credentials provider.")
    private boolean useProfileCredentialsProvider;
    @UriParam(label = "security",
              description = "If using a profile credentials provider this parameter will set the profile name.")
    private String profileCredentialsName;

    public DynamoDbStreamsClient getAmazonDynamoDbStreamsClient() {
        return amazonDynamoDbStreamsClient;
    }

    public void setAmazonDynamoDbStreamsClient(DynamoDbStreamsClient amazonDynamoDbStreamsClient) {
        this.amazonDynamoDbStreamsClient = amazonDynamoDbStreamsClient;
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

    public int getMaxResultsPerRequest() {
        return maxResultsPerRequest;
    }

    public void setMaxResultsPerRequest(int maxResultsPerRequest) {
        this.maxResultsPerRequest = maxResultsPerRequest;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public StreamIteratorType getStreamIteratorType() {
        return streamIteratorType;
    }

    public void setStreamIteratorType(StreamIteratorType streamIteratorType) {
        this.streamIteratorType = streamIteratorType;
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

    public void setUseDefaultCredentialsProvider(Boolean useDefaultCredentialsProvider) {
        this.useDefaultCredentialsProvider = useDefaultCredentialsProvider;
    }

    public Boolean isUseDefaultCredentialsProvider() {
        return useDefaultCredentialsProvider;
    }

    public boolean isUseProfileCredentialsProvider() {
        return useProfileCredentialsProvider;
    }

    public void setUseProfileCredentialsProvider(boolean useProfileCredentialsProvider) {
        this.useProfileCredentialsProvider = useProfileCredentialsProvider;
    }

    public String getProfileCredentialsName() {
        return profileCredentialsName;
    }

    public void setProfileCredentialsName(String profileCredentialsName) {
        this.profileCredentialsName = profileCredentialsName;
    }

    // *************************************************
    //
    // *************************************************

    public Ddb2StreamConfiguration copy() {
        try {
            return (Ddb2StreamConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public enum StreamIteratorType {
        FROM_LATEST,
        FROM_START
    }
}
