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

import java.util.List;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class IrcConfigurationTest extends CamelTestSupport {

    @Test
    public void testConfigureFormat2() throws Exception {
        IrcComponent component = context.getComponent("irc", IrcComponent.class);

        // irc:nick@host[:port]/#room[?options]
        IrcEndpoint endpoint = (IrcEndpoint) component.createEndpoint("irc://camelbot@irc.freenode.net?channels=#camel");

        IrcConfiguration conf = endpoint.getConfiguration();
        assertEquals("camelbot", conf.getNickname());
        assertEquals("irc.freenode.net", conf.getHostname());
        List<IrcChannel> channels = conf.getChannelList();
        assertEquals(1, channels.size());
        assertEquals("#camel", channels.get(0).getName());
    }

    @Test
    public void testConfigureFormat3() throws Exception {
        IrcComponent component = context.getComponent("irc", IrcComponent.class);

        // irc:nick@host[:port]/#room[?options]
        IrcEndpoint endpoint = (IrcEndpoint) component.createEndpoint("irc://irc.freenode.net?channels=#camel&nickname=camelbot");

        IrcConfiguration conf = endpoint.getConfiguration();
        assertEquals("camelbot", conf.getNickname());
        assertEquals("irc.freenode.net", conf.getHostname());
        List<IrcChannel> channels = conf.getChannelList();
        assertEquals(1, channels.size());
        assertEquals("#camel", channels.get(0).getName());
    }

    @Test
    public void testConfigureFormat4() throws Exception {
        IrcComponent component = context.getComponent("irc", IrcComponent.class);

        // irc:nick@host[:port]/#room[?options]
        IrcEndpoint endpoint = (IrcEndpoint) component.createEndpoint("irc://irc.freenode.net?keys=,foo&channels=%23camel,%23smx&nickname=camelbot");

        IrcConfiguration conf = endpoint.getConfiguration();
        assertEquals("camelbot", conf.getNickname());
        assertEquals("irc.freenode.net", conf.getHostname());
        List<IrcChannel> channels = conf.getChannelList();
        assertEquals(2, channels.size());
        assertNotNull(conf.findChannel("#camel"));
        assertNotNull(conf.findChannel("#smx"));
        assertEquals("foo", conf.findChannel("#smx").getKey());
    }

    @Test
    public void testConfigureFormat5() throws Exception {
        IrcComponent component = context.getComponent("irc", IrcComponent.class);

        // irc:nick@host[:port]/#room[?options]
        IrcEndpoint  endpoint = (IrcEndpoint) component.
        createEndpoint("irc://badnick@irc.freenode.net?keys=foo,&channels=#camel,#smx&realname=Camel+Bot&nickname=camelbot");

        IrcConfiguration conf = endpoint.getConfiguration();
        assertEquals("camelbot", conf.getNickname());
        assertEquals("irc.freenode.net", conf.getHostname());
        List<IrcChannel> channels = conf.getChannelList();
        assertEquals(2, channels.size());
        assertNotNull(conf.findChannel("#camel"));
        assertEquals("foo", conf.findChannel("#camel").getKey());
        assertEquals("Camel Bot", conf.getRealname());
    }

    @Test
    public void testConfigureFormat6() throws Exception {
        IrcComponent component = context.getComponent("irc", IrcComponent.class);

        // irc:nick@host[:port]/#room[?options]
        IrcEndpoint  endpoint = (IrcEndpoint) component.
        createEndpoint("irc://badnick@irc.freenode.net?keys=foo,bar&channels=#camel,#smx&realname=Camel+Bot&nickname=camelbot");

        IrcConfiguration conf = endpoint.getConfiguration();
        assertEquals("camelbot", conf.getNickname());
        assertEquals("irc.freenode.net", conf.getHostname());
        List<IrcChannel> channels = conf.getChannelList();
        assertEquals(2, channels.size());
        assertNotNull(conf.findChannel("#camel"));
        assertNotNull(conf.findChannel("#smx"));
        assertEquals("foo", conf.findChannel("#camel").getKey());
        assertEquals("bar", conf.findChannel("#smx").getKey());
        assertEquals("Camel Bot", conf.getRealname());
    }
}
