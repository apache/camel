/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.irc;

import org.apache.camel.RuntimeCamelException;

import java.net.URI;

public class IrcConfiguration implements Cloneable {
    String target;
    String hostname;
    String password;
    String nickname;
    String realname;
    String username;
    boolean persistent = true;
    boolean colors = true;
    boolean onNick = true;
    boolean onQuit = true;
    boolean onJoin = true;
    boolean onKick = true;
    boolean onMode = true;
    boolean onPart = true;
    boolean onTopic = true;
    boolean onPrivmsg = true;
    int[] ports = {6667, 6668, 6669};

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
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public String getCacheKey() {
        return hostname + ":" + nickname;
    }

    public void configure(URI uri) {
        setNickname(uri.getUserInfo());
        setUsername(uri.getUserInfo());
        setRealname(uri.getUserInfo());
        setHostname(uri.getHost());
        setTarget(uri.getPath().substring(1));
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
        return "IrcConfiguration{" +
                "target='" + target + '\'' +
                ", hostname='" + hostname + '\'' +
                ", password='" + password + '\'' +
                ", nickname='" + nickname + '\'' +
                ", realname='" + realname + '\'' +
                ", username='" + username + '\'' +
                ", persistent=" + persistent +
                ", colors=" + colors +
                ", onNick=" + onNick +
                ", onQuit=" + onQuit +
                ", onJoin=" + onJoin +
                ", onKick=" + onKick +
                ", onMode=" + onMode +
                ", onPart=" + onPart +
                ", onTopic=" + onTopic +
                ", onPrivmsg=" + onPrivmsg +
                ", ports=" + ports +
                '}';
    }
}
