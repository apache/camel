/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.mllp;

import org.apache.camel.*;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.tools.apt.helper.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;

/**
 * Represents a MLLP endpoint.
 *
 * NOTE: MLLP payloads are not logged unless the logging level is set to DEBUG or TRACE to avoid introducing PHI
 * into the log files.  Logging of PHI can be globally disabled by setting the org.apache.camel.mllp.logPHI system
 * property to false.
 * <p/>
 * TODO:  Implement checking the org.apache.camel.mllp.logPHI system property before logging PHI
 * TODO:  Move the @UriPath and @UriParam elements to a separate configuration class
 */
@UriEndpoint(scheme = "mllp", title = "mllp", syntax = "mllp:hostname:port", consumerClass = MllpTcpServerConsumer.class, label = "mllp")
public class MllpEndpoint extends DefaultEndpoint {
    public static final char START_OF_BLOCK = 0x0b;      // VT (vertical tab)        - decimal 11, octal 013
    public static final char END_OF_BLOCK = 0x1c;        // FS (file separator)      - decimal 28, octal 034
    public static final char END_OF_DATA = 0x0d;         // CR (carriage return)     - decimal 13, octal 015
    public static final int END_OF_STREAM = -1;          //
    public static final char SEGMENT_DELIMITER = 0x0d;   // CR (carriage return)     - decimal 13, octal 015
    public static final char MESSAGE_TERMINATOR = 0x0a;  // LF (line feed, new line) - decimal 10, octal 012

    Logger log = LoggerFactory.getLogger(this.getClass());

    @UriPath(defaultValue = "0.0.0.0", description = "Hostname or IP for connection")
    String hostname = "0.0.0.0";

    @UriPath(description = "TCP Port for connection")
    int port = -1;

    @UriParam(defaultValue = "5", description = "TCP Server only - The maximum queue length for incoming connection indications (a request to connect) is set to the backlog parameter. If a connection indication arrives when the queue is full, the connection is refused.")
    int backlog = 5;

    @UriParam(defaultValue = "30000", description = "TCP Server only - timeout value while waiting for a tcp connection (milliseconds)")
    int acceptTimeout = 30000;

    @UriParam(defaultValue = "30000", description = "TCP Client only - timeout value while establishing for a tcp connection (milliseconds)")
    int connectTimeout = 30000;

    @UriParam(defaultValue = "5000", description = "Timeout value (milliseconds) used when reading a message from an external")
    int responseTimeout = 5000;

    @UriParam(defaultValue = "true", description = "Enable/disable the SO_KEEPALIVE socket option.")
    boolean keepAlive = true;

    @UriParam(defaultValue = "true", description = "Enable/disable the TCP_NODELAY socket option.")
    boolean tcpNoDelay = true;

    @UriParam(defaultValue = "false", description = "Enable/disable the SO_REUSEADDR socket option.")
    boolean reuseAddress = false;

    // TODO: Make the default value come from the system
    @UriParam(defaultValue = "65535", description = "Sets the SO_RCVBUF option to the specified value")
    int receiveBufferSize = 65535;

    // TODO: Make the default value come from the system
    @UriParam(defaultValue = "65535", description = "Sets the SO_SNDBUF option to the specified value")
    int sendBufferSize = 65535;

    @UriParam(defaultValue = "0", description = "The amount of time a TCP connection can remain idle before it is closed")
    int idleTimeout = 0;

    @UriParam(description = "The TCP mode of the endpoint (client or server).  Defaults to client for Producers and server for Consumers")
    String tcpMode;

    @UriParam(defaultValue = "true", description = "MLLP Consumers only - Automatically generate and send an MLLP Acknowledgement")
    boolean autoAck = true;

    @UriParam(description = "Set the CamelCharsetName property on the exchange")
    String charsetName;

    public MllpEndpoint() {
        log.trace("MllpEndpoint()");
    }

