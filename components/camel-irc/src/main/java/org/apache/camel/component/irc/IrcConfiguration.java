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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.ObjectHelper;

import org.schwering.irc.lib.ssl.SSLTrustManager;
import org.schwering.irc.lib.ssl.SSLDefaultTrustManager;

public class IrcConfiguration implements Cloneable {
    private String target;
    private String hostname;
    private String password;
    private String nickname;
    private String realname;
    private String username;
    private SSLTrustManager trustManager = new SSLDefaultTrustManager();
    private boolean usingSSL = false;
    private boolean persistent = true;
    private boolean colors = true;
    private boolean onNick = true;
    private boolean onQuit = true;
    private boolean onJoin = true;
    private boolean onKick = true;
    private boolean onMode = true;
    private boolean onPart = true;
    private boolean onTopic = true;
    private boolean onPrivmsg = true;
    private int[] ports = {6667, 6668, 6669};
    
    public IrcConfiguration() {
    }

    public IrcConfiguration(String hostname, String nickname, String displayname, String target) {
        this.target = target;
        this.hostname = hostname;
        this.nickname = nickname;
        this.username = nickname;
        this.realname = displayname;
    }

    public IrcConfiguration(String hostname, String username, String password, String nickname, String displayname, String target) {
        this.target = target;
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
            uriStr = ObjectHelper.before(uriStr, "?");
        }
        
        URI uri = new URI(uriStr);

        setNickname(uri.getUserInfo());
        setUsername(uri.getUserInfo());
        setRealname(uri.getUserInfo());
        setHostname(uri.getHost());

        if (uri.getFragment() == null || uri.getFragment().length() == 0) {
            throw new RuntimeCamelException("The IRC channel name is required but not configured");
        }

        String channel = uri.getFragment();

        setTarget("#" + channel);
    }

    public void setTrustManager(SSLTrustManager trustManager)
    {
        this.trustManager = trustManager;
    }

    public SSLTrustManager getTrustManager()
    {
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

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
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

    public String toString() {
        return "IrcConfiguration[hostname: " + hostname + ", ports=" + Arrays.toString(ports) + ", target: " + target + ", username=" + username + "]";
    }
}
