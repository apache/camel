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
package org.apache.camel.component.couchdb;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.lightcouch.Changes;
import org.lightcouch.ChangesResult.Row;
import org.lightcouch.CouchDbContext;
import org.lightcouch.CouchDbInfo;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CouchDbChangesetTrackerTest {

    @Mock
    private Changes changes;
    @Mock
    private CouchDbClientWrapper client;
    @Mock
    private CouchDbEndpoint endpoint;
    @Mock
    private CouchDbConsumer consumer;
    @Mock
    private CouchDbContext context;
    @Mock
    private CouchDbInfo info;
    @Mock
    private Row row3;
    @Mock
    private Row row2;
    @Mock
    private Row row1;
    @Mock
    private Exchange exchange1;
    @Mock
    private Exchange exchange2;
    @Mock
    private Exchange exchange3;
    @Mock
    private Processor processor;

    private CouchDbChangesetTracker tracker;

    @Before
    public void before() {
        when(endpoint.isUpdates()).thenReturn(true);

        when(client.context()).thenReturn(context);
        when(context.info()).thenReturn(info);
        when(info.getUpdateSeq()).thenReturn("100");

        when(client.changes()).thenReturn(changes);
        when(changes.continuousChanges()).thenReturn(changes);
        when(changes.includeDocs(true)).thenReturn(changes);
        when(changes.since(anyString())).thenReturn(changes);
        when(changes.heartBeat(anyLong())).thenReturn(changes);
        when(changes.style(ArgumentMatchers.isNull())).thenReturn(changes);

        when(row1.getSeq()).thenReturn("seq1");
        when(row2.getSeq()).thenReturn("seq2");
        when(row3.getSeq()).thenReturn("seq3");

        when(row1.getId()).thenReturn("id1");
        when(row2.getId()).thenReturn("id2");
        when(row3.getId()).thenReturn("id3");

        tracker = new CouchDbChangesetTracker(endpoint, consumer, client);
    }

    @Test
    public void testExchangeCreatedWithCorrectProperties() throws Exception {
        when(changes.hasNext()).thenReturn(true, true, true, false);
        when(changes.next()).thenReturn(row1, row2, row3);
        when(endpoint.createExchange("seq1", "id1", null, false)).thenReturn(exchange1);
        when(endpoint.createExchange("seq2", "id2", null, false)).thenReturn(exchange2);
        when(endpoint.createExchange("seq3", "id3", null, false)).thenReturn(exchange3);
        when(consumer.getProcessor()).thenReturn(processor);

        tracker.run();

        verify(endpoint).createExchange("seq1", "id1", null, false);
        verify(processor).process(exchange1);
        verify(endpoint).createExchange("seq2", "id2", null, false);
        verify(processor).process(exchange2);
        verify(endpoint).createExchange("seq3", "id3", null, false);
        verify(processor).process(exchange3);
    }

    @Test
    public void testProcessorInvoked() throws Exception {
        when(changes.hasNext()).thenReturn(true, false);
        when(changes.next()).thenReturn(row1);
        when(consumer.getProcessor()).thenReturn(processor);

        tracker.run();

        verify(endpoint).createExchange("seq1", "id1", null, false);
        verify(processor).process(ArgumentMatchers.isNull());
    }
}