    public MllpEndpoint(String uri, MllpComponent component) {
        super(uri, component);

        // mllp://hostname:port
        String hostPort;
        // look for options
        int optionsStartIndex = uri.indexOf('?');
        if (-1 == optionsStartIndex) {
            // No options - just get the host/port stuff
            hostPort = uri.substring(7);
        } else {
            hostPort = uri.substring(7, optionsStartIndex);
        }

        // Make sure it has a host - may just be a port
        int colonIndex = hostPort.indexOf(':');
        if (-1 != colonIndex) {
            hostname = hostPort.substring(0, colonIndex);
            port = Integer.parseInt(hostPort.substring(colonIndex + 1));
        } else {
            // No host specified - leave the default host and set the port
            port = Integer.parseInt(hostPort);
        }

        log.trace("MllpEndpoint(uri, component)");
    }


    @Override
    protected void doStart() throws Exception {
        log.trace("({}).doStart()", this.getEndpointKey());

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        log.trace("({}).doStop()", this.getEndpointKey());

        super.doStop();
    }

    @Override
    protected void doSuspend() throws Exception {
        log.trace("({}).doSuspend()", this.getEndpointKey());

        super.doSuspend();
    }

    @Override
    protected void doResume() throws Exception {
        log.trace("({}).doResume()", this.getEndpointKey());

        super.doSuspend();
    }

    @Override
    protected void doShutdown() throws Exception {
        log.trace("({}).doShutdown()", this.getEndpointKey());

        super.doShutdown();
    }

    @Override
    public ExchangePattern getExchangePattern() {
        return ExchangePattern.InOut;
    }

    @Override
    public Exchange createExchange() {
        return this.createExchange(getExchangePattern());
    }

    @Override
    public Exchange createExchange( ExchangePattern exchangePattern ) {
        Exchange mllpExchange = super.createExchange(exchangePattern);
        setExchangeProperties(mllpExchange);

        return mllpExchange;
    }

    @Override
    public Exchange createExchange(Exchange exchange) {
        Exchange mllpExchange = super.createExchange(exchange);
        setExchangeProperties(mllpExchange);

        return mllpExchange;
    }

    private void setExchangeProperties( Exchange mllpExchange ) {
        if ( null != charsetName ) {
            mllpExchange.setProperty(Exchange.CHARSET_NAME, charsetName);
        }
    }

    public Producer createProducer() throws Exception {
        log.trace("({}).createProducer()", this.getEndpointKey());

        return new MllpTcpClientProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        log.trace("({}).createConsumer(processor)", this.getEndpointKey());

        return new MllpTcpServerConsumer(this, processor);
    }

    public boolean isLenientProperties() {
        // default should be false for most components
        boolean lenientProperties = false;

        log.trace("(??).isLenientProperties(hostname): {}", lenientProperties);

        return lenientProperties;
    }

    @Override
    public boolean isSynchronous() {
        boolean synchronous = true;

        log.trace("({}).isSynchronous() -> {}", this.getEndpointKey(), synchronous);

        return synchronous;
    }

    public boolean isSingleton() {
        boolean singleton = true;

        log.trace("({}).isSingleton() -> {}", this.getEndpointKey(), singleton);

        return singleton;
    }

    public String getCharsetName() {
        return charsetName;
    }

    public void setCharsetName(String charsetName) {
        this.charsetName = charsetName;
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    /**
     * The maximum queue length for incoming connection indications (a request to connect) is set to the backlog parameter. If a connection indication arrives when the queue is full, the connection is refused.
     */
    public int getBacklog() {
        return backlog;
    }

    /**
     * The maximum queue length for incoming connection indications (a request to connect) is set to the backlog parameter. If a connection indication arrives when the queue is full, the connection is refused.
     */
    public void setBacklog(int backlog) {
        this.backlog = backlog;
    }

    public int getAcceptTimeout() {
        return acceptTimeout;
    }

    public void setAcceptTimeout(int acceptTimeout) {
        this.acceptTimeout = acceptTimeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(int responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public boolean isReuseAddress() {
        return reuseAddress;
    }

    public void setReuseAddress(boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
    }

    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    public void setReceiveBufferSize(int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
    }

    public int getSendBufferSize() {
        return sendBufferSize;
    }

    public void setSendBufferSize(int sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
    }

    public boolean isAutoAck() {
        log.trace("({}).isAutoAck() -> {}", this.getEndpointKey(), autoAck);

        return autoAck;
    }

    public void setAutoAck(boolean autoAck) {
        log.trace("({}).setAutoAck(boolean: {})", this.getEndpointKey(), autoAck);

        this.autoAck = autoAck;
    }

}
