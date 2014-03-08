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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.schwering.irc.lib.IRCConnection;
import org.schwering.irc.lib.IRCEventAdapter;
import org.schwering.irc.lib.IRCModeParser;
import org.schwering.irc.lib.IRCUser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version 
 */
public final class CodehausIrcChat {

    private static final Logger LOG = LoggerFactory.getLogger(CodehausIrcChat.class);

    private static final class CodehausIRCEventAdapter extends IRCEventAdapter {
        @Override
        public void onRegistered() {
            super.onRegistered();
            LOG.info("onRegistered");
        }

        @Override
        public void onDisconnected() {
            super.onDisconnected();
            LOG.info("onDisconnected");
        }

        @Override
        public void onMode(String string, IRCUser ircUser, IRCModeParser ircModeParser) {
            super.onMode(string, ircUser, ircModeParser);
            LOG.info("onMode.string = " + string);
            LOG.info("onMode.ircUser = " + ircUser);
            LOG.info("onMode.ircModeParser = " + ircModeParser);
        }

        @Override
        public void onMode(IRCUser ircUser, String string, String string1) {
            super.onMode(ircUser, string, string1);
            LOG.info("onMode.ircUser = " + ircUser);
            LOG.info("onMode.string = " + string);
            LOG.info("onMode.string1 = " + string1);
        }

        @Override
        public void onPing(String string) {
            super.onPing(string);
            LOG.info("onPing.string = " + string);
        }

        @Override
        public void onError(String string) {
            LOG.info("onError.string = " + string);
        }

        @Override
        public void onError(int i, String string) {
            super.onError(i, string);
            LOG.info("onError.i = " + i);
            LOG.info("onError.string = " + string);
        }

        @Override
        public void unknown(String string, String string1, String string2, String string3) {
            super.unknown(string, string1, string2, string3);
            LOG.info("unknown.string = " + string);
            LOG.info("unknown.string1 = " + string1);
            LOG.info("unknown.string2 = " + string2);
            LOG.info("unknown.string3 = " + string3);
        }
    }

    private CodehausIrcChat() {
    }

    public static void main(String[] args) throws InterruptedException {
        List<IrcChannel> channels = new ArrayList<IrcChannel>();
        channels.add(new IrcChannel("camel-test", null));
        final IrcConfiguration config = new IrcConfiguration("irc.codehaus.org", "camel-rc", "Camel IRC Component", channels);

        final IRCConnection conn = new IRCConnection(config.getHostname(), config.getPorts(), config.getPassword(), config.getNickname(), config.getUsername(), config.getRealname());

        conn.addIRCEventListener(new CodehausIRCEventAdapter());
        conn.setEncoding("UTF-8");
        // conn.setDaemon(true);
        conn.setColors(false);
        conn.setPong(true);

        try {
            conn.connect();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // while (!conn.isConnected()) {
        // Thread.sleep(1000);
        // LOG.info("Sleeping");
        // }
        LOG.info("Connected");
        // conn.send("/JOIN #camel-test");

        // LOG.info("Joining Channel: " + config.getTarget());

        for (IrcChannel channel : config.getChannels()) {
            conn.doJoin(channel.getName());
        }

        conn.doPrivmsg("#camel-test", "hi!");
        Thread.sleep(Integer.MAX_VALUE);
    }
}
