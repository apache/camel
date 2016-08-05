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
package org.apache.camel.component.mina;

import java.nio.charset.Charset;
import java.util.List;

import org.apache.camel.LoggingLevel;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.mina.common.IoFilter;
import org.apache.mina.filter.codec.ProtocolCodecFactory;

/**
 * Mina configuration
 */
@UriParams
public class MinaConfiguration implements Cloneable {
    @UriPath(enums = "tcp,udp,vm") @Metadata(required = "true")
    private String protocol;
    @UriPath @Metadata(required = "true")
    private String host;
    @UriPath @Metadata(required = "true")
    private int port;
    @UriParam(defaultValue = "true")
    private boolean sync = true;
    @UriParam(label = "codec")
    private boolean textline;
    @UriParam(label = "codec")
    private TextLineDelimiter textlineDelimiter;
    @UriParam(label = "codec")
    private ProtocolCodecFactory codec;
    @UriParam(label = "codec")
    private String encoding;
    @UriParam(defaultValue = "30000")
    private long timeout = 30000;
    @UriParam(label = "producer,advanced", defaultValue = "true")
    private boolean lazySessionCreation = true;
    @UriParam(label = "advanced")
    private boolean transferExchange;
    @UriParam
    private boolean minaLogger;
    @UriParam(label = "codec", defaultValue = "-1")
    private int encoderMaxLineLength = -1;
    @UriParam(label = "codec", defaultValue = "1024")
    private int decoderMaxLineLength = 1024;
    @UriParam(label = "codec")
    private List<IoFilter> filters;
    @UriParam(label = "codec", defaultValue = "true")
    private boolean allowDefaultCodec = true;
    @UriParam
    private boolean disconnect;
    @UriParam(label = "consumer,advanced", defaultValue = "true")
    private boolean disconnectOnNoReply = true;
    @UriParam(label = "consumer,advanced", defaultValue = "WARN")
    private LoggingLevel noReplyLogLevel = LoggingLevel.WARN;
    @UriParam(label = "consumer")
    private boolean clientMode;

