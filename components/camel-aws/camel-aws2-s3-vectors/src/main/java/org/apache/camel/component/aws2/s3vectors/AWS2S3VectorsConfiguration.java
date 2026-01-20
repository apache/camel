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
package org.apache.camel.component.aws2.s3vectors;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.aws.common.AwsCommonConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;

@UriParams
public class AWS2S3VectorsConfiguration implements Cloneable, AwsCommonConfiguration {

    private String vectorBucketName;
    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private S3VectorsClient s3VectorsClient;
    @UriParam(label = "security", secret = true)
    private String accessKey;
    @UriParam(label = "security", secret = true)
    private String secretKey;
    @UriParam(label = "security", secret = true)
    private String sessionToken;
    @UriParam(enums = "ap-south-2,ap-south-1,eu-south-1,eu-south-2,us-gov-east-1,me-central-1,il-central-1,ca-central-1,eu-central-1,us-iso-west-1,eu-central-2,eu-isoe-west-1,us-west-1,us-west-2,af-south-1,eu-north-1,eu-west-3,eu-west-2,eu-west-1,ap-northeast-3,ap-northeast-2,ap-northeast-1,me-south-1,sa-east-1,ap-east-1,cn-north-1,ca-west-1,us-gov-west-1,ap-southeast-1,ap-southeast-2,us-iso-east-1,ap-southeast-3,ap-southeast-4,us-east-1,us-east-2,cn-northwest-1,us-isob-east-1,aws-global,aws-cn-global,aws-us-gov-global,aws-iso-global,aws-iso-b-global")
    private String region;
    @UriParam(label = "security")
    private boolean useDefaultCredentialsProvider;
    @UriParam(label = "security")
    private boolean useProfileCredentialsProvider;
    @UriParam(label = "security")
    private boolean useSessionCredentials;
    @UriParam(label = "security")
    private String profileCredentialsName;
    @UriParam
    private boolean overrideEndpoint;
    @UriParam(label = "security")
    private boolean trustAllCertificates;
    @UriParam
    private String uriEndpointOverride;
    @UriParam(label = "proxy", enums = "HTTP,HTTPS", defaultValue = "HTTPS")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam(label = "proxy")
    private String proxyHost;
    @UriParam(label = "proxy")
    private Integer proxyPort;
    @UriParam(label = "producer")
    private AWS2S3VectorsOperations operation;

    // Vector-specific configuration
    @UriParam
    private String vectorIndexName;
    @UriParam(defaultValue = "1536")
    private Integer vectorDimensions = 1536;
    @UriParam(enums = "float32,float16", defaultValue = "float32")
    private String dataType = "float32";
    @UriParam(enums = "cosine,euclidean,dot-product", defaultValue = "cosine")
    private String distanceMetric = "cosine";
    @UriParam(defaultValue = "10")
    private Integer topK = 10;
    @UriParam
    private Float similarityThreshold;

    // Consumer configuration
    @UriParam(label = "consumer", defaultValue = "500")
    private long delay = 500;
    @UriParam(label = "consumer", defaultValue = "10")
    private int maxMessagesPerPoll = 10;
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean deleteAfterRead;
    @UriParam(label = "consumer")
    private String consumerQueryVector;
    @UriParam(label = "consumer")
    private String consumerMetadataFilter;

    public String getVectorBucketName() {
        return vectorBucketName;
    }

    /**
     * The name of the vector bucket
     */
    public void setVectorBucketName(String vectorBucketName) {
        this.vectorBucketName = vectorBucketName;
    }

    public S3VectorsClient getS3VectorsClient() {
        return s3VectorsClient;
    }

    /**
     * Reference to a `software.amazon.awssdk.services.s3vectors.S3VectorsClient` in the registry.
     */
    public void setS3VectorsClient(S3VectorsClient s3VectorsClient) {
        this.s3VectorsClient = s3VectorsClient;
    }

    public String getAccessKey() {
        return accessKey;
    }

    /**
     * Amazon AWS Access Key
     */
    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    /**
     * Amazon AWS Secret Key
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    /**
     * Amazon AWS Session Token used when the user needs to assume an IAM role
     */
    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public String getRegion() {
        return region;
    }

    /**
     * The region in which S3 Vectors client needs to work. When using this parameter, the configuration will expect the
     * lowercase name of the region (for example ap-east-1)
     */
    public void setRegion(String region) {
        this.region = region;
    }

    public boolean isUseDefaultCredentialsProvider() {
        return useDefaultCredentialsProvider;
    }

    /**
     * Set whether the S3 Vectors client should expect to load credentials through a default credentials provider.
     */
    public void setUseDefaultCredentialsProvider(boolean useDefaultCredentialsProvider) {
        this.useDefaultCredentialsProvider = useDefaultCredentialsProvider;
    }

    public boolean isUseProfileCredentialsProvider() {
        return useProfileCredentialsProvider;
    }

