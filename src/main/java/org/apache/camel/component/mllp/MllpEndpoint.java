package org.apache.camel.component.mllp;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;

/**
 * Represents a mllp endpoint.
 */
@UriEndpoint(scheme = "mllp", title = "mllp", syntax="mllp:hostname:port", consumerClass = MllpTcpServerConsumer.class, label = "mllp")
public class MllpEndpoint extends DefaultEndpoint {
    public static final char START_OF_BLOCK = 0x0b;      // VT (vertical tab)        - decimal 11, octal 013
    public static final char END_OF_BLOCK = 0x1c;        // FS (file separator)      - decimal 28, octal 034
    public static final char END_OF_DATA = 0x0d;         // CR (carriage return)     - decimal 13, octal 015
    public static final char SEGMENT_DELIMITER = 0x0d;   // CR (carriage return)     - decimal 13, octal 015
    public static final char MESSAGE_TERMINATOR = 0x0a;  // LF (line feed, new line) - decimal 10, octal 012

    Logger log = LoggerFactory.getLogger(this.getClass());

    @UriPath @Metadata(required = "false", defaultValue = "0.0.0.0")
    String hostname = "0.0.0.0";

    @UriPath @Metadata(required = "true")
    int port = -1;

    @UriParam( defaultValue = "5")
    int backlog = 5;

    @UriParam( defaultValue = "30000")
    int connectTimeout = 30000;

    @UriParam( defaultValue = "5000")
    int responseTimeout = 5000;

    @UriParam( defaultValue = "true")
    boolean keepAlive = true;

    @UriParam( defaultValue = "true")
    boolean tcpNoDelay = true;

    @UriParam( defaultValue = "false")
    boolean reuseAddress = false;

    @UriParam( defaultValue = "65535")
    int receiveBufferSize = 65535;

    @UriParam( defaultValue = "65535")
    int sendBufferSize = 65535;

    @UriParam(defaultValue = "true")
    boolean autoAck = true;

    @UriParam(defaultValue = "true")
    boolean waitForAck = true;

    Charset charset = Charset.defaultCharset();

    public MllpEndpoint() {
        log.trace( "MllpEndpoint()" );
    }

    public MllpEndpoint(String uri, MllpComponent component) {
        super(uri, component);

        // mllp://hostname:port
        String hostPort;
        // look for options
        int optionsStartIndex = uri.indexOf('?');
        if ( -1 == optionsStartIndex ) {
            // No options - just get the host/port stuff
            hostPort = uri.substring(7);
        } else {
            hostPort = uri.substring(7, optionsStartIndex);
        }

        // Make sure it has a host - may just be a port
        int colonIndex = hostPort.indexOf(':');
        if ( -1 != colonIndex ) {
            hostname = hostPort.substring(0,colonIndex);
            port = Integer.parseInt( hostPort.substring(colonIndex+1) );
        } else {
            // No host specified - leave the default host and set the port
            port = Integer.valueOf( hostPort );
        }

        log.trace( "MllpEndpoint(uri, component)" );
    }

    @Override
    protected void doStart() throws Exception {
        log.trace( "({}).doStart()", this.getEndpointKey());

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        log.trace( "({}).doStop()", this.getEndpointKey());

        super.doStop();
    }

    @Override
    protected void doSuspend() throws Exception {
        log.trace( "({}).doSuspend()", this.getEndpointKey());

        super.doSuspend();
    }

    @Override
    protected void doResume() throws Exception {
        log.trace( "({}).doResume()", this.getEndpointKey());

        super.doSuspend();
    }

    @Override
    protected void doShutdown() throws Exception {
        log.trace( "({}).doShutdown()", this.getEndpointKey());

        super.doShutdown();
    }

    public Producer createProducer() throws Exception {
        log.trace( "({}).createProducer()", this.getEndpointKey() );

        return new MllpTcpClientProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        log.trace( "({}).createConsumer(processor)", this.getEndpointKey() );

        return new MllpTcpServerConsumer(this, processor);
    }

    public boolean isLenientProperties() {
        // default should be false for most components
        boolean lenientProperties = false;

        log.trace( "(??).isLenientProperties(hostname): {}", lenientProperties );

        return lenientProperties;
    }

    @Override
    public boolean isSynchronous() {
        boolean synchronous = true;

        log.trace( "({}).isSynchronous() -> {}", this.getEndpointKey(), synchronous );

        return synchronous;
    }

    public boolean isSingleton() {
        boolean singleton = true;

        log.trace( "({}).isSingleton() -> {}", this.getEndpointKey(), singleton );

        return singleton;
    }

    public void setHostname(String hostname) {
        log.trace( "({}).setHostname(String: {})", this.getEndpointKey(), hostname );

        this.hostname = hostname;
    }

    public String getHostname() {
        log.trace( "({}).getHostname() -> {}", this.getEndpointKey(), hostname );

        return hostname;
    }

    public int getPort() {
        log.trace( "({}).getPort() -> {}", this.getEndpointKey(), port );

        return port;
    }

    public void setPort(int port) {
        log.trace( "({}).setPort(int: {})", this.getEndpointKey(), port );
        this.port = port;
    }

    public Charset getCharset() {
        return charset;
    }

    public void setCharset(Charset charset) {
        this.charset = charset;
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
        log.trace( "({}).isAutoAck() -> {}", this.getEndpointKey(), autoAck );

        return autoAck;
    }

    public void setAutoAck(boolean autoAck) {
        log.trace( "({}).setAutoAck(boolean: {})", this.getEndpointKey(), autoAck );

        this.autoAck = autoAck;
    }

    public boolean isWaitForAck() {
        log.trace( "({}).isWaitForAck() -> {}", this.getEndpointKey(), waitForAck );

        return waitForAck;
    }

    public void setWaitForAck(boolean waitForAck) {
        log.trace( "({}).setWaitForAck(boolean: {})", this.getEndpointKey(), waitForAck );

        this.waitForAck = waitForAck;
    }


}
