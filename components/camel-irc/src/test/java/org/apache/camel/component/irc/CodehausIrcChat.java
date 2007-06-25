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

import org.schwering.irc.lib.IRCConnection;
import org.schwering.irc.lib.IRCEventAdapter;
import org.schwering.irc.lib.IRCUser;
import org.schwering.irc.lib.IRCModeParser;

import java.io.IOException;

/**
 * @version $Revision: 1.1 $
 */
public class CodehausIrcChat {
    public static void main(String[] args) throws InterruptedException {
        final IrcConfiguration config = new IrcConfiguration("irc.codehaus.org", "camel-irc", "Camel IRC Component", "#camel-test");

        final IRCConnection conn = new IRCConnection(config.getHostname(), config.getPorts(), config.getPassword(), config.getNickname(), config.getUsername(), config.getRealname());

        conn.addIRCEventListener(new IRCEventAdapter() {

            @Override
            public void onRegistered() {
                super.onRegistered();
                System.out.println("onRegistered");
            }

            @Override
            public void onDisconnected() {
                super.onDisconnected();
                System.out.println("onDisconnected");
            }

            @Override
            public void onMode(String string, IRCUser ircUser, IRCModeParser ircModeParser) {
                super.onMode(string, ircUser, ircModeParser);
                System.out.println("onMode.string = " + string);
                System.out.println("onMode.ircUser = " + ircUser);
                System.out.println("onMode.ircModeParser = " + ircModeParser);
            }

            @Override
            public void onMode(IRCUser ircUser, String string, String string1) {
                super.onMode(ircUser, string, string1);
                System.out.println("onMode.ircUser = " + ircUser);
                System.out.println("onMode.string = " + string);
                System.out.println("onMode.string1 = " + string1);
            }

            @Override
            public void onPing(String string) {
                super.onPing(string);
                System.out.println("onPing.string = " + string);
            }

            @Override
            public void onError(String string) {
                System.out.println("onError.string = " + string);
            }

            @Override
            public void onError(int i, String string) {
                super.onError(i, string);
                System.out.println("onError.i = " + i);
                System.out.println("onError.string = " + string);
            }

            @Override
            public void unknown(String string, String string1, String string2, String string3) {
                super.unknown(string, string1, string2, string3);
                System.out.println("unknown.string = " + string);
                System.out.println("unknown.string1 = " + string1);
                System.out.println("unknown.string2 = " + string2);
                System.out.println("unknown.string3 = " + string3);
            }
        });
        conn.setEncoding("UTF-8");
        //conn.setDaemon(true);
        conn.setColors(false);
        conn.setPong(true);

        try {
            conn.connect();
        } catch (IOException e) {
            e.printStackTrace();
        }
//        while (!conn.isConnected()) {
//            Thread.sleep(1000);
//            System.out.println("Sleeping");
//        }
        System.out.println("Connected");
        //conn.send("/JOIN #camel-test");

        //System.out.println("Joining Channel: " + config.getTarget());
        conn.doJoin(config.getTarget());

        conn.doPrivmsg("#camel-test", "hi!");
        Thread.sleep(Integer.MAX_VALUE);
    }
}