    /**
     * Set whether the S3 Vectors client should expect to load credentials through a profile credentials provider.
     */
    public void setUseProfileCredentialsProvider(boolean useProfileCredentialsProvider) {
        this.useProfileCredentialsProvider = useProfileCredentialsProvider;
    }

    public boolean isUseSessionCredentials() {
        return useSessionCredentials;
    }

    /**
     * Set whether the S3 Vectors client should expect to use Session Credentials. This is useful in a situation in
     * which the user needs to assume an IAM role for doing operations in S3 Vectors.
     */
    public void setUseSessionCredentials(boolean useSessionCredentials) {
        this.useSessionCredentials = useSessionCredentials;
    }

    public String getProfileCredentialsName() {
        return profileCredentialsName;
    }

    /**
     * If using a profile credentials provider, this parameter will set the profile name
     */
    public void setProfileCredentialsName(String profileCredentialsName) {
        this.profileCredentialsName = profileCredentialsName;
    }

    public Protocol getProxyProtocol() {
        return proxyProtocol;
    }

    /**
     * To define a proxy protocol when instantiating the S3 Vectors client
     */
    public void setProxyProtocol(Protocol proxyProtocol) {
        this.proxyProtocol = proxyProtocol;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * To define a proxy host when instantiating the S3 Vectors client
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    /**
     * To define a proxy port when instantiating the S3 Vectors client
     */
    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public AWS2S3VectorsOperations getOperation() {
        return operation;
    }

    /**
     * The operation to perform
     */
    public void setOperation(AWS2S3VectorsOperations operation) {
        this.operation = operation;
    }

    public boolean isOverrideEndpoint() {
        return overrideEndpoint;
    }

    /**
     * Set the need for overriding the endpoint. This option needs to be used in combination with uriEndpointOverride
     * option
     */
    public void setOverrideEndpoint(boolean overrideEndpoint) {
        this.overrideEndpoint = overrideEndpoint;
    }

    public String getUriEndpointOverride() {
        return uriEndpointOverride;
    }

    /**
     * Set the overriding uri endpoint. This option needs to be used in combination with overrideEndpoint option
     */
    public void setUriEndpointOverride(String uriEndpointOverride) {
        this.uriEndpointOverride = uriEndpointOverride;
    }

    public boolean isTrustAllCertificates() {
        return trustAllCertificates;
    }

    /**
     * If we want to trust all certificates in case of overriding the endpoint
     */
    public void setTrustAllCertificates(boolean trustAllCertificates) {
        this.trustAllCertificates = trustAllCertificates;
    }

    public String getVectorIndexName() {
        return vectorIndexName;
    }

    /**
     * The name of the vector index
     */
    public void setVectorIndexName(String vectorIndexName) {
        this.vectorIndexName = vectorIndexName;
    }

    public Integer getVectorDimensions() {
        return vectorDimensions;
    }

    /**
     * The dimensions of the vector embeddings (default: 1536, which is the dimension for OpenAI text-embedding-3-small)
     */
    public void setVectorDimensions(Integer vectorDimensions) {
        this.vectorDimensions = vectorDimensions;
    }

    public String getDataType() {
        return dataType;
    }

    /**
     * The data type of the vector. Options: float32, float16
     */
    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getDistanceMetric() {
        return distanceMetric;
    }

    /**
     * The distance metric to use for similarity search. Options: cosine, euclidean, dot-product
     */
    public void setDistanceMetric(String distanceMetric) {
        this.distanceMetric = distanceMetric;
    }

    public Integer getTopK() {
        return topK;
    }

    /**
     * The number of top similar vectors to return in a query
     */
    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Float getSimilarityThreshold() {
        return similarityThreshold;
    }

    /**
     * The minimum similarity threshold for results
     */
    public void setSimilarityThreshold(Float similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public long getDelay() {
        return delay;
    }

    /**
     * Milliseconds before the next poll for the consumer
     */
    public void setDelay(long delay) {
        this.delay = delay;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    /**
     * The maximum number of messages to consume per poll for the consumer
     */
    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    public boolean isDeleteAfterRead() {
        return deleteAfterRead;
    }

    /**
     * Delete vectors after they have been consumed
     */
    public void setDeleteAfterRead(boolean deleteAfterRead) {
        this.deleteAfterRead = deleteAfterRead;
    }

    public String getConsumerQueryVector() {
        return consumerQueryVector;
    }

    /**
     * The query vector to use for the consumer to poll for similar vectors. Specified as comma-separated float values
     * (e.g., "0.1,0.2,0.3"). If not specified, the consumer will not poll.
     */
    public void setConsumerQueryVector(String consumerQueryVector) {
        this.consumerQueryVector = consumerQueryVector;
    }

    public String getConsumerMetadataFilter() {
        return consumerMetadataFilter;
    }

    /**
     * Optional metadata filter for the consumer to filter vectors during polling
     */
    public void setConsumerMetadataFilter(String consumerMetadataFilter) {
        this.consumerMetadataFilter = consumerMetadataFilter;
    }

    public AWS2S3VectorsConfiguration copy() {
        try {
            return (AWS2S3VectorsConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
