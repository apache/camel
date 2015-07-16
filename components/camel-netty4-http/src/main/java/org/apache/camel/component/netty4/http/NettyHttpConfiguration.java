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
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

/**
 * Extended configuration for using HTTP with Netty.
 */
@UriParams
public class NettyHttpConfiguration extends NettyConfiguration {

    @UriPath
    private String path;
    @UriParam
    private boolean urlDecodeHeaders;
    @UriParam(defaultValue = "true")
    private boolean mapHeaders = true;
    @UriParam
    private boolean compression;
    @UriParam(defaultValue = "true")
    private boolean throwExceptionOnFailure = true;
    @UriParam
    private boolean transferException;
    @UriParam
    private boolean matchOnUriPrefix;
    @UriParam
    private boolean bridgeEndpoint;
    @UriParam
    private boolean disableStreamCache;
    @UriParam(defaultValue = "true")
    private boolean send503whenSuspended = true;
    @UriParam(defaultValue = "" + 1024 * 1024)
    private int chunkedMaxContentLength = 1024 * 1024;
    @UriParam(defaultValue = "true")
    private boolean chunked = true;
    @UriParam(defaultValue = "8192")
    private int maxHeaderSize = 8192;

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

    public boolean isCompression() {
        return compression;
    }

    public void setCompression(boolean compression) {
        this.compression = compression;
    }

    public boolean isThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    public void setThrowExceptionOnFailure(boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    public boolean isTransferException() {
        return transferException;
    }

    public void setTransferException(boolean transferException) {
        this.transferException = transferException;
    }

    public boolean isUrlDecodeHeaders() {
        return urlDecodeHeaders;
    }

    public void setUrlDecodeHeaders(boolean urlDecodeHeaders) {
        this.urlDecodeHeaders = urlDecodeHeaders;
    }

    public boolean isMapHeaders() {
        return mapHeaders;
    }

    public void setMapHeaders(boolean mapHeaders) {
        this.mapHeaders = mapHeaders;
    }

    public boolean isMatchOnUriPrefix() {
        return matchOnUriPrefix;
    }

    public void setMatchOnUriPrefix(boolean matchOnUriPrefix) {
        this.matchOnUriPrefix = matchOnUriPrefix;
    }

    public boolean isBridgeEndpoint() {
        return bridgeEndpoint;
    }

    public void setBridgeEndpoint(boolean bridgeEndpoint) {
        this.bridgeEndpoint = bridgeEndpoint;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isDisableStreamCache() {
        return disableStreamCache;
    }

    public void setDisableStreamCache(boolean disableStreamCache) {
        this.disableStreamCache = disableStreamCache;
    }

    public boolean isSend503whenSuspended() {
        return send503whenSuspended;
    }

    public void setSend503whenSuspended(boolean send503whenSuspended) {
        this.send503whenSuspended = send503whenSuspended;
    }

    public boolean isChunked() {
        return chunked;
    }

    public void setChunked(boolean chunked) {
        this.chunked = chunked;
    }

    public int getChunkedMaxContentLength() {
        return chunkedMaxContentLength;
    }

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

}
