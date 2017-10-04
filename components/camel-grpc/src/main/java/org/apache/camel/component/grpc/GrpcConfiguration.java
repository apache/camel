/**
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
package org.apache.camel.component.grpc;

import java.net.URI;
import java.util.Map;

import io.grpc.internal.GrpcUtil;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import org.apache.camel.component.grpc.auth.jwt.JwtAlgorithm;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class GrpcConfiguration {

    @UriPath
    @Metadata(required = "true")
    private String host;

    @UriPath
    @Metadata(required = "true")
    private int port;
    
    @UriPath
    @Metadata(required = "true")
    private String service;
    
    @UriParam(label = "producer")
    private String method;
            
    @UriParam(label = "security", defaultValue = "PLAINTEXT")
    private NegotiationType negotiationType = NegotiationType.PLAINTEXT;
    
    @UriParam(label = "security", defaultValue = "NONE")
    private GrpcAuthType authenticationType = GrpcAuthType.NONE;
    
    @UriParam(label = "security", defaultValue = "HMAC256")
    private JwtAlgorithm jwtAlgorithm = JwtAlgorithm.HMAC256;
    
    @UriParam(label = "security", secret = true)
    private String jwtSecret;
    
    @UriParam(label = "security")
    private String jwtIssuer;
    
    @UriParam(label = "security")
    private String jwtSubject;
    
    @UriParam(label = "security")
    private String serviceAccountResource;
    
    @UriParam(label = "security")
    private String keyCertChainResource;
    
    @UriParam(label = "security")
    private String keyResource;
    
    @UriParam(label = "security", secret = true)
    private String keyPassword;
    
    @UriParam(label = "security")
    private String trustCertCollectionResource;

    @UriParam(label = "producer", defaultValue = "SIMPLE")
    private GrpcProducerStrategy producerStrategy = GrpcProducerStrategy.SIMPLE;

    @UriParam(label = "producer")
    private String streamRepliesTo;
    
    @UriParam(label = "producer")
    private String userAgent;

    @UriParam(label = "consumer", defaultValue = "PROPAGATION")
    private GrpcConsumerStrategy consumerStrategy = GrpcConsumerStrategy.PROPAGATION;
    
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean forwardOnCompleted;

    @UriParam(label = "consumer", defaultValue = "false")
    private boolean forwardOnError;
    
    @UriParam(defaultValue = "" + NettyChannelBuilder.DEFAULT_FLOW_CONTROL_WINDOW)
    private int flowControlWindow = NettyChannelBuilder.DEFAULT_FLOW_CONTROL_WINDOW;
    
    @UriParam(defaultValue = "" + GrpcUtil.DEFAULT_MAX_MESSAGE_SIZE)
    private int maxMessageSize = GrpcUtil.DEFAULT_MAX_MESSAGE_SIZE;
    
    @UriParam(label = "consumer", defaultValue = "" + Integer.MAX_VALUE)
    private int maxConcurrentCallsPerConnection = Integer.MAX_VALUE;
    
    /**
     * Fully qualified service name from the protocol buffer descriptor file
     * (package dot service definition name)
     */
    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    /**
     * gRPC method name
     */
    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    /**
     * The gRPC server host name. This is localhost or 0.0.0.0 when being a
     * consumer or remote server host name when using producer.
     */
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    /**
     * The gRPC local or remote server port
     */
    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
    
    /**
     * Identifies the security negotiation type used for HTTP/2 communication
     */
    public void setNegotiationType(NegotiationType negotiationType) {
        this.negotiationType = negotiationType;
    }
    
    public NegotiationType getNegotiationType() {
        return negotiationType;
    }
    
    /**
     * Authentication method type in advance to the SSL/TLS negotiation
     */
    public GrpcAuthType getAuthenticationType() {
        return authenticationType;
    }

    public void setAuthenticationType(GrpcAuthType authenticationType) {
        this.authenticationType = authenticationType;
    }
    
    /**
     * JSON Web Token sign algorithm
     */
    public JwtAlgorithm getJwtAlgorithm() {
        return jwtAlgorithm;
    }

    public void setJwtAlgorithm(JwtAlgorithm jwtAlgorithm) {
        this.jwtAlgorithm = jwtAlgorithm;
    }
    
    /**
     * JSON Web Token secret
     */
    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    /**
     * JSON Web Token issuer
     */
    public String getJwtIssuer() {
        return jwtIssuer;
    }

    public void setJwtIssuer(String jwtIssuer) {
        this.jwtIssuer = jwtIssuer;
    }

    /**
     * JSON Web Token subject
     */
    public String getJwtSubject() {
        return jwtSubject;
    }

    public void setJwtSubject(String jwtSubject) {
        this.jwtSubject = jwtSubject;
    }
    
    /**
     * Service Account key file in JSON format resource link supported by the Google Cloud SDK
     */
    public String getServiceAccountResource() {
        return serviceAccountResource;
    }

    public void setServiceAccountResource(String serviceAccountResource) {
        this.serviceAccountResource = serviceAccountResource;
    }

    /**
     * The X.509 certificate chain file resource in PEM format link 
     */
    public void setKeyCertChainResource(String keyCertChainResource) {
        this.keyCertChainResource = keyCertChainResource;
    }
    
    public String getKeyCertChainResource() {
        return keyCertChainResource;
    }

    /**
     * The PKCS#8 private key file resource in PEM format link 
     */
    public void setKeyResource(String keyResource) {
        this.keyResource = keyResource;
    }
    
    public String getKeyResource() {
        return keyResource;
    }
    
    /**
     * The PKCS#8 private key file password
     */
    public String getKeyPassword() {
        return keyPassword;
    }

    public void setKeyPassword(String keyPassword) {
        this.keyPassword = keyPassword;
    }

    /**
     * The trusted certificates collection file resource in PEM format for verifying the remote endpoint's certificate
     */
    public void setTrustCertCollectionResource(String trustCertCollectionResource) {
        this.trustCertCollectionResource = trustCertCollectionResource;
    }
    
    public String getTrustCertCollectionResource() {
        return trustCertCollectionResource;
    }

    /**
     * This option specifies the top-level strategy for processing service
     * requests and responses in streaming mode. If an aggregation strategy is
     * selected, all requests will be accumulated in the list, then transferred
     * to the flow, and the accumulated responses will be sent to the sender. If
     * a propagation strategy is selected, request is sent to the stream, and the
     * response will be immediately sent back to the sender.
     */
    public GrpcConsumerStrategy getConsumerStrategy() {
        return consumerStrategy;
    }

    public void setConsumerStrategy(GrpcConsumerStrategy consumerStrategy) {
        this.consumerStrategy = consumerStrategy;
    }

    /**
     * Determines if onCompleted events should be pushed to the Camel route.
     */
    public void setForwardOnCompleted(boolean forwardOnCompleted) {
        this.forwardOnCompleted = forwardOnCompleted;
    }

    public boolean isForwardOnCompleted() {
        return forwardOnCompleted;
    }

    /**
     * Determines if onError events should be pushed to the Camel route.
     * Exceptions will be set as message body.
     */
    public void setForwardOnError(boolean forwardOnError) {
        this.forwardOnError = forwardOnError;
    }

    public boolean isForwardOnError() {
        return forwardOnError;
    }

    public GrpcProducerStrategy getProducerStrategy() {
        return producerStrategy;
    }

    /**
     * The mode used to communicate with a remote gRPC server.
     * In SIMPLE mode a single exchange is translated into a remote procedure call.
     * In STREAMING mode all exchanges will be sent within the same request (input and output of the recipient gRPC service must be of type 'stream').
     */
    public void setProducerStrategy(GrpcProducerStrategy producerStrategy) {
        this.producerStrategy = producerStrategy;
    }

    public String getStreamRepliesTo() {
        return streamRepliesTo;
    }

    /**
     * When using STREAMING client mode, it indicates the endpoint where responses should be forwarded.
     */
    public void setStreamRepliesTo(String streamRepliesTo) {
        this.streamRepliesTo = streamRepliesTo;
    }
    
    /**
     * The user agent header passed to the server
     */    
    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    /**
     * The HTTP/2 flow control window size (MiB)
     */
    public int getFlowControlWindow() {
        return flowControlWindow;
    }

    public void setFlowControlWindow(int flowControlWindow) {
        this.flowControlWindow = flowControlWindow;
    }

    /**
     * The maximum message size allowed to be received/sent (MiB)
     */
    public void setMaxMessageSize(int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }
    
    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    /**
     * The maximum number of concurrent calls permitted for each incoming server connection
     */
    public void setMaxConcurrentCallsPerConnection(int maxConcurrentCallsPerConnection) {
        this.maxConcurrentCallsPerConnection = maxConcurrentCallsPerConnection;
    }
    
    public int getMaxConcurrentCallsPerConnection() {
        return maxConcurrentCallsPerConnection;
    }

    public void parseURI(URI uri, Map<String, Object> parameters, GrpcComponent component) {
        setHost(uri.getHost());
        
        if (uri.getPort() != -1) {
            setPort(uri.getPort());
        }
        
        setService(uri.getPath().substring(1));
    }    
}
