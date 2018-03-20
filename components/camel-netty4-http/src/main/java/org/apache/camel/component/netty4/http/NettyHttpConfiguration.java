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
package org.apache.camel.component.netty4.http;

import java.util.ArrayList;
import java.util.List;

import io.netty.channel.ChannelHandler;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.netty4.NettyConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

/**
 * Extended configuration for using HTTP with Netty.
 */
@UriParams
public class NettyHttpConfiguration extends NettyConfiguration {

    @UriPath(enums = "http,https") @Metadata(required = "true")
    private String protocol;
    @UriPath @Metadata(required = "true")
    private String host;
    @UriPath(name = "port")
    private int dummy;
    @UriPath
    private String path;
    @UriParam(label = "consumer,advanced")
    private boolean urlDecodeHeaders;
    @UriParam(label = "consumer,advanced", defaultValue = "true")
    private boolean mapHeaders = true;
    @UriParam(label = "consumer,advanced")
    private boolean compression;
    @UriParam(label = "producer", defaultValue = "true")
    private boolean throwExceptionOnFailure = true;
    @UriParam(label = "advanced")
    private boolean transferException;
    @UriParam(label = "consumer")
    private boolean matchOnUriPrefix;
    @UriParam
    private boolean bridgeEndpoint;
    @UriParam(label = "advanced")
    private boolean disableStreamCache;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean send503whenSuspended = true;
    @UriParam(label = "consumer,advanced", defaultValue = "" + 1024 * 1024)
    private int chunkedMaxContentLength = 1024 * 1024;
    @UriParam(label = "consumer,advanced", defaultValue = "8192")
    private int maxHeaderSize = 8192;
    @UriParam(label = "producer,advanced", defaultValue = "200-299")
    private String okStatusCodeRange = "200-299";
    @UriParam(label = "producer,advanced")
    private boolean useRelativePath;
    
    public NettyHttpConfiguration() {
        // we need sync=true as http is request/reply by nature
        setSync(true);
        setReuseAddress(true);
        setServerInitializerFactory(new HttpServerInitializerFactory());
        setClientInitializerFactory(new HttpClientInitializerFactory());
    }

