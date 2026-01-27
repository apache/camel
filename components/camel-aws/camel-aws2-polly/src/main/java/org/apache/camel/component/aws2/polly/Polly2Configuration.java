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
package org.apache.camel.component.aws2.polly;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.aws.common.AwsCommonConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.Engine;
import software.amazon.awssdk.services.polly.model.OutputFormat;
import software.amazon.awssdk.services.polly.model.TextType;
import software.amazon.awssdk.services.polly.model.VoiceId;

@UriParams
public class Polly2Configuration implements Cloneable, AwsCommonConfiguration {

    @UriPath(description = "Logical name")
    @Metadata(required = true)
    private String label;
    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private PollyClient pollyClient;
    @UriParam(label = "security", secret = true)
    private String accessKey;
    @UriParam(label = "security", secret = true)
    private String secretKey;
    @UriParam(label = "security", secret = true)
    private String sessionToken;
    @UriParam(defaultValue = "synthesizeSpeech")
    @Metadata(required = true)
    private Polly2Operations operation = Polly2Operations.synthesizeSpeech;
    @UriParam(label = "proxy", enums = "HTTP,HTTPS", defaultValue = "HTTPS")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam(label = "proxy")
    private String proxyHost;
    @UriParam(label = "proxy")
    private Integer proxyPort;
    @UriParam(enums = "ap-south-2,ap-south-1,eu-south-1,eu-south-2,us-gov-east-1,me-central-1,il-central-1,ca-central-1,eu-central-1,us-iso-west-1,eu-central-2,eu-isoe-west-1,us-west-1,us-west-2,af-south-1,eu-north-1,eu-west-3,eu-west-2,eu-west-1,ap-northeast-3,ap-northeast-2,ap-northeast-1,me-south-1,sa-east-1,ap-east-1,cn-north-1,ca-west-1,us-gov-west-1,ap-southeast-1,ap-southeast-2,us-iso-east-1,ap-southeast-3,ap-southeast-4,us-east-1,us-east-2,cn-northwest-1,us-isob-east-1,aws-global,aws-cn-global,aws-us-gov-global,aws-iso-global,aws-iso-b-global")
    private String region;
    @UriParam(description = "The voice ID to use for synthesis")
    private VoiceId voiceId;
    @UriParam(description = "The audio output format", defaultValue = "MP3")
    private OutputFormat outputFormat = OutputFormat.MP3;
    @UriParam(description = "The type of text input (text or ssml)", defaultValue = "TEXT")
    private TextType textType = TextType.TEXT;
    @UriParam(description = "The engine to use for synthesis (standard, neural, long-form, generative)")
    private Engine engine;
    @UriParam(description = "The sample rate in Hz for the audio output")
    private String sampleRate;
    @UriParam(description = "The language code for the synthesis")
    private String languageCode;
    @UriParam(description = "Lexicon names to apply during synthesis")
    private String lexiconNames;
    @UriParam
    private boolean pojoRequest;
    @UriParam(label = "security")
    private boolean trustAllCertificates;
    @UriParam
    private boolean overrideEndpoint;
    @UriParam
    private String uriEndpointOverride;
    @UriParam(label = "security")
    private boolean useDefaultCredentialsProvider;
    @UriParam(label = "security")
    private boolean useProfileCredentialsProvider;
    @UriParam(label = "security")
    private boolean useSessionCredentials;
    @UriParam(label = "security")
    private String profileCredentialsName;

    public PollyClient getPollyClient() {
        return pollyClient;
    }

    /**
     * To use an existing configured AWS Polly client
     */
    public void setPollyClient(PollyClient pollyClient) {
        this.pollyClient = pollyClient;
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

    public Polly2Operations getOperation() {
        return operation;
    }

    /**
     * The operation to perform
     */
    public void setOperation(Polly2Operations operation) {
        this.operation = operation;
    }

    public Protocol getProxyProtocol() {
        return proxyProtocol;
    }

    /**
     * To define a proxy protocol when instantiating the Polly client
     */
    public void setProxyProtocol(Protocol proxyProtocol) {
        this.proxyProtocol = proxyProtocol;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * To define a proxy host when instantiating the Polly client
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    /**
     * To define a proxy port when instantiating the Polly client
     */
    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getRegion() {
        return region;
    }

    /**
     * The region in which the Polly client needs to work. When using this parameter, the configuration will expect the
     * lowercase name of the region (for example, ap-east-1) You'll need to use the name Region.EU_WEST_1.id()
     */
    public void setRegion(String region) {
        this.region = region;
    }

    public VoiceId getVoiceId() {
        return voiceId;
    }

    /**
     * The voice ID to use for speech synthesis. If not set, it must be provided as a header.
     */
    public void setVoiceId(VoiceId voiceId) {
        this.voiceId = voiceId;
    }

    public OutputFormat getOutputFormat() {
        return outputFormat;
    }

    /**
     * The audio format in which the resulting stream will be encoded
     */
    public void setOutputFormat(OutputFormat outputFormat) {
        this.outputFormat = outputFormat;
    }

    public TextType getTextType() {
        return textType;
    }

    /**
     * Specifies whether the input text is plain text or SSML
     */
    public void setTextType(TextType textType) {
        this.textType = textType;
    }

    public Engine getEngine() {
        return engine;
    }

    /**
     * Specifies the engine (standard, neural, long-form, generative) for Amazon Polly to use when processing input text
     * for speech synthesis
     */
    public void setEngine(Engine engine) {
        this.engine = engine;
    }

    public String getSampleRate() {
        return sampleRate;
    }

    /**
     * The audio frequency in Hz
     */
    public void setSampleRate(String sampleRate) {
        this.sampleRate = sampleRate;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    /**
     * Optional language code for voice synthesis. This is only necessary if using a bilingual voice.
     */
    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public String getLexiconNames() {
        return lexiconNames;
    }

    /**
     * Comma-separated list of lexicon names to apply during synthesis
     */
    public void setLexiconNames(String lexiconNames) {
        this.lexiconNames = lexiconNames;
    }

    public boolean isPojoRequest() {
        return pojoRequest;
    }

    /**
     * If we want to use a POJO request as body or not
     */
    public void setPojoRequest(boolean pojoRequest) {
        this.pojoRequest = pojoRequest;
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
     * Set the need for overriding the endpoint. This option needs to be used in combination with the
     * uriEndpointOverride option
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
     * Set whether the Polly client should expect to load credentials through a default credentials provider or to
     * expect static credentials to be passed in.
     */
    public void setUseDefaultCredentialsProvider(Boolean useDefaultCredentialsProvider) {
        this.useDefaultCredentialsProvider = useDefaultCredentialsProvider;
    }

    public boolean isUseDefaultCredentialsProvider() {
        return useDefaultCredentialsProvider;
    }

    public boolean isUseProfileCredentialsProvider() {
        return useProfileCredentialsProvider;
    }

    /**
     * Set whether the Polly client should expect to load credentials through a profile credentials provider.
     */
    public void setUseProfileCredentialsProvider(boolean useProfileCredentialsProvider) {
        this.useProfileCredentialsProvider = useProfileCredentialsProvider;
    }

    public boolean isUseSessionCredentials() {
        return useSessionCredentials;
    }

    /**
     * Set whether the Polly client should expect to use Session Credentials. This is useful in a situation in which the
     * user needs to assume an IAM role for doing operations in Polly.
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
    // *************************************************
    //
    // *************************************************

    public Polly2Configuration copy() {
        try {
            return (Polly2Configuration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
