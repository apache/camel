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
import java.util.List;

import org.apache.camel.Endpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class IrcConfigurationTest extends CamelTestSupport {

    @Test
    public void testInvalidUriConversion() throws Exception {
        // Note: valid URIs won't throw on new URI(endpoint.getEndpointUri())
        
        String deprecate;
        String sanitized;
        Endpoint endpoint;
        IrcComponent component = context.getComponent("irc", IrcComponent.class);

        // Test conversion of the URI path to @channel parameter (drop the '#')
        deprecate = "irc://camelbot@irc.freenode.net:1234/#camel";
        sanitized = "irc://camelbot@irc.freenode.net:1234?channel=camel";
        endpoint = component.createEndpoint(deprecate); 
        assertEquals(sanitized, endpoint.getEndpointUri());
        assertNotNull(new URI(endpoint.getEndpointUri()));

        // Test conversion of the URI path to @channel parameter (encode the double '##')
        deprecate = "irc://camelbot@irc.freenode.net/##camel";
        sanitized = "irc://camelbot@irc.freenode.net?channel=%23%23camel";
        endpoint = component.createEndpoint(deprecate); 
        assertEquals(sanitized, endpoint.getEndpointUri());
        assertNotNull(new URI(endpoint.getEndpointUri()));

        // Test drop path and both path and @channels are specified
        deprecate = "irc://camelbot@irc.freenode.net/#karaf?channels=#camel,#cxf";
        sanitized = "irc://camelbot@irc.freenode.net?channel=camel&channel=cxf";
        endpoint = component.createEndpoint(deprecate); 
        assertEquals(sanitized, endpoint.getEndpointUri());
        assertNotNull(new URI(endpoint.getEndpointUri()));

        // Test multiple channels, no keys
        deprecate = "irc://camelbot@irc.freenode.net?channels=#camel,#cxf";
        sanitized = "irc://camelbot@irc.freenode.net?channel=camel&channel=cxf";
        endpoint = component.createEndpoint(deprecate); 
        assertEquals(sanitized, endpoint.getEndpointUri());
        assertNotNull(new URI(endpoint.getEndpointUri()));

        // Test multiple channels, with keys
        deprecate = "irc://camelbot@irc.freenode.net?channels=#camel,#cxf&keys=foo,bar";
        sanitized = "irc://camelbot@irc.freenode.net?channel=camel!foo&channel=cxf!bar";
        endpoint = component.createEndpoint(deprecate); 
        assertEquals(sanitized, endpoint.getEndpointUri());
        assertNotNull(new URI(endpoint.getEndpointUri()));

        // Test multiple channels, with keys (last key is empty)
        deprecate = "irc://camelbot@irc.freenode.net?channels=#camel,#cxf&keys=foo,";
        sanitized = "irc://camelbot@irc.freenode.net?channel=camel!foo&channel=cxf";
        endpoint = component.createEndpoint(deprecate); 
        assertEquals(sanitized, endpoint.getEndpointUri());
        assertNotNull(new URI(endpoint.getEndpointUri()));

        // Test multiple channels, deprecated @username
        deprecate = "irc://irc.freenode.net?keys=,foo&channels=#camel,#cxf&username=camelbot";
        sanitized = "irc://camelbot@irc.freenode.net?channel=camel&channel=cxf!foo";
        endpoint = component.createEndpoint(deprecate); 
        assertEquals(sanitized, endpoint.getEndpointUri());
        assertNotNull(new URI(endpoint.getEndpointUri()));

        // Test multiple channels, deprecated @username and @password
        deprecate = "irc://irc.freenode.net?keys=,foo&channels=#camel,#cxf&username=camelbot&password=secret";
        sanitized = "irc://camelbot:secret@irc.freenode.net?channel=camel&channel=cxf!foo";
        endpoint = component.createEndpoint(deprecate); 
        assertEquals(sanitized, endpoint.getEndpointUri());
        assertNotNull(new URI(endpoint.getEndpointUri()));

        // Test multiple channels, drop @nickname same as @username
        deprecate = "irc://irc.freenode.net?channels=#camel,#cxf&nickname=camelbot";
        sanitized = "irc://camelbot@irc.freenode.net?channel=camel&channel=cxf";
        endpoint = component.createEndpoint(deprecate); 
        assertEquals(sanitized, endpoint.getEndpointUri());
        assertNotNull(new URI(endpoint.getEndpointUri()));

        // Test with encoding of @realname
        deprecate = "irc://user@irc.freenode.net?keys=foo,&channels=#camel,#cxf&realname=Camel Bot&username=user&nickname=camelbot";
        sanitized = "irc://user@irc.freenode.net?realname=Camel%20Bot&nickname=camelbot&channel=camel!foo&channel=cxf";
        endpoint = component.createEndpoint(deprecate);
        assertEquals(sanitized, endpoint.getEndpointUri());
        assertNotNull(new URI(endpoint.getEndpointUri()));
    }

    @Test
    public void testConfigureFormat1() throws Exception {
        IrcComponent component = context.getComponent("irc", IrcComponent.class);

        // irc:nick@host[:port]/#room[?options]
        IrcEndpoint endpoint = (IrcEndpoint) component.createEndpoint("irc://camelbot@irc.freenode.net/#camel");
        IrcConfiguration conf = endpoint.getConfiguration();
        assertEquals("camelbot", conf.getNickname());
        assertEquals("irc.freenode.net", conf.getHostname());
        List<IrcChannel> channels = conf.getChannels();
        assertEquals(1, channels.size());
        assertEquals("#camel", channels.get(0).getName());
    }

    @Test
    public void testConfigureFormat2() throws Exception {
        IrcComponent component = context.getComponent("irc", IrcComponent.class);

        // irc:nick@host[:port]/#room[?options]
        IrcEndpoint endpoint = (IrcEndpoint) component.createEndpoint("irc://camelbot@irc.freenode.net?channels=#camel");

        IrcConfiguration conf = endpoint.getConfiguration();
        assertEquals("camelbot", conf.getNickname());
        assertEquals("irc.freenode.net", conf.getHostname());
        List<IrcChannel> channels = conf.getChannels();
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
        List<IrcChannel> channels = conf.getChannels();
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
        List<IrcChannel> channels = conf.getChannels();
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
        List<IrcChannel> channels = conf.getChannels();
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
        List<IrcChannel> channels = conf.getChannels();
        assertEquals(2, channels.size());
        assertNotNull(conf.findChannel("#camel"));
        assertNotNull(conf.findChannel("#smx"));
        assertEquals("foo", conf.findChannel("#camel").getKey());
        assertEquals("bar", conf.findChannel("#smx").getKey());
        assertEquals("Camel Bot", conf.getRealname());
    }
}