    @Override
    public NettyHttpConfiguration copy() {
        try {
            // clone as NettyHttpConfiguration
            NettyHttpConfiguration answer = (NettyHttpConfiguration) clone();
            // make sure the lists is copied in its own instance
            List<ChannelHandler> encodersCopy = new ArrayList<ChannelHandler>(getEncoders());
            answer.setEncoders(encodersCopy);
            List<ChannelHandler> decodersCopy = new ArrayList<ChannelHandler>(getDecoders());
            answer.setDecoders(decodersCopy);
            return answer;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public String getProtocol() {
        return protocol;
    }

    /**
     * The protocol to use which is either http or https
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    @Override
    public String getHost() {
        // override to setup better documentation for netty-http
        return super.getHost();
    }

    /**
     * The local hostname such as localhost, or 0.0.0.0 when being a consumer.
     * The remote HTTP server hostname when using producer.
     */
    @Override
    public void setHost(String host) {
        // override to setup better documentation for netty-http
        super.setHost(host);
    }

    @Override
    public int getPort() {
        // override to setup better documentation for netty-http
        return super.getPort();
    }

    /**
     * The port number. Is default 80 for http and 443 for https.
     */
    @Override
    public void setPort(int port) {
        // override to setup better documentation for netty-http
        super.setPort(port);
    }

    public boolean isCompression() {
        return compression;
    }

    /**
     * Allow using gzip/deflate for compression on the Netty HTTP server if the client supports it from the HTTP headers.
     */
    public void setCompression(boolean compression) {
        this.compression = compression;
    }

    public boolean isThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    /**
     * Option to disable throwing the HttpOperationFailedException in case of failed responses from the remote server.
     * This allows you to get all responses regardless of the HTTP status code.
     */
    public void setThrowExceptionOnFailure(boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    public boolean isTransferException() {
        return transferException;
    }

    /**
     * If enabled and an Exchange failed processing on the consumer side, and if the caused Exception was send back serialized
     * in the response as a application/x-java-serialized-object content type.
     * On the producer side the exception will be deserialized and thrown as is, instead of the HttpOperationFailedException.
     * The caused exception is required to be serialized.
     * <p/>
     * This is by default turned off. If you enable this then be aware that Java will deserialize the incoming
     * data from the request to Java and that can be a potential security risk.
     */
    public void setTransferException(boolean transferException) {
        this.transferException = transferException;
    }

    public boolean isUrlDecodeHeaders() {
        return urlDecodeHeaders;
    }

    /**
     * If this option is enabled, then during binding from Netty to Camel Message then the header values will be URL decoded
     * (eg %20 will be a space character. Notice this option is used by the default org.apache.camel.component.netty.http.NettyHttpBinding
     * and therefore if you implement a custom org.apache.camel.component.netty4.http.NettyHttpBinding then you would
     * need to decode the headers accordingly to this option.
     */
    public void setUrlDecodeHeaders(boolean urlDecodeHeaders) {
        this.urlDecodeHeaders = urlDecodeHeaders;
    }

    public boolean isMapHeaders() {
        return mapHeaders;
    }

    /**
     * If this option is enabled, then during binding from Netty to Camel Message then the headers will be mapped as well
     * (eg added as header to the Camel Message as well). You can turn off this option to disable this.
     * The headers can still be accessed from the org.apache.camel.component.netty.http.NettyHttpMessage message with
     * the method getHttpRequest() that returns the Netty HTTP request io.netty.handler.codec.http.HttpRequest instance.
     */
    public void setMapHeaders(boolean mapHeaders) {
        this.mapHeaders = mapHeaders;
    }

    public boolean isMatchOnUriPrefix() {
        return matchOnUriPrefix;
    }

    /**
     * Whether or not Camel should try to find a target consumer by matching the URI prefix if no exact match is found.
     */
    public void setMatchOnUriPrefix(boolean matchOnUriPrefix) {
        this.matchOnUriPrefix = matchOnUriPrefix;
    }

    public boolean isBridgeEndpoint() {
        return bridgeEndpoint;
    }

    /**
     * If the option is true, the producer will ignore the Exchange.HTTP_URI header, and use the endpoint's URI for request.
     * You may also set the throwExceptionOnFailure to be false to let the producer send all the fault response back.
     * The consumer working in the bridge mode will skip the gzip compression and WWW URL form encoding (by adding the Exchange.SKIP_GZIP_ENCODING
     * and Exchange.SKIP_WWW_FORM_URLENCODED headers to the consumed exchange).
     */
    public void setBridgeEndpoint(boolean bridgeEndpoint) {
        this.bridgeEndpoint = bridgeEndpoint;
    }

    public String getPath() {
        return path;
    }

    /**
     * Resource path
     */
    public void setPath(String path) {
        this.path = path;
    }

    public boolean isDisableStreamCache() {
        return disableStreamCache;
    }

    /**
     * Determines whether or not the raw input stream from Netty HttpRequest#getContent() or HttpResponset#getContent()
     * is cached or not (Camel will read the stream into a in light-weight memory based Stream caching) cache.
     * By default Camel will cache the Netty input stream to support reading it multiple times to ensure it Camel
     * can retrieve all data from the stream. However you can set this option to true when you for example need to
     * access the raw stream, such as streaming it directly to a file or other persistent store. Mind that
     * if you enable this option, then you cannot read the Netty stream multiple times out of the box, and you would
     * need manually to reset the reader index on the Netty raw stream. Also Netty will auto-close the Netty stream
     * when the Netty HTTP server/HTTP client is done processing, which means that if the asynchronous routing engine is in
     * use then any asynchronous thread that may continue routing the {@link org.apache.camel.Exchange} may not
     * be able to read the Netty stream, because Netty has closed it.
     */
    public void setDisableStreamCache(boolean disableStreamCache) {
        this.disableStreamCache = disableStreamCache;
    }

    public boolean isSend503whenSuspended() {
        return send503whenSuspended;
    }

    /**
     * Whether to send back HTTP status code 503 when the consumer has been suspended.
     * If the option is false then the Netty Acceptor is unbound when the consumer is suspended, so clients cannot connect anymore.
     */
    public void setSend503whenSuspended(boolean send503whenSuspended) {
        this.send503whenSuspended = send503whenSuspended;
    }

    public int getChunkedMaxContentLength() {
        return chunkedMaxContentLength;
    }

    /**
     * Value in bytes the max content length per chunked frame received on the Netty HTTP server.
     */
    public void setChunkedMaxContentLength(int chunkedMaxContentLength) {
        this.chunkedMaxContentLength = chunkedMaxContentLength;
    }

    public int getMaxHeaderSize() {
        return maxHeaderSize;
    }

    /**
     * The maximum length of all headers.
     * If the sum of the length of each header exceeds this value, a {@link io.netty.handler.codec.TooLongFrameException} will be raised.
     */
    public void setMaxHeaderSize(int maxHeaderSize) {
        this.maxHeaderSize = maxHeaderSize;
    }

    // Don't support allowDefaultCodec
    public boolean isAllowDefaultCodec() {
        return false;
    }
    
    public void setAllowDefaultCodec(boolean allowDefaultCodec) {
        throw new UnsupportedOperationException("You cannot setAllowDefaultCodec here.");
    }

    public String getOkStatusCodeRange() {
        return okStatusCodeRange;
    }

    /**
     * The status codes which are considered a success response. The values are inclusive. Multiple ranges can be
     * defined, separated by comma, e.g. <tt>200-204,209,301-304</tt>. Each range must be a single number or from-to with the
     * dash included.
     * <p/>
     * The default range is <tt>200-299</tt>
     */
    public void setOkStatusCodeRange(String okStatusCodeRange) {
        this.okStatusCodeRange = okStatusCodeRange;
    }

    /**
     * Sets whether to use a relative path in HTTP requests.
     */
    public void setUseRelativePath(boolean useRelativePath) {
        this.useRelativePath = useRelativePath;
    }

    public boolean isUseRelativePath() {
        return this.useRelativePath;        
    }
}
