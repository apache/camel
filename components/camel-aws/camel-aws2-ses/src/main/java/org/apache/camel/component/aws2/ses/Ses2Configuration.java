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
package org.apache.camel.component.aws2.ses;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.ses.SesClient;

@UriParams
public class Ses2Configuration implements Cloneable {

    @UriPath
    @Metadata(required = true)
    private String from;
    @UriParam
    @Metadata(autowired = true)
    private SesClient amazonSESClient;
    @UriParam(label = "security", secret = true)
    private String accessKey;
    @UriParam(label = "security", secret = true)
    private String secretKey;
    @UriParam
    private String subject;
    @UriParam
    private String to;
    @UriParam
    private String cc;
    @UriParam
    private String bcc;
    @UriParam
    private String returnPath;
    @UriParam
    private String replyToAddresses;
    @UriParam(enums = "HTTP,HTTPS", defaultValue = "HTTPS")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam
    private String proxyHost;
    @UriParam
    private Integer proxyPort;
    @UriParam
    private String region;
    @UriParam(defaultValue = "false")
    private boolean trustAllCertificates;
    @UriParam(defaultValue = "false")
    private boolean overrideEndpoint;
    @UriParam
    private String uriEndpointOverride;
    @UriParam(defaultValue = "false")
    private boolean useDefaultCredentialsProvider;
    @UriParam
    private String configurationSet;

    public String getAccessKey() {
        return accessKey;
    }

    /**
     * Amazon AWS Access Key
     */
    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public SesClient getAmazonSESClient() {
        return amazonSESClient;
    }

    /**
     * To use the AmazonSimpleEmailService as the client
     */
    public void setAmazonSESClient(SesClient amazonSESClient) {
        this.amazonSESClient = amazonSESClient;
    }

    public String getFrom() {
        return from;
    }

    /**
     * The sender's email address.
     */
    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    /**
     * List of comma separated destination email address. Can be overridden with 'CamelAwsSesTo' header.
     */
    public void setTo(String to) {
        this.to = to;
    }

    public String getCc() {
        return cc;
    }

    /**
     * List of comma separated destination carbon copy (cc) email address. Can be overridden with 'CamelAwsSesCc'
     * header.
     */
    public void setCc(String cc) {
        this.cc = cc;
    }

    public String getBcc() {
        return bcc;
    }

    /**
     * List of comma separated destination blind carbon copy (bcc) email address. Can be overridden with
     * 'CamelAwsSesBcc' header.
     */
    public void setBcc(String bcc) {
        this.bcc = bcc;
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

    public String getSubject() {
        return subject;
    }

    /**
     * The subject which is used if the message header 'CamelAwsSesSubject' is not present.
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getReturnPath() {
        return returnPath;
    }

    /**
     * The email address to which bounce notifications are to be forwarded, override it using 'CamelAwsSesReturnPath'
     * header.
     */
    public void setReturnPath(String returnPath) {
        this.returnPath = returnPath;
    }

    public String getReplyToAddresses() {
        return replyToAddresses;
    }

    /**
     * List of comma separated reply-to email address(es) for the message, override it using
     * 'CamelAwsSesReplyToAddresses' header.
     */
    public void setReplyToAddresses(String replyToAddresses) {
        this.replyToAddresses = replyToAddresses;
    }

    public Protocol getProxyProtocol() {
        return proxyProtocol;
    }

    /**
     * To define a proxy protocol when instantiating the SES client
     */
    public void setProxyProtocol(Protocol proxyProtocol) {
        this.proxyProtocol = proxyProtocol;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * To define a proxy host when instantiating the SES client
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    /**
     * To define a proxy port when instantiating the SES client
     */
    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getRegion() {
        return region;
    }

    /**
     * The region in which SES client needs to work. When using this parameter, the configuration will expect the
     * lowercase name of the region (for example ap-east-1) You'll need to use the name Region.EU_WEST_1.id()
     */
    public void setRegion(String region) {
        this.region = region;
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

    public boolean isOverrideEndpoint() {
        return overrideEndpoint;
    }

    /**
     * Set the need for overidding the endpoint. This option needs to be used in combination with uriEndpointOverride
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

    /**
     * Set whether the Ses client should expect to load credentials through a default credentials provider or to expect
     * static credentials to be passed in.
     */
    public void setUseDefaultCredentialsProvider(Boolean useDefaultCredentialsProvider) {
        this.useDefaultCredentialsProvider = useDefaultCredentialsProvider;
    }

    /**
     * The configuration set to send with every request
     */
    public String getConfigurationSet() {
        return configurationSet;
    }

    /**
     * Set the configuration set to send with every request. Override it with 'CamelAwsSesConfigurationSet' header.
     */
    public void setConfigurationSet(String configurationSet) {
        this.configurationSet = configurationSet;
    }

    public Boolean isUseDefaultCredentialsProvider() {
        return useDefaultCredentialsProvider;
    }
    // *************************************************
    //
    // *************************************************

    public Ses2Configuration copy() {
        try {
            return (Ses2Configuration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
