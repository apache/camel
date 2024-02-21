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
package org.apache.camel.component.irc;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.StringHelper;
import org.schwering.irc.lib.ssl.SSLDefaultTrustManager;
import org.schwering.irc.lib.ssl.SSLTrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriParams
public class IrcConfiguration implements Cloneable {
    private static final Logger LOG = LoggerFactory.getLogger(IrcConfiguration.class);

    private boolean usingSSL;
    private List<IrcChannel> channelList = new ArrayList<>();

    @UriPath
    @Metadata(required = true)
    private String hostname;
    @UriPath
    private int port;
    private int[] ports = { 6667, 6668, 6669 };
    @UriParam(label = "security", secret = true)
    private String password;
    @UriParam(label = "common")
    private String nickname;
    @UriParam(label = "common")
    private String channels;
    @UriParam(label = "common")
    private String keys;
    @UriParam(label = "common")
    private String realname;
    @UriParam(label = "security", secret = true)
    private String username;
    @UriParam(label = "security")
    private SSLTrustManager trustManager = new SSLDefaultTrustManager();
    @UriParam(defaultValue = "true")
    @Deprecated
    private boolean persistent = true;
    @UriParam(defaultValue = "true", label = "advanced")
    private boolean colors = true;
    @UriParam(defaultValue = "true", label = "filter")
    private boolean onNick = true;
    @UriParam(defaultValue = "true", label = "filter")
    private boolean onQuit = true;
    @UriParam(defaultValue = "true", label = "filter")
    private boolean onJoin = true;
    @UriParam(defaultValue = "true", label = "filter")
    private boolean onKick = true;
    @UriParam(defaultValue = "true", label = "filter")
    private boolean onMode = true;
    @UriParam(defaultValue = "true", label = "filter")
    private boolean onPart = true;
    @UriParam(label = "filter")
    private boolean onReply;
    @UriParam(defaultValue = "true", label = "filter")
    private boolean onTopic = true;
    @UriParam(defaultValue = "true", label = "filter")
    private boolean onPrivmsg = true;
    @UriParam(defaultValue = "true")
    private boolean autoRejoin = true;
    @UriParam(label = "common")
    private boolean namesOnJoin;
    @UriParam(label = "security")
    private SSLContextParameters sslContextParameters;
    @UriParam(label = "security", secret = true)
    private String nickPassword;
    @UriParam(defaultValue = "5000")
    private long commandTimeout = 5000L;

    public IrcConfiguration() {
    }

    public IrcConfiguration(String hostname, String nickname, String displayname, String channels) {
        this(hostname, null, null, nickname, displayname, channels);
    }

    public IrcConfiguration(String hostname, String username, String password, String nickname, String displayname,
                            String channels) {
        this.channels = channels;
        this.hostname = hostname;
        this.username = username;
        this.password = password;
        this.nickname = nickname;
        this.realname = displayname;
    }

