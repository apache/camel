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

import java.util.Dictionary;
import java.util.List;

import junit.framework.TestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Test;

public class IrcConfigurationTest extends TestCase {

    @Test
    public void testConfigureFormat1() throws Exception {

        CamelContext camel = new DefaultCamelContext();
        IrcComponent component = new IrcComponent(camel);

        // irc:nick@host[:port]/#room[?options]
        IrcEndpoint endpoint = (IrcEndpoint) component.createEndpoint("irc://camelbot@irc.freenode.net/#camel");
        IrcConfiguration conf = endpoint.getConfiguration();
        assertEquals("camelbot", conf.getNickname());
        assertEquals("irc.freenode.net", conf.getHostname());
        List<String> channels = conf.getChannels();
        assertEquals(1, channels.size());
        assertEquals("#camel", channels.get(0));
    }

    @Test
    public void testConfigureFormat2() throws Exception {

        CamelContext camel = new DefaultCamelContext();
        IrcComponent component = new IrcComponent(camel);

        // irc:nick@host[:port]/#room[?options]
        IrcEndpoint endpoint = (IrcEndpoint) component.createEndpoint("irc://camelbot@irc.freenode.net?channels=#camel");

        IrcConfiguration conf = endpoint.getConfiguration();
        assertEquals("camelbot", conf.getNickname());
        assertEquals("irc.freenode.net", conf.getHostname());
        List<String> channels = conf.getChannels();
        assertEquals(1, channels.size());
        assertEquals("#camel", channels.get(0));
    }

    @Test
    public void testConfigureFormat3() throws Exception {

        CamelContext camel = new DefaultCamelContext();
        IrcComponent component = new IrcComponent(camel);

        // irc:nick@host[:port]/#room[?options]
        IrcEndpoint endpoint = (IrcEndpoint) component.createEndpoint("irc://irc.freenode.net?channels=#camel&nickname=camelbot");

        IrcConfiguration conf = endpoint.getConfiguration();
        assertEquals("camelbot", conf.getNickname());
        assertEquals("irc.freenode.net", conf.getHostname());
        List<String> channels = conf.getChannels();
        assertEquals(1, channels.size());
        assertEquals("#camel", channels.get(0));
    }

    @Test
    public void testConfigureFormat4() throws Exception {

        CamelContext camel = new DefaultCamelContext();
        IrcComponent component = new IrcComponent(camel);

        // irc:nick@host[:port]/#room[?options]
        IrcEndpoint endpoint = (IrcEndpoint) component.createEndpoint("irc://irc.freenode.net?keys=,foo&channels=#camel,#smx&nickname=camelbot");

        IrcConfiguration conf = endpoint.getConfiguration();
        assertEquals("camelbot", conf.getNickname());
        assertEquals("irc.freenode.net", conf.getHostname());
        List<String> channels = conf.getChannels();
        assertEquals(2, channels.size());
        assertEquals("#camel", channels.get(0));
        Dictionary<String, String> keys = conf.getKeys();
        assertEquals(2, keys.size());
        assertEquals("foo", conf.getKey("#smx"));
    }

    @Test
    public void testConfigureFormat5() throws Exception {

        CamelContext camel = new DefaultCamelContext();
        IrcComponent component = new IrcComponent(camel);

        // irc:nick@host[:port]/#room[?options]
        IrcEndpoint  endpoint = (IrcEndpoint) component.
        createEndpoint("irc://badnick@irc.freenode.net?keys=foo,&channels=#camel,#smx&realname=Camel Bot&nickname=camelbot");

        IrcConfiguration conf = endpoint.getConfiguration();
        assertEquals("camelbot", conf.getNickname());
        assertEquals("irc.freenode.net", conf.getHostname());
        List<String> channels = conf.getChannels();
        assertEquals(2, channels.size());
        assertEquals("#camel", channels.get(0));
        Dictionary<String, String> keys = conf.getKeys();
        assertEquals(1, keys.size());
        assertEquals("foo", conf.getKey("#camel"));
        assertEquals("Camel Bot", conf.getRealname());
    }

    @Test
    public void testConfigureFormat6() throws Exception {

        CamelContext camel = new DefaultCamelContext();
        IrcComponent component = new IrcComponent(camel);

        // irc:nick@host[:port]/#room[?options]
        IrcEndpoint  endpoint = (IrcEndpoint) component.
        createEndpoint("irc://badnick@irc.freenode.net?keys=foo,bar&channels=#camel,#smx&realname=Camel Bot&nickname=camelbot");

        IrcConfiguration conf = endpoint.getConfiguration();
        assertEquals("camelbot", conf.getNickname());
        assertEquals("irc.freenode.net", conf.getHostname());
        List<String> channels = conf.getChannels();
        assertEquals(2, channels.size());
        assertEquals("#camel", channels.get(0));
        Dictionary<String, String> keys = conf.getKeys();
        assertEquals(2, keys.size());
        assertEquals("foo", conf.getKey("#camel"));
        assertEquals("bar", conf.getKey("#smx"));
        assertEquals("Camel Bot", conf.getRealname());
    }

}
