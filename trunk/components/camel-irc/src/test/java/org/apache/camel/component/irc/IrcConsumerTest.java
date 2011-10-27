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

import org.apache.camel.Processor;
import org.junit.Before;
import org.junit.Test;
import org.schwering.irc.lib.IRCConnection;
import org.schwering.irc.lib.IRCEventAdapter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrcConsumerTest {

    private IRCConnection connection;
    private Processor processor;
    private IrcEndpoint endpoint;
    private IrcConfiguration configuration;
    private IrcConsumer consumer;
    private IRCEventAdapter listener;

    @Before
    public void doSetup() {
        connection = mock(IRCConnection.class);
        endpoint = mock(IrcEndpoint.class);
        processor = mock(Processor.class);
        configuration = mock(IrcConfiguration.class);
        listener = mock(IRCEventAdapter.class);

        List<IrcChannel> channels = new ArrayList<IrcChannel>();
        channels.add(new IrcChannel("#chan1", null));
        channels.add(new IrcChannel("#chan2", "chan2key"));

        when(configuration.getChannels()).thenReturn(channels);
        when(endpoint.getConfiguration()).thenReturn(configuration);

        consumer = new IrcConsumer(endpoint, processor, connection);
        consumer.setListener(listener);
    }

    @Test
    public void doStopTest() throws Exception {
        consumer.doStop();
        verify(connection).doPart("#chan1");
        verify(connection).doPart("#chan2");
        verify(connection).removeIRCEventListener(listener);
    }

    @Test
    public void doStartTest() throws Exception {
        consumer.doStart();

        verify(connection).addIRCEventListener(listener);
        verify(endpoint).joinChannels();
    }
}
