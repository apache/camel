package org.apache.camel.component.nsq;

import com.github.brainlag.nsq.ServerAddress;
import com.google.common.collect.Sets;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.jsse.SSLContextParameters;

import java.util.Set;

import static org.apache.camel.component.nsq.NsqConstants.NSQ_DEFAULT_LOOKUP_PORT;
import static org.apache.camel.component.nsq.NsqConstants.NSQ_DEFAULT_PORT;

@UriParams
public class NsqConfiguration {

    @UriPath(description = "The hostnames of one or more nsqlookupd servers (consumer) or nsqd servers (producer).")
    @Metadata(required = "true")
    private String servers;
    @UriParam(description = "The NSQ topic")
    @Metadata(required = "true")
    private String topic;
    @UriParam(label = "consumer", description = "The NSQ channel")
    private String channel;
    @UriParam(label = "consumer", defaultValue = "10")
    private int poolSize = 10;
    @UriParam(label = "consumer", defaultValue = "4161", description = "The NSQ lookup server port")
    private int lookupServerPort = NSQ_DEFAULT_LOOKUP_PORT;
    @UriParam(label = "producer", defaultValue = "4150")
    private int port = NSQ_DEFAULT_PORT;
    @UriParam(label = "consumer", defaultValue = "5000", description = "The lookup interval")
    private long lookupInterval = 5000;
    @UriParam(label = "consumer", defaultValue = "0", description = "The requeue interval")
    private long requeueInterval = 0;
    @UriParam(label = "security")
    private SSLContextParameters sslContextParameters;
    @UriParam(label = "consumer", defaultValue = "true", description = "Automatically finish the NSQ Message when it is retrievd from the queue and before the Exchange is processed.")
    private Boolean autoFinish = true;
    @UriParam(label = "consumer", description = "The NSQ consumer timeout period for messages retrieved from the queue.")
    private long messageTimeout;

    /*
     * URL a NSQ lookup server hostname.
     */
    public String getServers() {
        return servers;
    }

    public void setServers(String servers) {
        this.servers = servers;
    }

    public Set<ServerAddress> getServerAddresses() {

        Set<ServerAddress> serverAddresses = Sets.newConcurrentHashSet();

        String[] addresses = servers.split(",");
        for (int i = 0; i < addresses.length; i++) {
            String[] token = addresses[i].split(":");
            String host;
            int port;
            if (token.length == 2) {
                host = token[0];
                port = Integer.parseInt(token[1]);

            } else if (token.length == 1) {
                host = token[0];
                port = 0;

            } else {
                throw new IllegalArgumentException("Invalid address: " + addresses[i]);
            }
            serverAddresses.add(new ServerAddress(host, port));
        }
        return serverAddresses;
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
     * The name of channel we want to use
     */
    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
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

    /**
     * The port of the nsqdlookupd server
     */
    public int getLookupServerPort() {
        return lookupServerPort;
    }

    public void setLookupServerPort(int lookupServerPort) {
        this.lookupServerPort = lookupServerPort;
    }

    /**
     * The port of the nsqd server
     */
    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    /**
     * The lookup retry interval
     */
    public long getLookupInterval() {
        return lookupInterval;
    }

    public void setLookupInterval(long lookupInterval) {
        this.lookupInterval = lookupInterval;
    }

    /**
     * The requeue interval
     */
    public long getRequeueInterval() {
        return requeueInterval;
    }

    public void setRequeueInterval(long requeueInterval) {
        this.requeueInterval = requeueInterval;
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

    /**
     * Automatically finish the NSQ message when it is retrieved from the quese and before the Exchange is processed.
     */
    public Boolean getAutoFinish() {
        return autoFinish;
    }

    public void setAutoFinish(Boolean autoFinish) {
        this.autoFinish = autoFinish;
    }

    /**
     * The NSQ message timeout for a consumer.
     */
    public long getMessageTimeout() {
        return messageTimeout;
    }

    public void setMessageTimeout(long messageTimeout) {
        this.messageTimeout = messageTimeout;
    }

    private String splitServers() {
        StringBuilder servers = new StringBuilder();

        String[] pieces = getServers().split(",");
        for (int i = 0; i < pieces.length; i++) {
            if (i < pieces.length - 1) {
                servers.append(pieces[i] + ",");
            } else {
                servers.append(pieces[i]);
            }
        }
        return servers.toString();
    }
}
