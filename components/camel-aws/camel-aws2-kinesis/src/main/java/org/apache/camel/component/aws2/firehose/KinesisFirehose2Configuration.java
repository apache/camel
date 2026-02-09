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
package org.apache.camel.component.aws2.firehose;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.aws.common.AwsCommonConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.firehose.FirehoseClient;

@UriParams
public class KinesisFirehose2Configuration implements Cloneable, AwsCommonConfiguration {

    @UriPath(description = "Name of the stream")
    @Metadata(required = true)
    private String streamName;
    @UriParam(label = "security", secret = true, description = "Amazon AWS Access Key")
    private String accessKey;
    @UriParam(label = "security", secret = true, description = "Amazon AWS Secret Key")
    private String secretKey;
    @UriParam(label = "security", secret = true,
              description = "Amazon AWS Session Token used when the user needs to assume a IAM role")
    private String sessionToken;
    @UriParam(enums = "ap-south-2,ap-south-1,eu-south-1,eu-south-2,us-gov-east-1,me-central-1,il-central-1,ca-central-1,eu-central-1,us-iso-west-1,eu-central-2,eu-isoe-west-1,us-west-1,us-west-2,af-south-1,eu-north-1,eu-west-3,eu-west-2,eu-west-1,ap-northeast-3,ap-northeast-2,ap-northeast-1,me-south-1,sa-east-1,ap-east-1,cn-north-1,ca-west-1,us-gov-west-1,ap-southeast-1,ap-southeast-2,us-iso-east-1,ap-southeast-3,ap-southeast-4,us-east-1,us-east-2,cn-northwest-1,us-isob-east-1,aws-global,aws-cn-global,aws-us-gov-global,aws-iso-global,aws-iso-b-global",
              description = "The region in which Kinesis Firehose client needs to work. When using this parameter, the configuration will expect the lowercase name of the "
                            + "region (for example ap-east-1) You'll need to use the name Region.EU_WEST_1.id()")
    private String region;
    @UriParam(description = "Amazon Kinesis Firehose client to use for all requests for this endpoint")
    @Metadata(label = "advanced", autowired = true)
    private FirehoseClient amazonKinesisFirehoseClient;
    @UriParam(label = "proxy", enums = "HTTP,HTTPS", defaultValue = "HTTPS",
              description = "To define a proxy protocol when instantiating the Kinesis Firehose client")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam(label = "proxy", description = "To define a proxy host when instantiating the Kinesis Firehose client")
    private String proxyHost;
    @UriParam(label = "proxy", description = "To define a proxy port when instantiating the Kinesis Firehose client")
    private Integer proxyPort;
    @UriParam(label = "producer", description = "The operation to do in case the user don't want to send only a record")
    private KinesisFirehose2Operations operation;
    @UriParam(label = "security", description = "If we want to trust all certificates in case of overriding the endpoint")
    private boolean trustAllCertificates;
    @UriParam(label = "common", defaultValue = "true",
              description = "This option will set the CBOR_ENABLED property during the execution")
    private boolean cborEnabled = true;
    @UriParam(label = "common",
              description = "Set the need for overriding the endpoint. This option needs to be used in combination with uriEndpointOverride"
                            + " option")
    private boolean overrideEndpoint;
    @UriParam(label = "common",
              description = "Set the overriding uri endpoint. This option needs to be used in combination with overrideEndpoint option")
    private String uriEndpointOverride;
    @UriParam(label = "common",
              description = "Set whether the Kinesis Firehose client should expect to load credentials through a default credentials provider or to expect"
                            + " static credentials to be passed in.")
    private boolean useDefaultCredentialsProvider;
    @UriParam(label = "security",
              description = "Set whether the Kinesis Firehose client should expect to load credentials through a profile credentials provider.")
    private boolean useProfileCredentialsProvider;

    @UriParam(label = "security",
              description = "Set whether the Kinesis Firehose client should expect to use Session Credentials. This is useful in situation in which the user"
                            +
                            " needs to assume a IAM role for doing operations in Kinesis Firehose.")
    private boolean useSessionCredentials;
    @UriParam(label = "security",
              description = "If using a profile credentials provider this parameter will set the profile name.")
    private String profileCredentialsName;

    public void setAmazonKinesisFirehoseClient(FirehoseClient client) {
        this.amazonKinesisFirehoseClient = client;
    }

    public FirehoseClient getAmazonKinesisFirehoseClient() {
        return amazonKinesisFirehoseClient;
    }

    public void setStreamName(String streamName) {
        this.streamName = streamName;
    }

    public String getStreamName() {
        return streamName;
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

    public KinesisFirehose2Operations getOperation() {
        return operation;
    }

    public void setOperation(KinesisFirehose2Operations operation) {
        this.operation = operation;
    }

    public boolean isTrustAllCertificates() {
        return trustAllCertificates;
    }

    public void setTrustAllCertificates(boolean trustAllCertificates) {
        this.trustAllCertificates = trustAllCertificates;
    }

    public boolean isCborEnabled() {
        return cborEnabled;
    }

    public void setCborEnabled(boolean cborEnabled) {
        this.cborEnabled = cborEnabled;
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

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public boolean isUseSessionCredentials() {
        return useSessionCredentials;
    }

    public void setUseSessionCredentials(boolean useSessionCredentials) {
        this.useSessionCredentials = useSessionCredentials;
    }

    // *************************************************
    //
    // *************************************************

    public KinesisFirehose2Configuration copy() {
        try {
            return (KinesisFirehose2Configuration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
