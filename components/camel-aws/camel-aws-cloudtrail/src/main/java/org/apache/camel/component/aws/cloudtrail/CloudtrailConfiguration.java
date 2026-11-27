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
import org.apache.camel.component.aws.common.AwsCommonConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.cloudtrail.CloudTrailClient;

@UriParams
public class CloudtrailConfiguration implements Cloneable, AwsCommonConfiguration {

    @UriPath(description = "A name of your choice that identifies the endpoint, for example trail. The consumer does not"
                           + " use it: the region, the credentials and the eventSource option select the events.")
    @Metadata(required = true)
    private String label;
    @UriParam(label = "security", security = "secret",
              description = "The access key ID of the AWS credentials, used together with the secretKey option. The"
                            + " endpoint requires it, unless cloudTrailClient is set or useDefaultCredentialsProvider,"
                            + " useProfileCredentialsProvider or useSessionCredentials is true.")
    private String accessKey;
    @UriParam(label = "security", security = "secret",
              description = "The secret access key of the AWS credentials, used together with the accessKey option. The"
                            + " endpoint requires it, unless cloudTrailClient is set or useDefaultCredentialsProvider,"
                            + " useProfileCredentialsProvider or useSessionCredentials is true.")
    private String secretKey;

    @UriParam(label = "security", security = "secret",
              description = "The session token of temporary AWS credentials, for example of an assumed IAM role. It is used"
                            + " only when useSessionCredentials is true, together with the accessKey and secretKey options."
                            + " When it is not set, the accessKey and secretKey options are used without a session token.")
    private String sessionToken;

