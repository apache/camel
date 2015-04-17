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
package org.apache.camel.component.irc;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.schwering.irc.lib.ssl.SSLDefaultTrustManager;
import org.schwering.irc.lib.ssl.SSLTrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriParams
public class IrcConfiguration implements Cloneable {
    private static final Logger LOG = LoggerFactory.getLogger(IrcConfiguration.class);

    private List<IrcChannel> channels = new ArrayList<IrcChannel>();
    @UriPath @Metadata(required = "true")
    private String hostname;
    @UriPath
    private int port;
    private int[] ports = {6667, 6668, 6669};
    @UriParam
    private String password;
    @UriParam
    private String nickname;
    @UriParam
    private String realname;
    @UriParam
    private String username;
    private SSLTrustManager trustManager = new SSLDefaultTrustManager();
    private boolean usingSSL;
    @UriParam(defaultValue = "true")
    private boolean persistent = true;
    @UriParam(defaultValue = "true")
    private boolean colors = true;
    @UriParam(defaultValue = "true")
    private boolean onNick = true;
    @UriParam(defaultValue = "true")
    private boolean onQuit = true;
    @UriParam(defaultValue = "true")
    private boolean onJoin = true;
    @UriParam(defaultValue = "true")
    private boolean onKick = true;
    @UriParam(defaultValue = "true")
    private boolean onMode = true;
    @UriParam(defaultValue = "true")
    private boolean onPart = true;
    @UriParam
    private boolean onReply;
    @UriParam(defaultValue = "true")
    private boolean onTopic = true;
    @UriParam(defaultValue = "true")
    private boolean onPrivmsg = true;
    @UriParam(defaultValue = "true")
    private boolean autoRejoin = true;
    private SSLContextParameters sslContextParameters;

    public IrcConfiguration() {
    }

    public IrcConfiguration(String hostname, String nickname, String displayname, List<IrcChannel> channels) {
        this(hostname, null, null, nickname, displayname, channels);
    }

