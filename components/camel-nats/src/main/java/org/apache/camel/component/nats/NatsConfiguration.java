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
package org.apache.camel.component.nats;

import java.util.Properties;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class NatsConfiguration {

    @UriPath
    @Metadata(required = "true")
    private String servers;
    @UriParam
    @Metadata(required = "true")
    private String topic;
    @UriParam(defaultValue = "true")
    private boolean reconnect = true;
    @UriParam(defaultValue = "false")
    private boolean pedantic;
    @UriParam(defaultValue = "false")
    private boolean verbose;
    @UriParam(defaultValue = "false")
    private boolean ssl;
    @UriParam(defaultValue = "2000")
    private int reconnectTimeWait = 2000;
    @UriParam(defaultValue = "3")
    private int maxReconnectAttempts = 3;
    @UriParam(defaultValue = "4000")
    private int pingInterval = 4000;
    @UriParam(label = "producer")
    private String replySubject;
    @UriParam(defaultValue = "false")
    private boolean noRandomizeServers;
    @UriParam(label = "consumer")
    private String queueName;
    @UriParam(label = "consumer")
    private String maxMessages;
    @UriParam(label = "consumer", defaultValue = "10")
    private int poolSize = 10;

    /**
     * URLs to one or more NAT servers. Use comma to separate URLs when specifying multiple servers.
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
     * Whether or not using reconnection feature
     */
    public boolean getReconnect() {
        return reconnect;
    }

    public void setReconnect(boolean reconnect) {
        this.reconnect = reconnect;
    }

    /**
     * Whether or not running in pedantic mode (this affects performace)
     */
    public boolean getPedantic() {
        return pedantic;
    }

    public void setPedantic(boolean pedantic) {
        this.pedantic = pedantic;
    }

    /**
     * Whether or not running in verbose mode
     */
    public boolean getVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Whether or not using SSL
     */
    public boolean getSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
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
     * Ping interval to be aware if connection is still alive (in milliseconds)
     */
    public int getPingInterval() {
        return pingInterval;
    }

    public void setPingInterval(int pingInterval) {
        this.pingInterval = pingInterval;
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
     * Whether or not randomizing the order of servers for the connection attempts
     */
    public boolean getNoRandomizeServers() {
        return noRandomizeServers;
    }

    public void setNoRandomizeServers(boolean noRandomizeServers) {
        this.noRandomizeServers = noRandomizeServers;
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

    /**
     * Stop receiving messages from a topic we are subscribing to after maxMessages 
     */
    public String getMaxMessages() {
        return maxMessages;
    }

    public void setMaxMessages(String maxMessages) {
        this.maxMessages = maxMessages;
    }

    /**
     * Consumer pool size
     */
    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    private static <T> void addPropertyIfNotNull(Properties props, String key, T value) {
        if (value != null) {
            props.put(key, value);
        }
    }

    public Properties createProperties() {
        Properties props = new Properties();
        addPropertyIfNotNull(props, NatsPropertiesConstants.NATS_PROPERTY_URL, splitServers());
        addPropertyIfNotNull(props, NatsPropertiesConstants.NATS_PROPERTY_VERBOSE, getVerbose());
        addPropertyIfNotNull(props, NatsPropertiesConstants.NATS_PROPERTY_PEDANTIC, getPedantic());
        addPropertyIfNotNull(props, NatsPropertiesConstants.NATS_PROPERTY_SSL, getSsl());
        addPropertyIfNotNull(props, NatsPropertiesConstants.NATS_PROPERTY_RECONNECT, getReconnect());
        addPropertyIfNotNull(props, NatsPropertiesConstants.NATS_PROPERTY_MAX_RECONNECT_ATTEMPTS, getMaxReconnectAttempts());
        addPropertyIfNotNull(props, NatsPropertiesConstants.NATS_PROPERTY_RECONNECT_TIME_WAIT, getReconnectTimeWait());
        addPropertyIfNotNull(props, NatsPropertiesConstants.NATS_PROPERTY_PING_INTERVAL, getPingInterval());
        addPropertyIfNotNull(props, NatsPropertiesConstants.NATS_PROPERTY_DONT_RANDOMIZE_SERVERS, getNoRandomizeServers());
        return props;
    }

    private String splitServers() {
        StringBuilder servers = new StringBuilder();
        String prefix = "nats://";

        String[] pieces = getServers().split(",");
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