    @UriParam(enums = "ap-south-2,ap-south-1,eu-south-1,eu-south-2,us-gov-east-1,me-central-1,il-central-1,ca-central-1,eu-central-1,us-iso-west-1,eu-central-2,eu-isoe-west-1,us-west-1,us-west-2,af-south-1,eu-north-1,eu-west-3,eu-west-2,eu-west-1,ap-northeast-3,ap-northeast-2,ap-northeast-1,me-south-1,sa-east-1,ap-east-1,cn-north-1,ca-west-1,us-gov-west-1,ap-southeast-1,ap-southeast-2,us-iso-east-1,ap-southeast-3,ap-southeast-4,us-east-1,us-east-2,cn-northwest-1,us-isob-east-1,aws-global,aws-cn-global,aws-us-gov-global,aws-iso-global,aws-iso-b-global",
              description = "The AWS region of Amazon CloudTrail, as a region ID such as eu-west-1, which is the value of"
                            + " Region.EU_WEST_1.id(). The consumer receives the events of this region only. If not set, the"
                            + " default region provider chain of the AWS SDK is used.")
    private String region;
    @UriParam(label = "advanced",
              description = "To use a custom software.amazon.awssdk.services.cloudtrail.CloudTrailClient, which is autowired"
                            + " when the registry has a single one. When this option is set, the credentials, region, proxy,"
                            + " endpoint override and trustAllCertificates options are not used. The endpoint does not close"
                            + " a client that is set or autowired.")
    @Metadata(autowired = true)
    private CloudTrailClient cloudTrailClient;
    @UriParam(description = "Maximum number of records that will be fetched in each lookup page. Each poll drains"
                            + " all pages, so this controls the page size rather than the total per poll. AWS allows"
                            + " up to 50.",
              defaultValue = "50")
    private int maxResults = 50;
    @UriParam(description = "The event source to receive the events of, which is the AWS service that the request was made"
                            + " to, for example s3.amazonaws.com. The consumer sends it as the EventSource lookup attribute"
                            + " of LookupEvents. If not set, the events of all event sources are received.")
    private String eventSource;
    @UriParam(defaultValue = "600000", javaType = "java.time.Duration",
              description = "How far back the consumer looks again, every lookBackInterval, for events that"
                            + " CloudTrail made visible after a newer event (CloudTrail typically delivers an event"
                            + " within 5 minutes). Events not routed yet are routed. The value is in milliseconds, or a"
                            + " duration such as 10m. Set to 0 to disable.")
    private long lookBackWindow = 600000;
    @UriParam(defaultValue = "60000", javaType = "java.time.Duration",
              description = "How often the consumer looks up the lookBackWindow again for events that CloudTrail made"
                            + " visible late. A late event is routed at most this long after it became visible. Must"
                            + " be above 0 unless lookBackWindow is 0. The value is in milliseconds, or a duration such as"
                            + " 1m.")
    private long lookBackInterval = 60000;
    @UriParam(label = "proxy", enums = "HTTP,HTTPS", defaultValue = "HTTPS",
              description = "The protocol that the HTTP client of Amazon CloudTrail uses to connect to the proxy. It is used"
                            + " only when the proxyHost and proxyPort options are set.")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam(label = "proxy",
              description = "The host name of the proxy that the HTTP client of Amazon CloudTrail connects through. The"
                            + " proxy is used only when the proxyPort option is set too.")
    private String proxyHost;
    @UriParam(label = "proxy",
              description = "The port of the proxy that the HTTP client of Amazon CloudTrail connects through. The proxy is"
                            + " used only when the proxyHost option is set too.")
    private Integer proxyPort;
    @UriParam(security = "insecure:ssl", label = "security",
              description = "Whether the HTTP client of Amazon CloudTrail trusts every TLS certificate without checking it."
                            + " It applies to any endpoint, not only to an overridden one, so use it only for testing, for"
                            + " example against a local emulator.")
    private boolean trustAllCertificates;
    @UriParam(defaultValue = "false",
              description = "Whether to send the requests to the URI of the uriEndpointOverride option instead of the"
                            + " endpoint of the region. It has no effect when uriEndpointOverride is not set.")
    private boolean overrideEndpoint;
    @UriParam(description = "The URI of the endpoint to use instead of the endpoint of the region, for example"
                            + " http://localhost:4566 for a local emulator. It is used only when overrideEndpoint is true.")
    private String uriEndpointOverride;
    @UriParam(label = "security",
              description = "Whether to load the credentials with the default credentials provider chain of the AWS SDK,"
                            + " which looks in system properties, environment variables, the shared credentials and config"
                            + " files, and the container or instance role. It takes precedence over the other credentials"
                            + " options.")
    private boolean useDefaultCredentialsProvider;
    @UriParam(label = "security",
              description = "Whether to load the credentials from a profile of the AWS shared credentials and config files,"
                            + " the one of the profileCredentialsName option. It is not used when useDefaultCredentialsProvider"
                            + " is true, and it takes precedence over the session and static credentials.")
    private boolean useProfileCredentialsProvider;

    @UriParam(label = "security",
              description = "Whether to use temporary credentials made of the accessKey, secretKey and sessionToken options,"
                            + " for example those of an assumed IAM role. It is not used when useDefaultCredentialsProvider or"
                            + " useProfileCredentialsProvider is true.")
    private boolean useSessionCredentials;
    @UriParam(label = "security",
              description = "The name of the profile in the AWS shared credentials and config files, used when"
                            + " useProfileCredentialsProvider is true. If not set, the AWS SDK uses its default profile, which"
                            + " the AWS_PROFILE environment variable can change.")
    private String profileCredentialsName;

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

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
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

    public long getLookBackWindow() {
        return lookBackWindow;
    }

    public void setLookBackWindow(long lookBackWindow) {
        this.lookBackWindow = lookBackWindow;
    }

    public long getLookBackInterval() {
        return lookBackInterval;
    }

    public void setLookBackInterval(long lookBackInterval) {
        this.lookBackInterval = lookBackInterval;
    }

    public boolean isUseProfileCredentialsProvider() {
        return useProfileCredentialsProvider;
    }

    public void setUseProfileCredentialsProvider(boolean useProfileCredentialsProvider) {
        this.useProfileCredentialsProvider = useProfileCredentialsProvider;
    }

    public boolean isUseSessionCredentials() {
        return useSessionCredentials;
    }

    public void setUseSessionCredentials(boolean useSessionCredentials) {
        this.useSessionCredentials = useSessionCredentials;
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
    public CloudtrailConfiguration copy() {
        try {
            return (CloudtrailConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