    public IrcConfiguration copy() {
        try {
            return (IrcConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public String getCacheKey() {
        return hostname + ":" + nickname;
    }

    /*
     * Return space separated list of channel names without pwd
     */
    public String getSpaceSeparatedChannelNames() {
        StringBuilder retval = new StringBuilder();
        for (IrcChannel channel : channelList) {
            retval.append(retval.isEmpty() ? "" : " ").append(channel.getName());
        }
        return retval.toString();
    }

    public void configure(String uriStr) throws URISyntaxException {
        // fix provided URI and handle that we can use # to indicate the IRC room
        if (uriStr.startsWith("ircs")) {
            setUsingSSL(true);
            if (!uriStr.startsWith("ircs://")) {
                uriStr = uriStr.replace("ircs:", "ircs://");
            }
        } else if (!uriStr.startsWith("irc://")) {
            uriStr = uriStr.replace("irc:", "irc://");
        }

        if (uriStr.contains("?")) {
            uriStr = StringHelper.before(uriStr, "?");
        }

        URI uri = new URI(uriStr);

        // Because we can get a "sanitized" URI, we need to deal with the situation where the
        // user info includes the username and password together or else we get a mangled username
        // that includes the user's secret being sent to the server.
        String userInfo = uri.getUserInfo();
        String username = null;
        String password = null;
        if (userInfo != null) {
            int colonIndex = userInfo.indexOf(':');
            if (colonIndex != -1) {
                username = userInfo.substring(0, colonIndex);
                password = userInfo.substring(colonIndex + 1);
            } else {
                username = userInfo;
            }
        }

        if (uri.getPort() != -1) {
            setPorts(new int[] { uri.getPort() });
            setPort(uri.getPort());
        }

        setNickname(username);
        setUsername(username);
        setRealname(username);
        setPassword(password);
        setHostname(uri.getHost());

        String path = uri.getPath();
        if (path != null && !path.isEmpty()) {
            LOG.warn("Channel {} should not be specified in the URI path. Use an @channel query parameter instead.", path);
        }
    }

    public List<IrcChannel> getChannelList() {
        return channelList;
    }

    public IrcChannel findChannel(String name) {
        for (IrcChannel channel : channelList) {
            if (channel.getName().equals(name)) {
                return channel;
            }
        }
        return null;
    }

    /**
     * The trust manager used to verify the SSL server's certificate.
     */
    public void setTrustManager(SSLTrustManager trustManager) {
        this.trustManager = trustManager;
    }

    public SSLTrustManager getTrustManager() {
        return trustManager;
    }

    public boolean getUsingSSL() {
        return usingSSL;
    }

    private void setUsingSSL(boolean usingSSL) {
        this.usingSSL = usingSSL;
    }

    public String getHostname() {
        return hostname;
    }

    /**
     * Hostname for the IRC chat server
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getPassword() {
        return password;
    }

    /**
     * The IRC server password.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    /**
     * The nickname used in chat.
     */
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getRealname() {
        return realname;
    }

    /**
     * The IRC user's actual name.
     */
    public void setRealname(String realname) {
        this.realname = realname;
    }

    public String getUsername() {
        return username;
    }

    /**
     * Comma separated list of IRC channels.
     */
    public String getChannels() {
        return channels;
    }

    public void setChannels(String channels) {
        this.channels = channels;
        createChannels();
    }

    /**
     * Comma separated list of keys for channels.
     */
    public String getKeys() {
        return keys;
    }

    public void setKeys(String keys) {
        this.keys = keys;
        createChannels();
    }

    private void createChannels() {
        channelList.clear();

        if (channels == null) {
            return;
        }

        String[] chs = channels.split(",");
        String[] ks = keys != null ? keys.split(",") : null;

        int count = chs.length;
        for (int i = 0; i < count; i++) {
            String channel = chs[i].trim();
            String key = ks != null && ks.length > i ? ks[i].trim() : null;
            if (channel.startsWith("#") && !channel.startsWith("##")) {
                channel = channel.substring(1);
            }
            if (key != null && !key.isEmpty()) {
                channel += "!" + key;
            }
            channelList.add(createChannel(channel));
        }
    }

    /**
     * The IRC server user name.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public int[] getPorts() {
        return ports;
    }

    /**
     * Port numbers for the IRC chat server
     */
    public void setPorts(int[] ports) {
        this.ports = ports;
    }

    public int getPort() {
        return port;
    }

    /**
     * Port number for the IRC chat server. If no port is configured then a default port of either 6667, 6668 or 6669 is
     * used.
     */
    public void setPort(int port) {
        this.port = port;
    }

    public boolean isPersistent() {
        return persistent;
    }

    /**
     * Use persistent messages.
     *
     * @deprecated not in use
     */
    @Deprecated
    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    public boolean isColors() {
        return colors;
    }

    /**
     * Whether or not the server supports color codes.
     */
    public void setColors(boolean colors) {
        this.colors = colors;
    }

    public boolean isOnNick() {
        return onNick;
    }

    /**
     * Handle nickname change events.
     */
    public void setOnNick(boolean onNick) {
        this.onNick = onNick;
    }

    public boolean isOnQuit() {
        return onQuit;
    }

    /**
     * Handle user quit events.
     */
    public void setOnQuit(boolean onQuit) {
        this.onQuit = onQuit;
    }

    public boolean isOnJoin() {
        return onJoin;
    }

    /**
     * Handle user join events.
     */
    public void setOnJoin(boolean onJoin) {
        this.onJoin = onJoin;
    }

    public boolean isOnKick() {
        return onKick;
    }

    /**
     * Handle kick events.
     */
    public void setOnKick(boolean onKick) {
        this.onKick = onKick;
    }

    public boolean isOnMode() {
        return onMode;
    }

    /**
     * Handle mode change events.
     */
    public void setOnMode(boolean onMode) {
        this.onMode = onMode;
    }

    public boolean isOnPart() {
        return onPart;
    }

    /**
     * Handle user part events.
     */
    public void setOnPart(boolean onPart) {
        this.onPart = onPart;
    }

    public boolean isOnReply() {
        return onReply;
    }

    /**
     * Whether or not to handle general responses to commands or informational messages.
     */
    public void setOnReply(boolean onReply) {
        this.onReply = onReply;
    }

    public boolean isOnTopic() {
        return onTopic;
    }

    /**
     * Handle topic change events.
     */
    public void setOnTopic(boolean onTopic) {
        this.onTopic = onTopic;
    }

    public boolean isOnPrivmsg() {
        return onPrivmsg;
    }

    /**
     * Handle private message events.
     */
    public void setOnPrivmsg(boolean onPrivmsg) {
        this.onPrivmsg = onPrivmsg;
    }

    public boolean isAutoRejoin() {
        return autoRejoin;
    }

    /**
     * Whether to auto re-join when being kicked
     */
    public void setAutoRejoin(boolean autoRejoin) {
        this.autoRejoin = autoRejoin;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * Used for configuring security using SSL. Reference to a org.apache.camel.support.jsse.SSLContextParameters in the
     * Registry. This reference overrides any configured SSLContextParameters at the component level. Note that this
     * setting overrides the trustManager option.
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    /**
     * Your IRC server nickname password.
     */
    public String getNickPassword() {
        return nickPassword;
    }

    public void setNickPassword(String nickPassword) {
        this.nickPassword = nickPassword;
    }

    /**
     * Delay in milliseconds before sending commands after the connection is established.
     *
     * @param timeout timeout value in milliseconds
     */
    public void setCommandTimeout(long timeout) {
        this.commandTimeout = timeout;
    }

    public long getCommandTimeout() {
        return commandTimeout;
    }

    public boolean isNamesOnJoin() {
        return namesOnJoin;
    }

    /**
     * Sends <code>NAMES</code> command to channel after joining it.<br>
     * {@link #onReply} has to be <code>true</code> in order to process the result which will have the header value
     * <code>irc.num = '353'</code>.
     */
    public void setNamesOnJoin(boolean namesOnJoin) {
        this.namesOnJoin = namesOnJoin;
    }

    @Override
    public String toString() {
        return "IrcConfiguration[hostname: " + hostname + ", ports=" + Arrays.toString(ports) + ", username=" + username
               + "]";
    }

    private static IrcChannel createChannel(String channelInfo) {
        String[] pair = channelInfo.split("!");
        return new IrcChannel(pair[0], pair.length > 1 ? pair[1] : null);
    }

    public static String sanitize(String uri) {
        //symbol # has to be encoded. otherwise value after '#' won't be propagated into parameters
        return uri.replace("#", "%23");
    }
}
