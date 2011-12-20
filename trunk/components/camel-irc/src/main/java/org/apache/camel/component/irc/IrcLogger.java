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

import org.schwering.irc.lib.IRCEventAdapter;
import org.schwering.irc.lib.IRCModeParser;
import org.schwering.irc.lib.IRCUser;
import org.slf4j.Logger;

/**
 * A helper class which logs errors
 *
 * @version 
 */
public class IrcLogger extends IRCEventAdapter {
    private Logger log;
    private String server;

    public IrcLogger(Logger log, String server) {
        this.log = log;
        this.server = server;
    }

    @Override
    public void onDisconnected() {
        log.info("Server: " + server + " - onDisconnected");
    }

    @Override
    public void onError(int num, String msg) {
        log.error("Server: " + server + " - onError num=" + num + " msg=\"" + msg + "\"");
    }

    @Override
    public void onError(String msg) {
        log.error("Server: " + server + " - onError msg=\"" + msg + "\"");
    }

    @Override
    public void onInvite(String chan, IRCUser user, String passiveNick) {
        log.debug("Server: " + server + " - onInvite chan=" + chan + " user=" + user + " passiveNick=" + passiveNick);
    }

    @Override
    public void onJoin(String chan, IRCUser user) {
        log.debug("Server: " + server + " - onJoin chan=" + chan + " user=" + user);
    }

    @Override
    public void onKick(String chan, IRCUser user, String passiveNick, String msg) {
        log.debug("Server: " + server + " - onKick chan=" + chan + " user=" + user + " passiveNick=" + passiveNick + " msg=\"" + msg + "\"");
    }

    @Override
    public void onMode(String chan, IRCUser user, IRCModeParser ircModeParser) {
        log.info("Server: " + server + " - onMode chan=" + chan + " user=" + user + " ircModeParser=" + ircModeParser);
    }

    @Override
    public void onMode(IRCUser user, String passiveNick, String mode) {
        log.info("Server: " + server + " - onMode user=" + user + " passiveNick=" + passiveNick + " mode=" + mode);
    }

    @Override
    public void onNick(IRCUser user, String newNick) {
        log.debug("Server: " + server + " - onNick user=" + user + " newNick=" + newNick);
    }

    @Override
    public void onNotice(String target, IRCUser user, String msg) {
        log.debug("Server: " + server + " - onNotice target=" + target + " user=" + user + " msg=\"" + msg + "\"");
    }

    @Override
    public void onPart(String chan, IRCUser user, String msg) {
        log.debug("Server: " + server + " - onPart chan=" + chan + " user=" + user + " msg=\"" + msg + "\"");
    }

    @Override
    public void onPing(String ping) {
        log.info("Server: " + server + " - onPing ping=" + ping);
    }

    @Override
    public void onPrivmsg(String target, IRCUser user, String msg) {
        log.debug("Server: " + server + " - onPrivmsg target=" + target + " user=" + user + " msg=\"" + msg + "\"");
    }

    @Override
    public void onQuit(IRCUser user, String msg) {
        log.debug("Server: " + server + " - onQuit user=" + user + " msg=\"" + msg + "\"");
    }

    @Override
    public void onRegistered() {
        log.info("Server: " + server + " - onRegistered");
    }

    @Override
    public void onReply(int num, String value, String msg) {
        log.debug("Server: " + server + " - onReply num=" + num + " value=\"" + value + "\" msg=\"" + msg + "\"");
    }    

    @Override
    public void onTopic(String chan, IRCUser user, String topic) {
        log.debug("Server: " + server + " - onTopic chan=" + chan + " user=" + user + " topic=" + topic);
    }

    @Override
    public void unknown(String prefix, String command, String middle, String trailing) {
        log.info("Server: " + server + " - unknown prefix=" + prefix + " command=" + command + " middle=" + middle + " trailing=" + trailing);
    }
}
