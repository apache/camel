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
package org.apache.camel.component.mllp;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a MLLP endpoint.
 *
 * NOTE: MLLP payloads are not logged unless the logging level is set to DEBUG or TRACE to avoid introducing PHI
 * into the log files.  Logging of PHI can be globally disabled by setting the org.apache.camel.mllp.logPHI system
 * property to false.
 * <p/>
 */
@UriEndpoint(scheme = "mllp", title = "mllp", syntax = "mllp:hostname:port", consumerClass = MllpTcpServerConsumer.class, label = "mllp")
public class MllpEndpoint extends DefaultEndpoint {
    public static final char START_OF_BLOCK = 0x0b;      // VT (vertical tab)        - decimal 11, octal 013
    public static final char END_OF_BLOCK = 0x1c;        // FS (file separator)      - decimal 28, octal 034
    public static final char END_OF_DATA = 0x0d;         // CR (carriage return)     - decimal 13, octal 015
    public static final int END_OF_STREAM = -1;          //
    public static final char SEGMENT_DELIMITER = 0x0d;   // CR (carriage return)     - decimal 13, octal 015
    public static final char MESSAGE_TERMINATOR = 0x0a;  // LF (line feed, new line) - decimal 10, octal 012

    private static final Logger LOG = LoggerFactory.getLogger(MllpEndpoint.class);

    @UriPath(defaultValue = "0.0.0.0", description = "Hostname or IP for connection")
    String hostname = "0.0.0.0";

    @UriPath(description = "TCP Port for connection")
    int port = -1;

    // TODO:  Move URI Params to a MllpConfiguration class
    // TODO: Move the description documentation to javadoc in the setter method
    @UriParam(defaultValue = "5", description = "TCP Server only - The maximum queue length for incoming connection indications (a request to connect) is set to the backlog parameter. If a "
            + "connection indication arrives when the queue is full, the connection is refused.")
    int backlog = 5;

    @UriParam(defaultValue = "30000", description = "TCP Server only - timeout value while waiting for a TCP listener to start (milliseconds)")
    int bindTimeout = 30000;

    @UriParam(defaultValue = "30000", description = "TCP Server only - timeout value while waiting for a TCP connection (milliseconds)")
    int acceptTimeout = 30000;

    @UriParam(defaultValue = "30000", description = "TCP Client only - timeout value while establishing for a TCP connection (milliseconds)")
    int connectTimeout = 30000;

    @UriParam(defaultValue = "5000", description = "Timeout value (milliseconds) used when reading a message from an external")
    int responseTimeout = 5000;

    @UriParam(defaultValue = "true", description = "Enable/disable the SO_KEEPALIVE socket option.")
    boolean keepAlive = true;

    @UriParam(defaultValue = "true", description = "Enable/disable the TCP_NODELAY socket option.")
    boolean tcpNoDelay = true;

    @UriParam(defaultValue = "false", description = "Enable/disable the SO_REUSEADDR socket option.")
    boolean reuseAddress;

    @UriParam(description = "Sets the SO_RCVBUF option to the specified value")
    Integer receiveBufferSize;

    @UriParam(description = "Sets the SO_SNDBUF option to the specified value")
    Integer sendBufferSize;

    @UriParam(defaultValue = "0", description = "The amount of time a TCP connection can remain idle before it is closed")
    int idleTimeout;

    @UriParam(description = "The TCP mode of the endpoint (client or server).  Defaults to client for Producers and server for Consumers")
    String tcpMode;

    @UriParam(defaultValue = "true", description = "MLLP Consumers only - Automatically generate and send an MLLP Acknowledgement")
    boolean autoAck = true;

    @UriParam(description = "Set the CamelCharsetName property on the exchange")
    String charsetName;

    public MllpEndpoint(String uri, MllpComponent component) {
        super(uri, component);

        // TODO: this logic should be in component class
        // TODO: all the options in the endpoint should be getter/setter so you can configure them as plain Java

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
    }


    @Override
    public ExchangePattern getExchangePattern() {
        return ExchangePattern.InOut;
    }

    @Override
    public Exchange createExchange(ExchangePattern exchangePattern) {
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

    private void setExchangeProperties(Exchange mllpExchange) {
        if (charsetName != null) {
            mllpExchange.setProperty(Exchange.CHARSET_NAME, charsetName);
        }
    }

    public Producer createProducer() throws Exception {
        LOG.trace("({}).createProducer()", this.getEndpointKey());
        return new MllpTcpClientProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        LOG.trace("({}).createConsumer(processor)", this.getEndpointKey());
        Consumer consumer = new MllpTcpServerConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public boolean isSynchronous() {
        return true;
    }

    public boolean isSingleton() {
        return true;
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
     * The maximum queue length for incoming connection indications (a request to connect) is set to the backlog parameter. If a connection indication arrives when the queue is full, the connection
     * is refused.
     */
    public int getBacklog() {
        return backlog;
    }

    /**
     * The maximum queue length for incoming connection indications (a request to connect) is set to the backlog parameter. If a connection indication arrives when the queue is full, the connection
     * is refused.
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
        return autoAck;
    }

    public void setAutoAck(boolean autoAck) {
        this.autoAck = autoAck;
    }

}
