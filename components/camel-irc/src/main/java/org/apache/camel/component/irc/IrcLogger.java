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

import org.schwering.irc.lib.IRCEventAdapter;
import org.schwering.irc.lib.IRCModeParser;
import org.schwering.irc.lib.IRCUser;
import org.slf4j.Logger;

/**
 * A helper class which logs errors
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
        log.info("Server: {} - onDisconnected", server);
    }

    @Override
    public void onError(int num, String msg) {
        log.error("Server: {} - onError num={} msg=\"{}\"", server, num, msg);
    }

    @Override
    public void onError(String msg) {
        log.error("Server: {} - onError msg=\"{}\"", server, msg);
    }

    @Override
    public void onInvite(String chan, IRCUser user, String passiveNick) {
        log.debug("Server: {} - onInvite chan={} user={} passiveNick={}", server, chan, user, passiveNick);
    }

    @Override
    public void onJoin(String chan, IRCUser user) {
        log.debug("Server: {} - onJoin chan={} user={}", server, chan, user);
    }

    @Override
    public void onKick(String chan, IRCUser user, String passiveNick, String msg) {
        log.debug("Server: {} - onKick chan={} user={} passiveNick={} msg=\"{}\"", server, chan, user, passiveNick, msg);
    }

    @Override
    public void onMode(String chan, IRCUser user, IRCModeParser ircModeParser) {
        log.info("Server: {} - onMode chan={} user={} ircModeParser={}", server, chan, user, ircModeParser);
    }

    @Override
    public void onMode(IRCUser user, String passiveNick, String mode) {
        log.info("Server: {} - onMode user={} passiveNick={} mode={}", server, user, passiveNick, mode);
    }

    @Override
    public void onNick(IRCUser user, String newNick) {
        log.debug("Server: {} - onNick user={} newNick={}", server, user, newNick);
    }

    @Override
    public void onNotice(String target, IRCUser user, String msg) {
        log.debug("Server: {} - onNotice target={} user={} msg=\"{}\"", server, target, user, msg);
    }

    @Override
    public void onPart(String chan, IRCUser user, String msg) {
        log.debug("Server: {} - onPart chan={} user={} msg=\"{}\"", server, chan, user, msg);
    }

    @Override
    public void onPing(String ping) {
        log.info("Server: {} - onPing ping={}", server, ping);
    }

    @Override
    public void onPrivmsg(String target, IRCUser user, String msg) {
        log.debug("Server: {} - onPrivmsg target={} user={} msg=\"{}\"", server, target, user, msg);
    }

    @Override
    public void onQuit(IRCUser user, String msg) {
        log.debug("Server: {} - onQuit user={} msg=\"{}\"", server, user, msg);
    }

    @Override
    public void onRegistered() {
        log.info("Server: {} - onRegistered", server);
    }

    @Override
    public void onReply(int num, String value, String msg) {
        log.debug("Server: {} - onReply num={} value=\"{}\" msg=\"{}\"", server, num, value, msg);
    }

    @Override
    public void onTopic(String chan, IRCUser user, String topic) {
        log.debug("Server: {} - onTopic chan={} user={} topic={}", server, chan, user, topic);
    }

    @Override
    public void unknown(String prefix, String command, String middle, String trailing) {
        log.info("Server: {} - unknown prefix={} command={} middle={} trailing={}", server, prefix, command,
                middle, trailing);
    }
}
