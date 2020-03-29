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
package org.apache.camel.component.nats;

import java.security.NoSuchAlgorithmException;
import java.time.Duration;

import io.nats.client.Connection;
import io.nats.client.Options;
import io.nats.client.Options.Builder;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;

@UriParams
public class NatsConfiguration {

    @UriPath
    @Metadata(required = true)
    private String topic;
    @UriParam(label = "common")
    private String servers;
    @UriParam(label = "advanced")
    private Connection connection;
    @UriParam(label = "common", defaultValue = "true")
    private boolean reconnect = true;
    @UriParam(label = "common", defaultValue = "2000")
    private int reconnectTimeWait = 2000;
    @UriParam(label = "common")
    private boolean pedantic;
    @UriParam
    private boolean verbose;
    @UriParam(defaultValue = "60")
    private int maxReconnectAttempts = Options.DEFAULT_MAX_RECONNECT;
    @UriParam(defaultValue = "120000")
    private int pingInterval = 120000;
    @UriParam(label = "common", defaultValue = "2000")
    private int connectionTimeout = 2000;
    @UriParam(label = "common", defaultValue = "2")
    private int maxPingsOut = Options.DEFAULT_MAX_PINGS_OUT;
    @UriParam(label = "common", defaultValue = "5000")
    private int requestCleanupInterval = 5000;
    @UriParam(label = "producer")
    private String replySubject;
    @UriParam
    private boolean noRandomizeServers;
    @UriParam
    private boolean noEcho;
    @UriParam(label = "consumer")
    private String queueName;
    @UriParam(label = "consumer")
    private boolean replyToDisabled;
    @UriParam(label = "consumer")
    private String maxMessages;
    @UriParam(label = "consumer", defaultValue = "10")
    private int poolSize = 10;
    @UriParam(label = "common", defaultValue = "false")
    private boolean flushConnection;
    @UriParam(label = "common", defaultValue = "1000")
    private int flushTimeout = 1000;
    @UriParam(label = "security")
    private boolean secure;
    @UriParam(label = "security")
    private SSLContextParameters sslContextParameters;

    /**
     * URLs to one or more NAT servers. Use comma to separate URLs when
     * specifying multiple servers.
     */
    public String getServers() {
        return servers;
    }

    public void setServers(String servers) {
        this.servers = servers;
    }

