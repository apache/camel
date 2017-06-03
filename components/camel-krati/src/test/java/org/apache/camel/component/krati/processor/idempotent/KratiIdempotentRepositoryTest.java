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

package org.apache.camel.component.krati.processor.idempotent;

import krati.core.segment.ChannelSegmentFactory;
import krati.io.Serializer;
import krati.store.DataSet;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.krati.KratiHelper;
import org.apache.camel.component.krati.serializer.KratiDefaultSerializer;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class KratiIdempotentRepositoryTest extends CamelTestSupport {

    private String path = "target/test/idempotent";
    private DataSet<byte[]> dataSet = KratiHelper.createDataSet(path, 2, new ChannelSegmentFactory());
    private Serializer<String> serializer = new KratiDefaultSerializer<String>();
    private KratiIdempotentRepository repository;

    private String key01 = "123";
    private String key02 = "456";

    public void setUp() throws Exception {
        repository = new KratiIdempotentRepository("target/test/idempotent");
        repository.setDataSet(dataSet);
        dataSet.clear();
        super.setUp();
    }

    public void tearDown() throws Exception {
        super.tearDown();
        dataSet.clear();
    }

    @Test
    public void testAdd() throws Exception {
        // add first key
        assertTrue(dataSet.add(serializer.serialize(key01)));
        assertTrue(repository.contains(key01));

        // try to add an other one
        assertTrue(dataSet.add(serializer.serialize(key02)));
        assertTrue(repository.contains(key02));
    }


    @Test
    public void testContains() throws Exception {
        assertFalse(repository.contains(key01));

        // add key and check again
        assertTrue(repository.add(key01));
        assertTrue(repository.contains(key01));

    }

    @Test
    public void testRemove() throws Exception {
        // add key to remove
        assertTrue(repository.add(key01));
        // assertEquals(1, dataSet.size());

        // remove key
        assertTrue(repository.remove(key01));
        //assertEquals(0, dataSet.size());

        // try to remove a key that isn't there
        assertFalse(repository.remove(key02));
    }

    @Test
    public void testClear() throws Exception {
        // add keys to clear
        assertTrue(repository.add(key01));
        assertTrue(repository.add(key02));

        repository.clear();
        
        assertFalse(repository.contains(key01));
        assertFalse(repository.contains(key02));
    }

    @Test
    public void testRepositoryInRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:out");
        mock.expectedBodiesReceived("a", "b");
        // c is a duplicate

        // should be started
        assertEquals("Should be started", true, repository.getStatus().isStarted());

        // send 3 message with one duplicated key (key01)
        template.sendBodyAndHeader("direct://in", "a", "messageId", key01);
        template.sendBodyAndHeader("direct://in", "b", "messageId", key02);
        template.sendBodyAndHeader("direct://in", "c", "messageId", key01);

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct://in")
                        .idempotentConsumer(header("messageId"), repository)
                        .to("mock://out");
            }
        };
    }


}
