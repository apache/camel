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
package org.apache.camel.component.grpc;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.grpc.ClientInterceptor;
import io.grpc.ServerInterceptor;
import io.grpc.internal.GrpcUtil;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import org.apache.camel.component.grpc.auth.jwt.JwtAlgorithm;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class GrpcConfiguration {

    @UriPath(description = "The host name or IP address. For the consumer, the address that the gRPC server binds to, such as"
                           + " localhost or 0.0.0.0. For the producer, the host of the remote gRPC server.")
    @Metadata(required = true)
    private String host;

    @UriPath(description = "The port. For the consumer, the port that the gRPC server listens on, where 0 lets the operating"
                           + " system choose a free port. For the producer, the port of the remote gRPC server.")
    @Metadata(required = true)
    private int port = -1;

    @UriPath(description = "The fully qualified name of the gRPC service: the Java package of the classes generated from the"
                           + " .proto file, a dot, and the service name, for example org.apache.camel.component.grpc.PingPong. The"
                           + " component loads the generated class of the service (PingPongGrpc in the example) by this name.")
    @Metadata(required = true)
    private String service;

    @UriParam(label = "producer",
              description = "The name of the method that the producer calls, as in the .proto file or as the Java method name."
                            + " The producer converts it to the lowerCamelCase name of the stub method, so SendPing and send_ping"
                            + " both call sendPing.")
    private String method;

    @UriParam(label = "security", defaultValue = "PLAINTEXT", enums = "PLAINTEXT,TLS",
              description = "The transport security of the HTTP/2 connection: PLAINTEXT for no encryption, or TLS. With TLS the"
                            + " consumer requires keyCertChainResource and keyResource, and a certificate from each client.")
    private NegotiationType negotiationType = NegotiationType.PLAINTEXT;

    @UriParam(label = "security", defaultValue = "NONE", enums = "NONE,GOOGLE,JWT",
              description = "The authentication of the calls, in addition to the transport security. GOOGLE makes the producer"
                            + " send the OAuth2 access tokens of the Google service account in serviceAccountResource, and requires"
                            + " negotiationType TLS. JWT makes the producer send a JSON Web Token signed with jwtSecret, and the"
                            + " consumer accept only calls with a valid token.")
    private GrpcAuthType authenticationType = GrpcAuthType.NONE;

    @UriParam(label = "security", defaultValue = "HMAC256",
              description = "The HMAC algorithm that signs the JSON Web Token on the producer and verifies it on the consumer.")
    private JwtAlgorithm jwtAlgorithm = JwtAlgorithm.HMAC256;

    @UriParam(label = "security", security = "secret",
              description = "The secret key of the HMAC algorithm that signs the JSON Web Token on the producer and verifies it"
                            + " on the consumer. Required when authenticationType is JWT.")
    private String jwtSecret;

    @UriParam(label = "security",
              description = "The issuer (iss claim) of the JSON Web Token. The producer puts it in the token, and the consumer"
                            + " accepts only tokens with the same issuer.")
    private String jwtIssuer;

    @UriParam(label = "security",
              description = "The subject (sub claim) of the JSON Web Token. The producer puts it in the token, and the consumer"
                            + " accepts only tokens with the same subject.")
    private String jwtSubject;

    @UriParam(label = "security",
              description = "The location of the Google Cloud service account key file in JSON format, for example"
                            + " classpath:service-account.json. Required when authenticationType is GOOGLE: the producer sends the"
                            + " OAuth2 access tokens of this account.")
    @Metadata(supportFileReference = true)
    private String serviceAccountResource;

    @UriParam(label = "security",
              description = "The location of the X.509 certificate chain in PEM format. For the consumer, the certificate of the"
                            + " server, required with TLS. For the producer, the certificate of the client for mutual TLS, set"
                            + " together with keyResource.")
    @Metadata(supportFileReference = true)
    private String keyCertChainResource;

    @UriParam(label = "security",
              description = "The location of the PKCS#8 private key in PEM format of keyCertChainResource. For the consumer, the"
                            + " key of the server, required with TLS. For the producer, the key of the client for mutual TLS, set"
                            + " together with keyCertChainResource.")
    @Metadata(supportFileReference = true)
    private String keyResource;

    @UriParam(label = "security", security = "secret",
              description = "The password of the private key in keyResource, if the key is encrypted.")
    private String keyPassword;

    @UriParam(label = "security",
              description = "The location of the trusted certificates in PEM format that verify the certificate of the other"
                            + " side: of the server for the producer, of the clients for the consumer. If not set, the producer"
                            + " verifies the server with the default trust store of the JVM.")
    @Metadata(supportFileReference = true)
    private String trustCertCollectionResource;

    @UriParam(label = "producer", defaultValue = "SIMPLE", enums = "SIMPLE,STREAMING",
              description = "How the producer calls the method. SIMPLE makes one call per exchange. STREAMING sends the body of"
                            + " each exchange as a message on one shared call, which needs a method with a stream request, and"
                            + " sends the responses to the streamRepliesTo endpoint.")
    private GrpcProducerStrategy producerStrategy = GrpcProducerStrategy.SIMPLE;

    @UriParam(label = "producer",
              description = "The URI of the endpoint that receives the responses of the server, one exchange per response"
                            + " message, with the CamelGrpcEventType header. Required with the STREAMING strategy. With the SIMPLE"
                            + " strategy the responses go to this endpoint instead of the body, unless synchronous is true.")
    private String streamRepliesTo;

    @UriParam(label = "producer",
              description = "The user agent that the producer sends to the server. gRPC Java adds its own user agent after this"
                            + " value.")
    private String userAgent;

    @UriParam(label = "consumer", defaultValue = "PROPAGATION", enums = "AGGREGATION,PROPAGATION,DELEGATION",
              description = "How the consumer handles client streaming and bidirectional streaming calls; unary and server"
                            + " streaming calls always give one exchange per call. AGGREGATION routes all request messages as one"
                            + " List when the client completes the stream, and sends the reply of the route. PROPAGATION routes"
                            + " each request message in its own exchange and sends its reply, unless routeControlledStreamObserver"
                            + " is true. DELEGATION routes each request message and sends nothing: it requires"
                            + " routeControlledStreamObserver.")
    private GrpcConsumerStrategy consumerStrategy = GrpcConsumerStrategy.PROPAGATION;

    @UriParam(label = "common", defaultValue = "false",
              description = "Whether to route an exchange without a body, with the CamelGrpcEventType header set to onCompleted,"
                            + " when a stream completes. The consumer does it for client and bidirectional streaming calls with the"
                            + " PROPAGATION or DELEGATION strategy, and the producer to the streamRepliesTo endpoint.")
    private boolean forwardOnCompleted;

    @UriParam(label = "common", defaultValue = "false",
              description = "Whether to route an exchange with the Throwable as body, and the CamelGrpcEventType header set to"
                            + " onError, when a stream fails. The consumer does it for client and bidirectional streaming calls"
                            + " with the PROPAGATION or DELEGATION strategy, and the producer to the streamRepliesTo endpoint.")
    private boolean forwardOnError;

    @UriParam(defaultValue = "" + NettyChannelBuilder.DEFAULT_FLOW_CONTROL_WINDOW,
              description = "The HTTP/2 flow control window size in bytes of the producer. The consumer uses"
                            + " initialFlowControlWindow instead.")
    private int flowControlWindow = NettyChannelBuilder.DEFAULT_FLOW_CONTROL_WINDOW;

    @UriParam(defaultValue = "" + GrpcUtil.DEFAULT_MAX_MESSAGE_SIZE,
              description = "The maximum size in bytes of a message that the consumer or the producer receives. A larger message"
                            + " fails the call with the RESOURCE_EXHAUSTED status.")
    private int maxMessageSize = GrpcUtil.DEFAULT_MAX_MESSAGE_SIZE;

    @UriParam(label = "consumer", defaultValue = "" + Integer.MAX_VALUE)
    private int maxConcurrentCallsPerConnection = Integer.MAX_VALUE;

    @UriParam(label = "consumer", defaultValue = "false",
              description = "Whether the route sends the responses instead of the consumer. The consumer sets the response"
                            + " StreamObserver of the call as the grpcResponseObserver exchange property, and the route replies"
                            + " with a producer with toRouteControlledStreamObserver=true. Not compatible with the AGGREGATION"
                            + " strategy.")
    private boolean routeControlledStreamObserver;

    @UriParam(label = "consumer", defaultValue = "true",
              description = "Whether the consumer leaves out the exception message from the status description that it sends to"
                            + " the client when the exchange fails. The description is then Exchange processing failed, and the"
                            + " status is INTERNAL in both cases.")
    private boolean muteException = true;

    private List<ServerInterceptor> serverInterceptors = Collections.emptyList();

    @UriParam(label = "consumer", defaultValue = "true",
              description = "Whether the consumer intercepts the incoming calls with all the io.grpc.ServerInterceptor beans of"
                            + " the registry.")
    private boolean autoDiscoverServerInterceptors = true;

    private List<ClientInterceptor> clientInterceptors = Collections.emptyList();

    @UriParam(label = "producer", defaultValue = "true",
              description = "Whether the producer intercepts the outgoing calls with all the io.grpc.ClientInterceptor beans of"
                            + " the registry.")
    private boolean autoDiscoverClientInterceptors = true;

    @UriParam(defaultValue = "false", label = "advanced",
              description = "Whether to process synchronously. The producer then calls the method with a blocking stub and waits"
                            + " for the response, which methods with a stream request do not support. The consumer then routes"
                            + " unary and server streaming calls synchronously.")
    private boolean synchronous;

    @UriParam(defaultValue = "false", label = "producer",
              description = "Whether the exchanges that the producer sends to the streamRepliesTo endpoint get a copy of the"
                            + " exchange properties of the exchange that made the call. For example, the replies then keep the"
                            + " grpcResponseObserver property for toRouteControlledStreamObserver.")
    private boolean inheritExchangePropertiesForReplies = false;

    @UriParam(defaultValue = "false", label = "producer",
              description = "Whether the producer replies to the client of a consumer with routeControlledStreamObserver=true,"
                            + " instead of calling a server. It sends the body to the StreamObserver in the grpcResponseObserver"
                            + " exchange property, as the event in the CamelGrpcEventType header. The other options of the producer"
                            + " are not used.")
    private boolean toRouteControlledStreamObserver = false;

    @UriParam(label = "consumer", defaultValue = "" + NettyServerBuilder.DEFAULT_FLOW_CONTROL_WINDOW,
              description = "The initial HTTP/2 flow control window size in bytes of the consumer. The consumer then adjusts the"
                            + " window to the bandwidth-delay product of the connection.")
    private int initialFlowControlWindow = NettyServerBuilder.DEFAULT_FLOW_CONTROL_WINDOW;

    @UriParam(label = "consumer", defaultValue = "7200000",
              description = "The time in milliseconds without received data after which the consumer sends a keepalive ping to"
                            + " the client. A value of 1000 days or more disables keepalive.")
    private long keepAliveTime = TimeUnit.NANOSECONDS.toMillis(GrpcUtil.DEFAULT_SERVER_KEEPALIVE_TIME_NANOS);

    @UriParam(label = "consumer", defaultValue = "20000",
              description = "The time in milliseconds that the consumer waits for the reply to a keepalive ping before it closes"
                            + " the connection.")
    private long keepAliveTimeout = TimeUnit.NANOSECONDS.toMillis(GrpcUtil.DEFAULT_SERVER_KEEPALIVE_TIMEOUT_NANOS);

    @UriParam(label = "consumer", defaultValue = "" + Long.MAX_VALUE,
              description = "The maximum time in milliseconds that a connection exists before the consumer closes it gracefully,"
                            + " with a random jitter of +/-10%. A value of 1000 days or more, such as the default, means no limit.")
    private long maxConnectionAge = Long.MAX_VALUE;

    @UriParam(label = "consumer", defaultValue = "" + Long.MAX_VALUE,
              description = "The time in milliseconds without outstanding calls after which the consumer closes a connection"
                            + " gracefully. A value of 1000 days or more, such as the default, means no limit.")
    private long maxConnectionIdle = Long.MAX_VALUE;

    @UriParam(label = "consumer", defaultValue = "" + Long.MAX_VALUE,
              description = "The time in milliseconds that the calls in progress have to complete once maxConnectionAge is"
                            + " reached, before the consumer cancels them and closes the connection. A value of 1000 days or more,"
                            + " such as the default, means no limit.")
    private long maxConnectionAgeGrace = Long.MAX_VALUE;

    @UriParam(label = "consumer", defaultValue = "" + GrpcUtil.DEFAULT_MAX_HEADER_LIST_SIZE,
              description = "The maximum size in bytes of the metadata (the headers) of a call that the consumer receives.")
    private int maxInboundMetadataSize = GrpcUtil.DEFAULT_MAX_HEADER_LIST_SIZE;

    @UriParam(label = "consumer", defaultValue = "0",
              description = "The maximum number of RST_STREAM frames that the consumer accepts from a connection per"
                            + " maxRstPeriodSeconds, before it closes the connection. Set it together with maxRstPeriodSeconds: the"
                            + " consumer fails to start if only one of them is greater than 0. 0 means no limit.")
    private int maxRstFramesPerWindow;

    @UriParam(label = "consumer", defaultValue = "0",
              description = "The period in seconds of maxRstFramesPerWindow. Set it together with maxRstFramesPerWindow: the"
                            + " consumer fails to start if only one of them is greater than 0. 0 means no limit.")
    private int maxRstPeriodSeconds;

    @UriParam(label = "consumer", defaultValue = "300000",
              description = "The shortest time in milliseconds between keepalive pings that the consumer permits a client. The"
                            + " consumer closes the connection of a client that sends pings more often.")
    private long permitKeepAliveTime = TimeUnit.MINUTES.toMillis(5);

    @UriParam(label = "consumer", defaultValue = "false",
              description = "Whether the consumer permits clients to send keepalive pings (HTTP/2 PING frames) on a connection"
                            + " without outstanding calls.")
    private boolean permitKeepAliveWithoutCalls;

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public NegotiationType getNegotiationType() {
        return negotiationType;
    }

    public void setNegotiationType(NegotiationType negotiationType) {
        this.negotiationType = negotiationType;
    }

    public GrpcAuthType getAuthenticationType() {
        return authenticationType;
    }

    public void setAuthenticationType(GrpcAuthType authenticationType) {
        this.authenticationType = authenticationType;
    }

    public JwtAlgorithm getJwtAlgorithm() {
        return jwtAlgorithm;
    }

    public void setJwtAlgorithm(JwtAlgorithm jwtAlgorithm) {
        this.jwtAlgorithm = jwtAlgorithm;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String getJwtIssuer() {
        return jwtIssuer;
    }

    public void setJwtIssuer(String jwtIssuer) {
        this.jwtIssuer = jwtIssuer;
    }

    public String getJwtSubject() {
        return jwtSubject;
    }

    public void setJwtSubject(String jwtSubject) {
        this.jwtSubject = jwtSubject;
    }

    public String getServiceAccountResource() {
        return serviceAccountResource;
    }

    public void setServiceAccountResource(String serviceAccountResource) {
        this.serviceAccountResource = serviceAccountResource;
    }

    public String getKeyCertChainResource() {
        return keyCertChainResource;
    }

    public void setKeyCertChainResource(String keyCertChainResource) {
        this.keyCertChainResource = keyCertChainResource;
    }

    public String getKeyResource() {
        return keyResource;
    }

    public void setKeyResource(String keyResource) {
        this.keyResource = keyResource;
    }

    public String getKeyPassword() {
        return keyPassword;
    }

    public void setKeyPassword(String keyPassword) {
        this.keyPassword = keyPassword;
    }

    public String getTrustCertCollectionResource() {
        return trustCertCollectionResource;
    }

    public void setTrustCertCollectionResource(String trustCertCollectionResource) {
        this.trustCertCollectionResource = trustCertCollectionResource;
    }

    public GrpcConsumerStrategy getConsumerStrategy() {
        return consumerStrategy;
    }

    public void setConsumerStrategy(GrpcConsumerStrategy consumerStrategy) {
        this.consumerStrategy = consumerStrategy;
    }

    public boolean isForwardOnCompleted() {
        return forwardOnCompleted;
    }

    public void setForwardOnCompleted(boolean forwardOnCompleted) {
        this.forwardOnCompleted = forwardOnCompleted;
    }

    public boolean isForwardOnError() {
        return forwardOnError;
    }

    public void setForwardOnError(boolean forwardOnError) {
        this.forwardOnError = forwardOnError;
    }

    public GrpcProducerStrategy getProducerStrategy() {
        return producerStrategy;
    }

    public void setProducerStrategy(GrpcProducerStrategy producerStrategy) {
        this.producerStrategy = producerStrategy;
    }

    public String getStreamRepliesTo() {
        return streamRepliesTo;
    }

    public void setStreamRepliesTo(String streamRepliesTo) {
        this.streamRepliesTo = streamRepliesTo;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public int getFlowControlWindow() {
        return flowControlWindow;
    }

    public void setFlowControlWindow(int flowControlWindow) {
        this.flowControlWindow = flowControlWindow;
    }

    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    public void setMaxMessageSize(int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }

    public boolean isRouteControlledStreamObserver() {
        return routeControlledStreamObserver;
    }

    public void setRouteControlledStreamObserver(boolean routeControlledStreamObserver) {
        this.routeControlledStreamObserver = routeControlledStreamObserver;
    }

    public boolean isMuteException() {
        return muteException;
    }

    public void setMuteException(boolean muteException) {
        this.muteException = muteException;
    }

    public int getMaxConcurrentCallsPerConnection() {
        return maxConcurrentCallsPerConnection;
    }

    /**
     * The maximum number of concurrent calls permitted for each incoming server connection. Defaults to no limit.
     */
    public void setMaxConcurrentCallsPerConnection(int maxConcurrentCallsPerConnection) {
        this.maxConcurrentCallsPerConnection = maxConcurrentCallsPerConnection;
    }

    public List<ServerInterceptor> getServerInterceptors() {
        return serverInterceptors;
    }

    /**
     * Setting the server interceptors on the netty channel in order to intercept incoming calls before they are
     * received by the server.
     */
    public void setServerInterceptors(List<ServerInterceptor> serverInterceptors) {
        this.serverInterceptors = serverInterceptors;
    }

    public boolean isAutoDiscoverServerInterceptors() {
        return autoDiscoverServerInterceptors;
    }

    public void setAutoDiscoverServerInterceptors(boolean autoDiscoverServerInterceptors) {
        this.autoDiscoverServerInterceptors = autoDiscoverServerInterceptors;
    }

    public List<ClientInterceptor> getClientInterceptors() {
        return clientInterceptors;
    }

    /**
     * Setting the client interceptors on the netty channel in order to intercept outgoing calls before they are
     * dispatched by the channel.
     */
    public void setClientInterceptors(List<ClientInterceptor> clientInterceptors) {
        this.clientInterceptors = clientInterceptors;
    }

    public boolean isAutoDiscoverClientInterceptors() {
        return autoDiscoverClientInterceptors;
    }

    public void setAutoDiscoverClientInterceptors(boolean autoDiscoverClientInterceptors) {
        this.autoDiscoverClientInterceptors = autoDiscoverClientInterceptors;
    }

    public boolean isSynchronous() {
        return synchronous;
    }

    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
    }

    public boolean isInheritExchangePropertiesForReplies() {
        return inheritExchangePropertiesForReplies;
    }

    public void setInheritExchangePropertiesForReplies(boolean inheritExchangePropertiesForReplies) {
        this.inheritExchangePropertiesForReplies = inheritExchangePropertiesForReplies;
    }

    public boolean isToRouteControlledStreamObserver() {
        return toRouteControlledStreamObserver;
    }

    public void setToRouteControlledStreamObserver(boolean toRouteControlledStreamObserver) {
        this.toRouteControlledStreamObserver = toRouteControlledStreamObserver;
    }

    public int getInitialFlowControlWindow() {
        return initialFlowControlWindow;
    }

    public void setInitialFlowControlWindow(int initialFlowControlWindow) {
        this.initialFlowControlWindow = initialFlowControlWindow;
    }

    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    public void setKeepAliveTime(long keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    public long getKeepAliveTimeout() {
        return keepAliveTimeout;
    }

    public void setKeepAliveTimeout(long keepAliveTimeout) {
        this.keepAliveTimeout = keepAliveTimeout;
    }

    public long getMaxConnectionAge() {
        return maxConnectionAge;
    }

    public void setMaxConnectionAge(long maxConnectionAge) {
        this.maxConnectionAge = maxConnectionAge;
    }

    public long getMaxConnectionIdle() {
        return maxConnectionIdle;
    }

    public void setMaxConnectionIdle(long maxConnectionIdle) {
        this.maxConnectionIdle = maxConnectionIdle;
    }

    public long getMaxConnectionAgeGrace() {
        return maxConnectionAgeGrace;
    }

    public void setMaxConnectionAgeGrace(long maxConnectionAgeGrace) {
        this.maxConnectionAgeGrace = maxConnectionAgeGrace;
    }

    public int getMaxInboundMetadataSize() {
        return maxInboundMetadataSize;
    }

    public void setMaxInboundMetadataSize(int maxInboundMetadataSize) {
        this.maxInboundMetadataSize = maxInboundMetadataSize;
    }

    public int getMaxRstFramesPerWindow() {
        return maxRstFramesPerWindow;
    }

    public void setMaxRstFramesPerWindow(int maxRstFramesPerWindow) {
        this.maxRstFramesPerWindow = maxRstFramesPerWindow;
    }

    public int getMaxRstPeriodSeconds() {
        return maxRstPeriodSeconds;
    }

    public void setMaxRstPeriodSeconds(int maxRstPeriodSeconds) {
        this.maxRstPeriodSeconds = maxRstPeriodSeconds;
    }

    public long getPermitKeepAliveTime() {
        return permitKeepAliveTime;
    }

    public void setPermitKeepAliveTime(long permitKeepAliveTime) {
        this.permitKeepAliveTime = permitKeepAliveTime;
    }

    public boolean isPermitKeepAliveWithoutCalls() {
        return permitKeepAliveWithoutCalls;
    }

    public void setPermitKeepAliveWithoutCalls(boolean permitKeepAliveWithoutCalls) {
        this.permitKeepAliveWithoutCalls = permitKeepAliveWithoutCalls;
    }

    public void parseURI(URI uri) {
        setHost(uri.getHost());

        if (uri.getPort() != -1) {
            setPort(uri.getPort());
        }

        setService(uri.getPath().substring(1));
    }
}