    public IrcConfiguration(String hostname, String username, String password, String nickname, String displayname, List<IrcChannel> channels) {
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
    public String getListOfChannels() {
        String retval = "";
        for (IrcChannel channel : channels) {
            retval += (retval.isEmpty() ? "" : " ") + channel.getName();
        }
        return retval;
    }

    public void configure(String uriStr) throws URISyntaxException, UnsupportedEncodingException  {
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
            uriStr = ObjectHelper.before(uriStr, "?");
        }

        URI uri = new URI(uriStr);

        // Because we can get a "sanitized" URI, we need to deal with the situation where the
        // user info includes the username and password together or else we get a mangled username
        // that includes the user's secret being sent to the server.
        String userInfo = uri.getUserInfo();
        String username = null;
        String password = null;
        if (userInfo != null) {
            int colonIndex = userInfo.indexOf(":");
            if (colonIndex != -1) {
                username = userInfo.substring(0, colonIndex);
                password = userInfo.substring(colonIndex + 1);
            } else {
                username = userInfo;
            }
        }
        
        if (uri.getPort() != -1) {
            setPorts(new int[] {uri.getPort()});
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

    public void setChannel(String channel) {
        channels.add(createChannel(channel));
    }

    public void setChannel(List<String> channels) {
        for (String ci : channels) {
            this.channels.add(createChannel(ci));
        }
    }

    public List<IrcChannel> getChannels() {
        return channels;
    }
    
    public IrcChannel findChannel(String name) {
        for (IrcChannel channel : channels) {
            if (channel.getName().equals(name)) {
                return channel;
            }
        }
        return null;
    }

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

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getRealname() {
        return realname;
    }

    public void setRealname(String realname) {
        this.realname = realname;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int[] getPorts() {
        return ports;
    }

    public void setPorts(int[] ports) {
        this.ports = ports;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    public boolean isColors() {
        return colors;
    }

    public void setColors(boolean colors) {
        this.colors = colors;
    }

    public boolean isOnNick() {
        return onNick;
    }

    public void setOnNick(boolean onNick) {
        this.onNick = onNick;
    }

    public boolean isOnQuit() {
        return onQuit;
    }

    public void setOnQuit(boolean onQuit) {
        this.onQuit = onQuit;
    }

    public boolean isOnJoin() {
        return onJoin;
    }

    public void setOnJoin(boolean onJoin) {
        this.onJoin = onJoin;
    }

    public boolean isOnKick() {
        return onKick;
    }

    public void setOnKick(boolean onKick) {
        this.onKick = onKick;
    }

    public boolean isOnMode() {
        return onMode;
    }

    public void setOnMode(boolean onMode) {
        this.onMode = onMode;
    }

    public boolean isOnPart() {
        return onPart;
    }

    public void setOnPart(boolean onPart) {
        this.onPart = onPart;
    }

    public boolean isOnReply() {
        return onReply;
    }

    public void setOnReply(boolean onReply) {
        this.onReply = onReply;
    }

    public boolean isOnTopic() {
        return onTopic;
    }

    public void setOnTopic(boolean onTopic) {
        this.onTopic = onTopic;
    }

    public boolean isOnPrivmsg() {
        return onPrivmsg;
    }

    public void setOnPrivmsg(boolean onPrivmsg) {
        this.onPrivmsg = onPrivmsg;
    }

    public boolean isAutoRejoin() {
        return autoRejoin;
    }

    public void setAutoRejoin(boolean autoRejoin) {
        this.autoRejoin = autoRejoin;
    }
    
    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public String toString() {
        return "IrcConfiguration[hostname: " + hostname + ", ports=" + Arrays.toString(ports) + ", username=" + username + "]";
    }
    
    private static IrcChannel createChannel(String channelInfo) {
        String[] pair = channelInfo.split("!");
        return new IrcChannel(pair[0], pair.length > 1 ? pair[1] : null);
    }

    @Deprecated
    public static String sanitize(String uri) {
        // may be removed in camel-3.0.0 
        // make sure it's an URL first
        int colon = uri.indexOf(':');
        if (colon != -1 && uri.indexOf("://") != colon) {
            uri = uri.substring(0, colon) + "://" + uri.substring(colon + 1);
        }

        try {
            URI u = new URI(UnsafeUriCharactersEncoder.encode(uri));
            String[] userInfo = u.getUserInfo() != null ? u.getUserInfo().split(":") : null;
            String username = userInfo != null ? userInfo[0] : null;
            String password = userInfo != null && userInfo.length > 1 ? userInfo[1] : null;

            String path = URLDecoder.decode(u.getPath() != null ? u.getPath() : "", "UTF-8");
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.startsWith("#") && !path.startsWith("##")) {
                path = path.substring(1);
            }

            Map<String, Object> parameters = URISupport.parseParameters(u);
            String user = (String)parameters.get("username");
            String nick = (String)parameters.get("nickname");
            // not specified in authority
            if (user != null) {
                if (username == null) {
                    username = user;
                } else if (!username.equals(user)) {
                    LOG.warn("Username specified twice in endpoint URI with different values. "
                        + "The userInfo value ('{}') will be used, paramter ('{}') ignored", username, user);
                }
                parameters.remove("username");
            }
            if (nick != null) {
                if (username == null) {
                    username = nick;
                }
                if (username.equals(nick)) {
                    parameters.remove("nickname");      // redundant
                }
            }
            if (username == null) {
                throw new RuntimeCamelException("IrcEndpoint URI with no user/nick specified is invalid");
            }

            String pwd = (String)parameters.get("password");
            if (pwd != null) {
                password = pwd;
                parameters.remove("password");
            }
            
            // Remove unneeded '#' channel prefixes per convention
            // and replace ',' separators and merge channel and key using convention "channel!key"
            List<String> cl = new ArrayList<String>();
            String channels = (String)parameters.get("channels");
            String keys =  (String)parameters.get("keys");
            keys = keys == null ? keys : keys + " ";    // if @keys ends with a ',' it will miss the last empty key after split(",")
            if (channels != null) {
                String[] chs = channels.split(",");
                String[] ks = keys != null ? keys.split(",") : null;
                parameters.remove("channels");
                int count = chs.length;
                if (ks != null) {
                    parameters.remove("keys");
                    if (!path.isEmpty()) {
                        LOG.warn("Specifying a channel '{}' in the URI path is ambiguous"
                            + " when @channels and @keys are provided and will be ignored", path);
                        path = "";
                    }
                    if (ks.length != chs.length) {
                        count = count < ks.length ? count : ks.length;
                        LOG.warn("Different count of @channels and @keys. Only the first {} are used.", count);
                    }
                }
                for (int i = 0; i < count; i++) {
                    String channel = chs[i].trim();
                    String key = ks != null ? ks[i].trim() : null;
                    if (channel.startsWith("#") && !channel.startsWith("##")) {
                        channel = channel.substring(1);
                    }
                    if (key != null && !key.isEmpty()) {
                        channel += "!" + key;
                    }
                    cl.add(channel);
                }
            } else {
                if (path.isEmpty()) {
                    LOG.warn("No channel specified for the irc endpoint");
                }
                cl.add(path);
            }
            parameters.put("channel", cl);

            StringBuilder sb = new StringBuilder();
            sb.append(u.getScheme());
            sb.append("://");
            sb.append(username);
            sb.append(password == null ? "" : ":" + password);
            sb.append("@");
            sb.append(u.getHost());
            sb.append(u.getPort() == -1 ? "" : ":" + u.getPort());
            // ignore the path we have it as a @channel now
            String query = formatQuery(parameters);
            if (!query.isEmpty()) {
                sb.append("?");
                sb.append(query);
            }
            // make things a bit more predictable
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }
    
    private static String formatQuery(Map<String, Object> params) {
        if (params == null || params.size() == 0) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, Object> pair : params.entrySet()) {
            Object value = pair.getValue();
            // the value may be a list since the same key has multiple values
            if (value instanceof List) {
                List<?> list = (List<?>)value;
                for (Object s : list) {
                    addQueryParameter(result, pair.getKey(), s);
                }
            } else {
                addQueryParameter(result, pair.getKey(), value);
            }
        }
        return result.toString();
    }
    
    private static void addQueryParameter(StringBuilder sb, String key, Object value) {
        sb.append(sb.length() == 0 ? "" : "&");
        sb.append(key);
        if (value != null) {
            String s = value.toString();
            sb.append(s.isEmpty() ? "" : "=" + UnsafeUriCharactersEncoder.encode(s));
        }
    }
}
