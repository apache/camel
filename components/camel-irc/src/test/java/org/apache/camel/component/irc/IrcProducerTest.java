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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.junit.Before;
import org.junit.Test;
import org.schwering.irc.lib.IRCConnection;
import org.schwering.irc.lib.IRCEventAdapter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrcProducerTest {

    private IRCConnection connection;
    private IrcEndpoint endpoint;
    private IrcConfiguration configuration;
    private IrcProducer producer;
    private IRCEventAdapter listener;
    private Exchange exchange;
    private Message message;

    @Before
    public void doSetup() {
        connection = mock(IRCConnection.class);
        endpoint = mock(IrcEndpoint.class);
        configuration = mock(IrcConfiguration.class);
        listener = mock(IRCEventAdapter.class);
        exchange = mock(Exchange.class);
        message = mock(Message.class);

        List<IrcChannel> channels = new ArrayList<IrcChannel>();
        channels.add(new IrcChannel("#chan1", null));
        channels.add(new IrcChannel("#chan2", "chan2key"));

        when(configuration.getChannels()).thenReturn(channels);
        when(endpoint.getConfiguration()).thenReturn(configuration);

        producer = new IrcProducer(endpoint, connection);
        producer.setListener(listener);
    }

    @Test
    public void doStopTest() throws Exception {
        producer.doStop();
        verify(connection).doPart("#chan1");
        verify(connection).doPart("#chan2");
        verify(connection).removeIRCEventListener(listener);
    }

    @Test
    public void doStartTest() throws Exception {
        producer.doStart();

        verify(connection).addIRCEventListener(listener);
        verify(endpoint).joinChannels();
    }

    @Test
    public void processTest() throws Exception {

        when(connection.isConnected()).thenReturn(true);
        when(exchange.getIn()).thenReturn(message);
        when(message.getBody(String.class)).thenReturn("PART foo");
        when(message.getHeader(IrcConstants.IRC_TARGET, String.class)).thenReturn("bottest");

        producer.process(exchange);
        verify(connection).send("PART foo");

        when(message.getBody(String.class)).thenReturn("foo");

        producer.process(exchange);
        verify(connection).doPrivmsg("bottest", "foo");

        when(message.getHeader(IrcConstants.IRC_TARGET, String.class)).thenReturn(null);

        producer.process(exchange);
        verify(connection).doPrivmsg("#chan1", "foo");
        verify(connection).doPrivmsg("#chan2", "foo");
    }

    @Test (expected = RuntimeCamelException.class)
    public void processTestException() throws Exception {

        when(exchange.getIn()).thenReturn(message);
        when(message.getBody(String.class)).thenReturn("PART foo");
        when(message.getHeader(IrcConstants.IRC_TARGET, String.class)).thenReturn("bottest");

        when(connection.isConnected()).thenReturn(false);

        producer.process(exchange);
    }
}
