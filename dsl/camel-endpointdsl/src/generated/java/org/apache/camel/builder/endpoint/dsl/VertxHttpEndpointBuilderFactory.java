/* Generated by camel build tools - do NOT edit this file! */
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
package org.apache.camel.builder.endpoint.dsl;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.*;
import javax.annotation.processing.Generated;
import org.apache.camel.builder.EndpointConsumerBuilder;
import org.apache.camel.builder.EndpointProducerBuilder;
import org.apache.camel.builder.endpoint.AbstractEndpointBuilder;

/**
 * Send requests to external HTTP servers using Vert.x
 * 
 * Generated by camel build tools - do NOT edit this file!
 */
@Generated("org.apache.camel.maven.packaging.EndpointDslMojo")
public interface VertxHttpEndpointBuilderFactory {

    /**
     * Builder for endpoint for the Vert.x HTTP Client component.
     */
    public interface VertxHttpEndpointBuilder
            extends
                EndpointProducerBuilder {
        default AdvancedVertxHttpEndpointBuilder advanced() {
            return (AdvancedVertxHttpEndpointBuilder) this;
        }

        /**
         * If the option is true, the Exchange.HTTP_URI header will be ignored
         * and the endpoint URI will be used for the HTTP request. You may also
         * set option throwExceptionOnFailure to false to return the fault
         * response back to the client.
         * 
         * The option is a: <code>boolean</code> type.
         * 
         * Default: false
         * Group: producer
         * 
         * @param bridgeEndpoint the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder bridgeEndpoint(boolean bridgeEndpoint) {
            doSetProperty("bridgeEndpoint", bridgeEndpoint);
            return this;
        }
        /**
         * If the option is true, the Exchange.HTTP_URI header will be ignored
         * and the endpoint URI will be used for the HTTP request. You may also
         * set option throwExceptionOnFailure to false to return the fault
         * response back to the client.
         * 
         * The option will be converted to a <code>boolean</code> type.
         * 
         * Default: false
         * Group: producer
         * 
         * @param bridgeEndpoint the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder bridgeEndpoint(String bridgeEndpoint) {
            doSetProperty("bridgeEndpoint", bridgeEndpoint);
            return this;
        }
        /**
         * The amount of time in milliseconds until a connection is established.
         * A timeout value of zero is interpreted as an infinite timeout.
         * 
         * The option is a: <code>int</code> type.
         * 
         * Default: 60000
         * Group: producer
         * 
         * @param connectTimeout the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder connectTimeout(int connectTimeout) {
            doSetProperty("connectTimeout", connectTimeout);
            return this;
        }
        /**
         * The amount of time in milliseconds until a connection is established.
         * A timeout value of zero is interpreted as an infinite timeout.
         * 
         * The option will be converted to a <code>int</code> type.
         * 
         * Default: 60000
         * Group: producer
         * 
         * @param connectTimeout the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder connectTimeout(String connectTimeout) {
            doSetProperty("connectTimeout", connectTimeout);
            return this;
        }
        /**
         * A custom CookieStore to use when session management is enabled. If
         * this option is not set then an in-memory CookieStore is used.
         * 
         * The option is a: <code>io.vertx.ext.web.client.spi.CookieStore</code>
         * type.
         * 
         * Default: InMemoryCookieStore
         * Group: producer
         * 
         * @param cookieStore the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder cookieStore(io.vertx.ext.web.client.spi.CookieStore cookieStore) {
            doSetProperty("cookieStore", cookieStore);
            return this;
        }
        /**
         * A custom CookieStore to use when session management is enabled. If
         * this option is not set then an in-memory CookieStore is used.
         * 
         * The option will be converted to a
         * <code>io.vertx.ext.web.client.spi.CookieStore</code> type.
         * 
         * Default: InMemoryCookieStore
         * Group: producer
         * 
         * @param cookieStore the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder cookieStore(String cookieStore) {
            doSetProperty("cookieStore", cookieStore);
            return this;
        }
        /**
         * A custom org.apache.camel.spi.HeaderFilterStrategy to filter header
         * to and from Camel message.
         * 
         * The option is a:
         * <code>org.apache.camel.spi.HeaderFilterStrategy</code> type.
         * 
         * Default: VertxHttpHeaderFilterStrategy
         * Group: producer
         * 
         * @param headerFilterStrategy the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder headerFilterStrategy(org.apache.camel.spi.HeaderFilterStrategy headerFilterStrategy) {
            doSetProperty("headerFilterStrategy", headerFilterStrategy);
            return this;
        }
        /**
         * A custom org.apache.camel.spi.HeaderFilterStrategy to filter header
         * to and from Camel message.
         * 
         * The option will be converted to a
         * <code>org.apache.camel.spi.HeaderFilterStrategy</code> type.
         * 
         * Default: VertxHttpHeaderFilterStrategy
         * Group: producer
         * 
         * @param headerFilterStrategy the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder headerFilterStrategy(String headerFilterStrategy) {
            doSetProperty("headerFilterStrategy", headerFilterStrategy);
            return this;
        }
        /**
         * The HTTP method to use. The HttpMethod header cannot override this
         * option if set.
         * 
         * The option is a: <code>io.vertx.core.http.HttpMethod</code> type.
         * 
         * Group: producer
         * 
         * @param httpMethod the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder httpMethod(io.vertx.core.http.HttpMethod httpMethod) {
            doSetProperty("httpMethod", httpMethod);
            return this;
        }
        /**
         * The HTTP method to use. The HttpMethod header cannot override this
         * option if set.
         * 
         * The option will be converted to a
         * <code>io.vertx.core.http.HttpMethod</code> type.
         * 
         * Group: producer
         * 
         * @param httpMethod the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder httpMethod(String httpMethod) {
            doSetProperty("httpMethod", httpMethod);
            return this;
        }
        /**
         * Whether to force using multipart/form-data for easy file uploads.
         * This is only to be used for uploading the message body as a single
         * entity form-data. For uploading multiple entries then use
         * io.vertx.ext.web.multipart.MultipartForm to build the form.
         * 
         * The option is a: <code>boolean</code> type.
         * 
         * Default: false
         * Group: producer
         * 
         * @param multipartUpload the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder multipartUpload(boolean multipartUpload) {
            doSetProperty("multipartUpload", multipartUpload);
            return this;
        }
        /**
         * Whether to force using multipart/form-data for easy file uploads.
         * This is only to be used for uploading the message body as a single
         * entity form-data. For uploading multiple entries then use
         * io.vertx.ext.web.multipart.MultipartForm to build the form.
         * 
         * The option will be converted to a <code>boolean</code> type.
         * 
         * Default: false
         * Group: producer
         * 
         * @param multipartUpload the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder multipartUpload(String multipartUpload) {
            doSetProperty("multipartUpload", multipartUpload);
            return this;
        }
        /**
         * The name of the multipart/form-data when multipartUpload is enabled.
         * 
         * The option is a: <code>java.lang.String</code> type.
         * 
         * Default: data
         * Group: producer
         * 
         * @param multipartUploadName the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder multipartUploadName(String multipartUploadName) {
            doSetProperty("multipartUploadName", multipartUploadName);
            return this;
        }
        /**
         * The status codes which are considered a success response. The values
         * are inclusive. Multiple ranges can be defined, separated by comma,
         * e.g. 200-204,209,301-304. Each range must be a single number or
         * from-to with the dash included.
         * 
         * The option is a: <code>java.lang.String</code> type.
         * 
         * Default: 200-299
         * Group: producer
         * 
         * @param okStatusCodeRange the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder okStatusCodeRange(String okStatusCodeRange) {
            doSetProperty("okStatusCodeRange", okStatusCodeRange);
            return this;
        }
        /**
         * Whether the response body should be byte or as
         * io.vertx.core.buffer.Buffer.
         * 
         * The option is a: <code>boolean</code> type.
         * 
         * Default: true
         * Group: producer
         * 
         * @param responsePayloadAsByteArray the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder responsePayloadAsByteArray(boolean responsePayloadAsByteArray) {
            doSetProperty("responsePayloadAsByteArray", responsePayloadAsByteArray);
            return this;
        }
        /**
         * Whether the response body should be byte or as
         * io.vertx.core.buffer.Buffer.
         * 
         * The option will be converted to a <code>boolean</code> type.
         * 
         * Default: true
         * Group: producer
         * 
         * @param responsePayloadAsByteArray the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder responsePayloadAsByteArray(String responsePayloadAsByteArray) {
            doSetProperty("responsePayloadAsByteArray", responsePayloadAsByteArray);
            return this;
        }
        /**
         * Enables session management via WebClientSession. By default the
         * client is configured to use an in-memory CookieStore. The cookieStore
         * option can be used to override this.
         * 
         * The option is a: <code>boolean</code> type.
         * 
         * Default: false
         * Group: producer
         * 
         * @param sessionManagement the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder sessionManagement(boolean sessionManagement) {
            doSetProperty("sessionManagement", sessionManagement);
            return this;
        }
        /**
         * Enables session management via WebClientSession. By default the
         * client is configured to use an in-memory CookieStore. The cookieStore
         * option can be used to override this.
         * 
         * The option will be converted to a <code>boolean</code> type.
         * 
         * Default: false
         * Group: producer
         * 
         * @param sessionManagement the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder sessionManagement(String sessionManagement) {
            doSetProperty("sessionManagement", sessionManagement);
            return this;
        }
        /**
         * Disable throwing HttpOperationFailedException in case of failed
         * responses from the remote server.
         * 
         * The option is a: <code>boolean</code> type.
         * 
         * Default: true
         * Group: producer
         * 
         * @param throwExceptionOnFailure the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder throwExceptionOnFailure(boolean throwExceptionOnFailure) {
            doSetProperty("throwExceptionOnFailure", throwExceptionOnFailure);
            return this;
        }
        /**
         * Disable throwing HttpOperationFailedException in case of failed
         * responses from the remote server.
         * 
         * The option will be converted to a <code>boolean</code> type.
         * 
         * Default: true
         * Group: producer
         * 
         * @param throwExceptionOnFailure the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder throwExceptionOnFailure(String throwExceptionOnFailure) {
            doSetProperty("throwExceptionOnFailure", throwExceptionOnFailure);
            return this;
        }
        /**
         * The amount of time in milliseconds after which if the request does
         * not return any data within the timeout period a TimeoutException
         * fails the request. Setting zero or a negative value disables the
         * timeout.
         * 
         * The option is a: <code>long</code> type.
         * 
         * Default: -1
         * Group: producer
         * 
         * @param timeout the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder timeout(long timeout) {
            doSetProperty("timeout", timeout);
            return this;
        }
        /**
         * The amount of time in milliseconds after which if the request does
         * not return any data within the timeout period a TimeoutException
         * fails the request. Setting zero or a negative value disables the
         * timeout.
         * 
         * The option will be converted to a <code>long</code> type.
         * 
         * Default: -1
         * Group: producer
         * 
         * @param timeout the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder timeout(String timeout) {
            doSetProperty("timeout", timeout);
            return this;
        }
        /**
         * If enabled and an Exchange failed processing on the consumer side,
         * and if the caused Exception was sent back serialized in the response
         * as a application/x-java-serialized-object content type. On the
         * producer side the exception will be deserialized and thrown as is,
         * instead of HttpOperationFailedException. The caused exception is
         * required to be serialized. This is by default turned off. If you
         * enable this then be aware that Camel will deserialize the incoming
         * data from the request to a Java object, which can be a potential
         * security risk.
         * 
         * The option is a: <code>boolean</code> type.
         * 
         * Default: false
         * Group: producer
         * 
         * @param transferException the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder transferException(boolean transferException) {
            doSetProperty("transferException", transferException);
            return this;
        }
        /**
         * If enabled and an Exchange failed processing on the consumer side,
         * and if the caused Exception was sent back serialized in the response
         * as a application/x-java-serialized-object content type. On the
         * producer side the exception will be deserialized and thrown as is,
         * instead of HttpOperationFailedException. The caused exception is
         * required to be serialized. This is by default turned off. If you
         * enable this then be aware that Camel will deserialize the incoming
         * data from the request to a Java object, which can be a potential
         * security risk.
         * 
         * The option will be converted to a <code>boolean</code> type.
         * 
         * Default: false
         * Group: producer
         * 
         * @param transferException the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder transferException(String transferException) {
            doSetProperty("transferException", transferException);
            return this;
        }
        /**
         * Set whether compression is enabled to handled compressed (E.g
         * gzipped) responses.
         * 
         * The option is a: <code>boolean</code> type.
         * 
         * Default: false
         * Group: producer
         * 
         * @param useCompression the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder useCompression(boolean useCompression) {
            doSetProperty("useCompression", useCompression);
            return this;
        }
        /**
         * Set whether compression is enabled to handled compressed (E.g
         * gzipped) responses.
         * 
         * The option will be converted to a <code>boolean</code> type.
         * 
         * Default: false
         * Group: producer
         * 
         * @param useCompression the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder useCompression(String useCompression) {
            doSetProperty("useCompression", useCompression);
            return this;
        }
        /**
         * A custom VertxHttpBinding which can control how to bind between
         * Vert.x and Camel.
         * 
         * The option is a:
         * <code>org.apache.camel.component.vertx.http.VertxHttpBinding</code>
         * type.
         * 
         * Group: producer
         * 
         * @param vertxHttpBinding the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder vertxHttpBinding(org.apache.camel.component.vertx.http.VertxHttpBinding vertxHttpBinding) {
            doSetProperty("vertxHttpBinding", vertxHttpBinding);
            return this;
        }
        /**
         * A custom VertxHttpBinding which can control how to bind between
         * Vert.x and Camel.
         * 
         * The option will be converted to a
         * <code>org.apache.camel.component.vertx.http.VertxHttpBinding</code>
         * type.
         * 
         * Group: producer
         * 
         * @param vertxHttpBinding the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder vertxHttpBinding(String vertxHttpBinding) {
            doSetProperty("vertxHttpBinding", vertxHttpBinding);
            return this;
        }
        /**
         * Sets customized options for configuring the Vert.x WebClient.
         * 
         * The option is a:
         * <code>io.vertx.ext.web.client.WebClientOptions</code> type.
         * 
         * Group: producer
         * 
         * @param webClientOptions the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder webClientOptions(io.vertx.ext.web.client.WebClientOptions webClientOptions) {
            doSetProperty("webClientOptions", webClientOptions);
            return this;
        }
        /**
         * Sets customized options for configuring the Vert.x WebClient.
         * 
         * The option will be converted to a
         * <code>io.vertx.ext.web.client.WebClientOptions</code> type.
         * 
         * Group: producer
         * 
         * @param webClientOptions the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder webClientOptions(String webClientOptions) {
            doSetProperty("webClientOptions", webClientOptions);
            return this;
        }
        /**
         * The proxy server host address.
         * 
         * The option is a: <code>java.lang.String</code> type.
         * 
         * Group: proxy
         * 
         * @param proxyHost the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder proxyHost(String proxyHost) {
            doSetProperty("proxyHost", proxyHost);
            return this;
        }
        /**
         * The proxy server password if authentication is required.
         * 
         * The option is a: <code>java.lang.String</code> type.
         * 
         * Group: proxy
         * 
         * @param proxyPassword the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder proxyPassword(String proxyPassword) {
            doSetProperty("proxyPassword", proxyPassword);
            return this;
        }
        /**
         * The proxy server port.
         * 
         * The option is a: <code>java.lang.Integer</code> type.
         * 
         * Group: proxy
         * 
         * @param proxyPort the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder proxyPort(Integer proxyPort) {
            doSetProperty("proxyPort", proxyPort);
            return this;
        }
        /**
         * The proxy server port.
         * 
         * The option will be converted to a <code>java.lang.Integer</code>
         * type.
         * 
         * Group: proxy
         * 
         * @param proxyPort the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder proxyPort(String proxyPort) {
            doSetProperty("proxyPort", proxyPort);
            return this;
        }
        /**
         * The proxy server type.
         * 
         * The option is a: <code>io.vertx.core.net.ProxyType</code> type.
         * 
         * Group: proxy
         * 
         * @param proxyType the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder proxyType(io.vertx.core.net.ProxyType proxyType) {
            doSetProperty("proxyType", proxyType);
            return this;
        }
        /**
         * The proxy server type.
         * 
         * The option will be converted to a
         * <code>io.vertx.core.net.ProxyType</code> type.
         * 
         * Group: proxy
         * 
         * @param proxyType the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder proxyType(String proxyType) {
            doSetProperty("proxyType", proxyType);
            return this;
        }
        /**
         * The proxy server username if authentication is required.
         * 
         * The option is a: <code>java.lang.String</code> type.
         * 
         * Group: proxy
         * 
         * @param proxyUsername the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder proxyUsername(String proxyUsername) {
            doSetProperty("proxyUsername", proxyUsername);
            return this;
        }
        /**
         * The password to use for basic authentication.
         * 
         * The option is a: <code>java.lang.String</code> type.
         * 
         * Group: security
         * 
         * @param basicAuthPassword the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder basicAuthPassword(String basicAuthPassword) {
            doSetProperty("basicAuthPassword", basicAuthPassword);
            return this;
        }
        /**
         * The user name to use for basic authentication.
         * 
         * The option is a: <code>java.lang.String</code> type.
         * 
         * Group: security
         * 
         * @param basicAuthUsername the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder basicAuthUsername(String basicAuthUsername) {
            doSetProperty("basicAuthUsername", basicAuthUsername);
            return this;
        }
        /**
         * The bearer token to use for bearer token authentication.
         * 
         * The option is a: <code>java.lang.String</code> type.
         * 
         * Group: security
         * 
         * @param bearerToken the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder bearerToken(String bearerToken) {
            doSetProperty("bearerToken", bearerToken);
            return this;
        }
        /**
         * To configure security using SSLContextParameters.
         * 
         * The option is a:
         * <code>org.apache.camel.support.jsse.SSLContextParameters</code> type.
         * 
         * Group: security
         * 
         * @param sslContextParameters the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder sslContextParameters(org.apache.camel.support.jsse.SSLContextParameters sslContextParameters) {
            doSetProperty("sslContextParameters", sslContextParameters);
            return this;
        }
        /**
         * To configure security using SSLContextParameters.
         * 
         * The option will be converted to a
         * <code>org.apache.camel.support.jsse.SSLContextParameters</code> type.
         * 
         * Group: security
         * 
         * @param sslContextParameters the value to set
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder sslContextParameters(String sslContextParameters) {
            doSetProperty("sslContextParameters", sslContextParameters);
            return this;
        }
    }

    /**
     * Advanced builder for endpoint for the Vert.x HTTP Client component.
     */
    public interface AdvancedVertxHttpEndpointBuilder
            extends
                EndpointProducerBuilder {
        default VertxHttpEndpointBuilder basic() {
            return (VertxHttpEndpointBuilder) this;
        }

        /**
         * Whether the producer should be started lazy (on the first message).
         * By starting lazy you can use this to allow CamelContext and routes to
         * startup in situations where a producer may otherwise fail during
         * starting and cause the route to fail being started. By deferring this
         * startup to be lazy then the startup failure can be handled during
         * routing messages via Camel's routing error handlers. Beware that when
         * the first message is processed then creating and starting the
         * producer may take a little time and prolong the total processing time
         * of the processing.
         * 
         * The option is a: <code>boolean</code> type.
         * 
         * Default: false
         * Group: producer (advanced)
         * 
         * @param lazyStartProducer the value to set
         * @return the dsl builder
         */
        default AdvancedVertxHttpEndpointBuilder lazyStartProducer(boolean lazyStartProducer) {
            doSetProperty("lazyStartProducer", lazyStartProducer);
            return this;
        }
        /**
         * Whether the producer should be started lazy (on the first message).
         * By starting lazy you can use this to allow CamelContext and routes to
         * startup in situations where a producer may otherwise fail during
         * starting and cause the route to fail being started. By deferring this
         * startup to be lazy then the startup failure can be handled during
         * routing messages via Camel's routing error handlers. Beware that when
         * the first message is processed then creating and starting the
         * producer may take a little time and prolong the total processing time
         * of the processing.
         * 
         * The option will be converted to a <code>boolean</code> type.
         * 
         * Default: false
         * Group: producer (advanced)
         * 
         * @param lazyStartProducer the value to set
         * @return the dsl builder
         */
        default AdvancedVertxHttpEndpointBuilder lazyStartProducer(String lazyStartProducer) {
            doSetProperty("lazyStartProducer", lazyStartProducer);
            return this;
        }
    }

    public interface VertxHttpBuilders {
        /**
         * Vert.x HTTP Client (camel-vertx-http)
         * Send requests to external HTTP servers using Vert.x
         * 
         * Category: http
         * Since: 3.5
         * Maven coordinates: org.apache.camel:camel-vertx-http
         * 
         * @return the dsl builder for the headers' name.
         */
        default VertxHttpHeaderNameBuilder vertxHttp() {
            return VertxHttpHeaderNameBuilder.INSTANCE;
        }
        /**
         * Vert.x HTTP Client (camel-vertx-http)
         * Send requests to external HTTP servers using Vert.x
         * 
         * Category: http
         * Since: 3.5
         * Maven coordinates: org.apache.camel:camel-vertx-http
         * 
         * Syntax: <code>vertx-http:httpUri</code>
         * 
         * Path parameter: httpUri (required)
         * The HTTP URI to connect to
         * 
         * @param path httpUri
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder vertxHttp(String path) {
            return VertxHttpEndpointBuilderFactory.endpointBuilder("vertx-http", path);
        }
        /**
         * Vert.x HTTP Client (camel-vertx-http)
         * Send requests to external HTTP servers using Vert.x
         * 
         * Category: http
         * Since: 3.5
         * Maven coordinates: org.apache.camel:camel-vertx-http
         * 
         * Syntax: <code>vertx-http:httpUri</code>
         * 
         * Path parameter: httpUri (required)
         * The HTTP URI to connect to
         * 
         * @param componentName to use a custom component name for the endpoint
         * instead of the default name
         * @param path httpUri
         * @return the dsl builder
         */
        default VertxHttpEndpointBuilder vertxHttp(String componentName, String path) {
            return VertxHttpEndpointBuilderFactory.endpointBuilder(componentName, path);
        }

    }
    /**
     * The builder of headers' name for the Vert.x HTTP Client component.
     */
    public static class VertxHttpHeaderNameBuilder {
        /**
         * The internal instance of the builder used to access to all the
         * methods representing the name of headers.
         */
        private static final VertxHttpHeaderNameBuilder INSTANCE = new VertxHttpHeaderNameBuilder();

        /**
         * The http method.
         * 
         * The option is a: {@code io.vertx.core.http.HttpMethod} type.
         * 
         * Group: producer
         * 
         * @return the name of the header {@code HttpMethod}.
         */
        public String httpMethod() {
            return "CamelHttpMethod";
        }
        /**
         * The HTTP response code from the external server.
         * 
         * The option is a: {@code Integer} type.
         * 
         * Group: producer
         * 
         * @return the name of the header {@code HttpResponseCode}.
         */
        public String httpResponseCode() {
            return "CamelHttpResponseCode";
        }
        /**
         * The HTTP response text from the external server.
         * 
         * The option is a: {@code String} type.
         * 
         * Group: producer
         * 
         * @return the name of the header {@code HttpResponseText}.
         */
        public String httpResponseText() {
            return "CamelHttpResponseText";
        }
        /**
         * The HTTP content type. Is set on both the IN and OUT message to
         * provide a content type, such as text/html.
         * 
         * The option is a: {@code String} type.
         * 
         * Group: producer
         * 
         * @return the name of the header {@code Content-Type}.
         */
        public String contentType() {
            return "Content-Type";
        }
        /**
         * URI parameters. Will override existing URI parameters set directly on
         * the endpoint.
         * 
         * The option is a: {@code String} type.
         * 
         * Group: producer
         * 
         * @return the name of the header {@code HttpQuery}.
         */
        public String httpQuery() {
            return "CamelHttpQuery";
        }
        /**
         * URI to call. Will override the existing URI set directly on the
         * endpoint. This URI is the URI of the http server to call. Its not the
         * same as the Camel endpoint URI, where you can configure endpoint
         * options such as security etc. This header does not support that, its
         * only the URI of the http server.
         * 
         * The option is a: {@code String} type.
         * 
         * Group: producer
         * 
         * @return the name of the header {@code HttpUri}.
         */
        public String httpUri() {
            return "CamelHttpUri";
        }
        /**
         * Request URI's path, the header will be used to build the request URI
         * with the HTTP_URI.
         * 
         * The option is a: {@code String} type.
         * 
         * Group: producer
         * 
         * @return the name of the header {@code HttpPath}.
         */
        public String httpPath() {
            return "CamelHttpPath";
        }
        /**
         * The HTTP content encoding. Is set to provide a content encoding, such
         * as gzip.
         * 
         * The option is a: {@code String} type.
         * 
         * Group: producer
         * 
         * @return the name of the header {@code Content-Encoding}.
         */
        public String contentEncoding() {
            return "Content-Encoding";
        }
    }
    static VertxHttpEndpointBuilder endpointBuilder(String componentName, String path) {
        class VertxHttpEndpointBuilderImpl extends AbstractEndpointBuilder implements VertxHttpEndpointBuilder, AdvancedVertxHttpEndpointBuilder {
            public VertxHttpEndpointBuilderImpl(String path) {
                super(componentName, path);
            }
        }
        return new VertxHttpEndpointBuilderImpl(path);
    }
}