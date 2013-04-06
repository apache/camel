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
import org.apache.mina.common.IoFilter;
import org.apache.mina.filter.codec.ProtocolCodecFactory;

/**
 * Mina configuration
 */
public class MinaConfiguration implements Cloneable {
    private String protocol;
    private String host;
    private int port;
    private boolean sync = true;
    private boolean textline;
    private TextLineDelimiter textlineDelimiter;
    private ProtocolCodecFactory codec;
    private String encoding;
    private long timeout = 30000;
    private boolean lazySessionCreation = true;
    private boolean transferExchange;
    private boolean minaLogger;
    private int encoderMaxLineLength = -1;
    private int decoderMaxLineLength = -1;
    private List<IoFilter> filters;
    private boolean allowDefaultCodec = true;
    private boolean disconnect;
    private boolean disconnectOnNoReply = true;
    private LoggingLevel noReplyLogLevel = LoggingLevel.WARN;

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

    public void setProtocol(String protocol) {
        this.protocol = protocol;
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

    public boolean isSync() {
        return sync;
    }

    public void setSync(boolean sync) {
        this.sync = sync;
    }

    public boolean isTextline() {
        return textline;
    }

    public void setTextline(boolean textline) {
        this.textline = textline;
    }

    public TextLineDelimiter getTextlineDelimiter() {
        return textlineDelimiter;
    }

    public void setTextlineDelimiter(TextLineDelimiter textlineDelimiter) {
        this.textlineDelimiter = textlineDelimiter;
    }

    public ProtocolCodecFactory getCodec() {
        return codec;
    }

    public void setCodec(ProtocolCodecFactory codec) {
        this.codec = codec;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public boolean isLazySessionCreation() {
        return lazySessionCreation;
    }

    public void setLazySessionCreation(boolean lazySessionCreation) {
        this.lazySessionCreation = lazySessionCreation;
    }

    public boolean isTransferExchange() {
        return transferExchange;
    }

    public void setTransferExchange(boolean transferExchange) {
        this.transferExchange = transferExchange;
    }

    public void setEncoderMaxLineLength(int encoderMaxLineLength) {
        this.encoderMaxLineLength = encoderMaxLineLength;
    }

    public int getEncoderMaxLineLength() {
        return encoderMaxLineLength;
    }

    public void setDecoderMaxLineLength(int decoderMaxLineLength) {
        this.decoderMaxLineLength = decoderMaxLineLength;
    }

    public int getDecoderMaxLineLength() {
        return decoderMaxLineLength;
    }

    public boolean isMinaLogger() {
        return minaLogger;
    }

    public void setMinaLogger(boolean minaLogger) {
        this.minaLogger = minaLogger;
    }

    public List<IoFilter> getFilters() {
        return filters;
    }

    public void setFilters(List<IoFilter> filters) {
        this.filters = filters;
    }

    public boolean isDatagramProtocol() {
        return protocol.equals("udp");
    }
    
    public void setAllowDefaultCodec(boolean allowDefaultCodec) {
        this.allowDefaultCodec = allowDefaultCodec;
    }

    public boolean isAllowDefaultCodec() {
        return allowDefaultCodec;
    }

    public boolean isDisconnect() {
        return disconnect;
    }

    public void setDisconnect(boolean disconnect) {
        this.disconnect = disconnect;
    }

    public boolean isDisconnectOnNoReply() {
        return disconnectOnNoReply;
    }

    public void setDisconnectOnNoReply(boolean disconnectOnNoReply) {
        this.disconnectOnNoReply = disconnectOnNoReply;
    }

    public LoggingLevel getNoReplyLogLevel() {
        return noReplyLogLevel;
    }

    public void setNoReplyLogLevel(LoggingLevel noReplyLogLevel) {
        this.noReplyLogLevel = noReplyLogLevel;
    }
}