    /**
     * Returns a copy of this configuration
     */
    public MinaConfiguration copy() {
        try {
            return (MinaConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public String getCharsetName() {
        if (encoding == null) {
            return null;
        }
        if (!Charset.isSupported(encoding)) {
            throw new IllegalArgumentException("The encoding: " + encoding + " is not supported");
        }

        return Charset.forName(encoding).name();
    }

    public String getProtocol() {
        return protocol;
    }

    /**
     * Protocol to use
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getHost() {
        return host;
    }

    /**
     * Hostname to use. Use localhost or 0.0.0.0 for local server as consumer. For producer use the hostname or ip address of the remote server.
     */
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /**
     * Port number
     */
    public void setPort(int port) {
        this.port = port;
    }

    public boolean isSync() {
        return sync;
    }

    /**
     * Setting to set endpoint as one-way or request-response.
     */
    public void setSync(boolean sync) {
        this.sync = sync;
    }

    public boolean isTextline() {
        return textline;
    }

    /**
     * Only used for TCP. If no codec is specified, you can use this flag to indicate a text line based codec;
     * if not specified or the value is false, then Object Serialization is assumed over TCP.
     */
    public void setTextline(boolean textline) {
        this.textline = textline;
    }

    public TextLineDelimiter getTextlineDelimiter() {
        return textlineDelimiter;
    }

    /**
     * Only used for TCP and if textline=true. Sets the text line delimiter to use.
     * If none provided, Camel will use DEFAULT.
     * This delimiter is used to mark the end of text.
     */
    public void setTextlineDelimiter(TextLineDelimiter textlineDelimiter) {
        this.textlineDelimiter = textlineDelimiter;
    }

    public ProtocolCodecFactory getCodec() {
        return codec;
    }

    /**
     * To use a custom minda codec implementation.
     */
    public void setCodec(ProtocolCodecFactory codec) {
        this.codec = codec;
    }

    public String getEncoding() {
        return encoding;
    }

    /**
     * You can configure the encoding (a charset name) to use for the TCP textline codec and the UDP protocol.
     * If not provided, Camel will use the JVM default Charset
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public long getTimeout() {
        return timeout;
    }

    /**
     * You can configure the timeout that specifies how long to wait for a response from a remote server.
     * The timeout unit is in milliseconds, so 60000 is 60 seconds.
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public boolean isLazySessionCreation() {
        return lazySessionCreation;
    }

    /**
     * Sessions can be lazily created to avoid exceptions, if the remote server is not up and running when the Camel producer is started.
     */
    public void setLazySessionCreation(boolean lazySessionCreation) {
        this.lazySessionCreation = lazySessionCreation;
    }

    public boolean isTransferExchange() {
        return transferExchange;
    }

    /**
     * Only used for TCP. You can transfer the exchange over the wire instead of just the body.
     * The following fields are transferred: In body, Out body, fault body, In headers, Out headers, fault headers, exchange properties, exchange exception.
     * This requires that the objects are serializable. Camel will exclude any non-serializable objects and log it at WARN level.
     */
    public void setTransferExchange(boolean transferExchange) {
        this.transferExchange = transferExchange;
    }

    /**
     * To set the textline protocol encoder max line length. By default the default value of Mina itself is used which are Integer.MAX_VALUE.
     */
    public void setEncoderMaxLineLength(int encoderMaxLineLength) {
        this.encoderMaxLineLength = encoderMaxLineLength;
    }

    public int getEncoderMaxLineLength() {
        return encoderMaxLineLength;
    }

    /**
     * To set the textline protocol decoder max line length. By default the default value of Mina itself is used which are 1024.
     */
    public void setDecoderMaxLineLength(int decoderMaxLineLength) {
        this.decoderMaxLineLength = decoderMaxLineLength;
    }

    public int getDecoderMaxLineLength() {
        return decoderMaxLineLength;
    }

    public boolean isMinaLogger() {
        return minaLogger;
    }

    /**
     * You can enable the Apache MINA logging filter. Apache MINA uses slf4j logging at INFO level to log all input and output.
     */
    public void setMinaLogger(boolean minaLogger) {
        this.minaLogger = minaLogger;
    }

    public List<IoFilter> getFilters() {
        return filters;
    }

    /**
     * You can set a list of Mina IoFilters to use.
     */
    public void setFilters(List<IoFilter> filters) {
        this.filters = filters;
    }

    public boolean isDatagramProtocol() {
        return protocol.equals("udp");
    }

    /**
     * The mina component installs a default codec if both, codec is null and textline is false.
     * Setting allowDefaultCodec to false prevents the mina component from installing a default codec as the first element in the filter chain.
     * This is useful in scenarios where another filter must be the first in the filter chain, like the SSL filter.
     */
    public void setAllowDefaultCodec(boolean allowDefaultCodec) {
        this.allowDefaultCodec = allowDefaultCodec;
    }

    public boolean isAllowDefaultCodec() {
        return allowDefaultCodec;
    }

    public boolean isDisconnect() {
        return disconnect;
    }

    /**
     * Whether or not to disconnect(close) from Mina session right after use. Can be used for both consumer and producer.
     */
    public void setDisconnect(boolean disconnect) {
        this.disconnect = disconnect;
    }

    public boolean isDisconnectOnNoReply() {
        return disconnectOnNoReply;
    }

    /**
     * If sync is enabled then this option dictates MinaConsumer if it should disconnect where there is no reply to send back.
     */
    public void setDisconnectOnNoReply(boolean disconnectOnNoReply) {
        this.disconnectOnNoReply = disconnectOnNoReply;
    }

    public LoggingLevel getNoReplyLogLevel() {
        return noReplyLogLevel;
    }

    /**
     * If sync is enabled this option dictates MinaConsumer which logging level to use when logging a there is no reply to send back.
     */
    public void setNoReplyLogLevel(LoggingLevel noReplyLogLevel) {
        this.noReplyLogLevel = noReplyLogLevel;
    }
    
    public boolean isClientMode() {
        return clientMode;
    }

    /**
     * If the clientMode is true, mina consumer will connect the address as a TCP client.
     */
    public void setClientMode(boolean clientMode) {
        this.clientMode = clientMode;
    }
    
    // here we just shows the option setting of host, port, protocol 
    public String getUriString() {
        return "mina:" + getProtocol() + ":" + getHost() + ":" + getPort();
    }
}