    /**
     * The name of topic we want to use
     */
    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }
    
    /**
     * Reference an already instantiated connection to Nats server
     */  
    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    /**
     * Whether or not using reconnection feature
     */
    public boolean isReconnect() {
        return reconnect;
    }

    public void setReconnect(boolean reconnect) {
        this.reconnect = reconnect;
    }

    /**
     * Whether or not running in pedantic mode (this affects performance)
     */
    public boolean isPedantic() {
        return pedantic;
    }

    public void setPedantic(boolean pedantic) {
        this.pedantic = pedantic;
    }

    /**
     * Whether or not running in verbose mode
     */
    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Waiting time before attempts reconnection (in milliseconds)
     */
    public int getReconnectTimeWait() {
        return reconnectTimeWait;
    }

    public void setReconnectTimeWait(int reconnectTimeWait) {
        this.reconnectTimeWait = reconnectTimeWait;
    }

    /**
     * Max reconnection attempts
     */
    public int getMaxReconnectAttempts() {
        return maxReconnectAttempts;
    }

    public void setMaxReconnectAttempts(int maxReconnectAttempts) {
        this.maxReconnectAttempts = maxReconnectAttempts;
    }

    /**
     * maximum number of pings have not received a response allowed by the
     * client
     */
    public int getMaxPingsOut() {
        return maxPingsOut;
    }

    public void setMaxPingsOut(int maxPingsOut) {
        this.maxPingsOut = maxPingsOut;
    }

    /**
     *  Interval to clean up cancelled/timed out requests.
     */
    public int getRequestCleanupInterval() {
        return requestCleanupInterval;
    }

    public void setRequestCleanupInterval(int requestCleanupInterval) {
        this.requestCleanupInterval = requestCleanupInterval;
    }

    /**
     * Ping interval to be aware if connection is still alive (in milliseconds)
     */
    public int getPingInterval() {
        return pingInterval;
    }

    public void setPingInterval(int pingInterval) {
        this.pingInterval = pingInterval;
    }

    /**
     * Timeout for connection attempts. (in milliseconds)
     */
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    /**
     * the subject to which subscribers should send response
     */
    public String getReplySubject() {
        return replySubject;
    }

    public void setReplySubject(String replySubject) {
        this.replySubject = replySubject;
    }

    /**
     * Whether or not randomizing the order of servers for the connection
     * attempts
     */
    public boolean isNoRandomizeServers() {
        return noRandomizeServers;
    }

    public void setNoRandomizeServers(boolean noRandomizeServers) {
        this.noRandomizeServers = noRandomizeServers;
    }

    /**
     * Turn off echo. If supported by the gnatsd version you are connecting to
     * this flag will prevent the server from echoing messages back to the
     * connection if it has subscriptions on the subject being published to.
     */
    public boolean isNoEcho() {
        return noEcho;
    }

    public void setNoEcho(boolean noEcho) {
        this.noEcho = noEcho;
    }

    /**
     * The Queue name if we are using nats for a queue configuration
     */
    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public boolean isReplyToDisabled() {
        return replyToDisabled;
    }

    /**
     * Can be used to turn off sending back reply message in the consumer.
     */
    public void setReplyToDisabled(boolean replyToDisabled) {
        this.replyToDisabled = replyToDisabled;
    }

    /**
     * Stop receiving messages from a topic we are subscribing to after
     * maxMessages
     */
    public String getMaxMessages() {
        return maxMessages;
    }

    public void setMaxMessages(String maxMessages) {
        this.maxMessages = maxMessages;
    }

    /**
     * Consumer thread pool size (default is 10)
     */
    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    public boolean isFlushConnection() {
        return flushConnection;
    }

    /**
     * Define if we want to flush connection when stopping or not
     */
    public void setFlushConnection(boolean flushConnection) {
        this.flushConnection = flushConnection;
    }

    public int getFlushTimeout() {
        return flushTimeout;
    }

    /**
     * Set the flush timeout (in milliseconds)
     */
    public void setFlushTimeout(int flushTimeout) {
        this.flushTimeout = flushTimeout;
    }

    /**
     * Set secure option indicating TLS is required
     */
    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    /**
     * To configure security using SSLContextParameters
     */
    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public Builder createOptions() throws NoSuchAlgorithmException, IllegalArgumentException {
        Builder builder = new Options.Builder();
        builder.server(splitServers());
        if (isVerbose()) {
            builder.verbose();
        }
        if (isPedantic()) {
            builder.pedantic();
        }
        if (isSecure()) {
            builder.secure();
        }
        if (!isReconnect()) {
            builder.noReconnect();
        } else {
            builder.maxReconnects(getMaxReconnectAttempts());
            builder.reconnectWait(Duration.ofMillis(getReconnectTimeWait()));
        }
        builder.pingInterval(Duration.ofMillis(getPingInterval()));
        builder.connectionTimeout(Duration.ofMillis(getConnectionTimeout()));
        builder.maxPingsOut(getMaxPingsOut());
        builder.requestCleanupInterval(Duration.ofMillis(getRequestCleanupInterval()));
        if (isNoRandomizeServers()) {
            builder.noRandomize();
        }
        if (isNoEcho()) {
            builder.noEcho();
        }
        return builder;
    }

    private String splitServers() {
        StringBuilder servers = new StringBuilder();
        String prefix = "nats://";

        String srvspec = getServers();
        ObjectHelper.notNull(srvspec, "No servers configured");

        String[] pieces = srvspec.split(",");
        for (int i = 0; i < pieces.length; i++) {
            if (i < pieces.length - 1) {
                servers.append(prefix + pieces[i] + ",");
            } else {
                servers.append(prefix + pieces[i]);
            }
        }
        return servers.toString();
    }
}
