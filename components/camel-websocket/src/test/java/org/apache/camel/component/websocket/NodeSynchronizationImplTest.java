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
package org.apache.camel.component.websocket;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

@RunWith(MockitoJUnitRunner.class)
public class NodeSynchronizationImplTest {

    private static final String KEY_1 = "one";
    private static final String KEY_2 = "two";
    private static final String KEY_3 = "three";

    @Mock
    private WebsocketConsumer consumer;

    private DefaultWebsocket websocket1;
    private DefaultWebsocket websocket2;

    private NodeSynchronization sync;

    private MemoryWebsocketStore store1;

    @Before
    public void setUp() throws Exception {

        store1 = new MemoryWebsocketStore();

        websocket1 = new DefaultWebsocket(sync, null, consumer);
        websocket1.setConnectionKey(KEY_1);

        websocket2 = new DefaultWebsocket(sync, null, consumer);
        websocket2.setConnectionKey(KEY_2);
    }

    /**
     * Test method for {@link org.apache.camel.component.websocket.NodeSynchronization#addSocket(org.apache.camel.component.websocket.DefaultWebsocket)} .
     */
    @Test
    public void testAddSocketMemoryAndGlobal() {
        sync = new DefaultNodeSynchronization(store1);

        sync.addSocket(websocket1);
        assertEquals(websocket1, store1.get(KEY_1));

        sync.addSocket(websocket2);
        assertEquals(websocket2, store1.get(KEY_2));
    }

    /**
     * Test method for {@link org.apache.camel.component.websocket.NodeSynchronization#addSocket(org.apache.camel.component.websocket.DefaultWebsocket)} .
     */
    @Test
    public void testAddSocketMemoryOnly() {
        sync = new DefaultNodeSynchronization(store1);

        sync.addSocket(websocket1);
        assertEquals(websocket1, store1.get(KEY_1));
    }

    /**
     * Test method for {@link org.apache.camel.component.websocket.NodeSynchronization#addSocket(org.apache.camel.component.websocket.DefaultWebsocket)} .
     */
    @Test(expected = NullPointerException.class)
    public void testAddNullValue() {
        sync.addSocket(null);
    }

    /**
     * Test method for {@link org.apache.camel.component.websocket.NodeSynchronization#removeSocket(org.apache.camel.component.websocket.DefaultWebsocket)} .
     */
    @Test
    public void testRemoveDefaultWebsocket() {
        sync = new DefaultNodeSynchronization(store1);

        // first call of websocket1.getConnectionKey()
        sync.addSocket(websocket1);
        assertEquals(websocket1, store1.get(KEY_1));

        sync.addSocket(websocket2);
        assertEquals(websocket2, store1.get(KEY_2));

        // second call of websocket1.getConnectionKey()
        sync.removeSocket(websocket1);
        assertNull(store1.get(KEY_1));

        assertNotNull(store1.get(KEY_2));

        sync.removeSocket(websocket2);
        assertNull(store1.get(KEY_2));
    }

    /**
     * Test method for {@link org.apache.camel.component.websocket.NodeSynchronization#removeSocket(org.apache.camel.component.websocket.DefaultWebsocket)} .
     */
    @Test
    public void testRemoveDefaultWebsocketKeyNotSet() {
        sync = new DefaultNodeSynchronization(store1);

        // first call of websocket1.getConnectionKey()
        sync.addSocket(websocket1);
        assertEquals(websocket1, store1.get(KEY_1));

        // setConnectionKey(null) after sync.addSocket()- otherwise npe
        websocket1.setConnectionKey(null);

        try {
            // second call of websocket1.getConnectionKey()
            sync.removeSocket(websocket1);
            fail("Exception expected");
        } catch (Exception e) {
            assertEquals(NullPointerException.class, e.getClass());
        }
    }

    /**
     * Test method for {@link org.apache.camel.component.websocket.NodeSynchronization#removeSocket(org.apache.camel.component.websocket.DefaultWebsocket)} .
     */
    @Test
    public void testRemoveNotExisting() {
        sync = new DefaultNodeSynchronization(store1);

        // first call of websocket1.getConnectionKey()
        sync.addSocket(websocket1);
        assertEquals(websocket1, store1.get(KEY_1));

        assertNull(store1.get(KEY_2));
        sync.removeSocket(websocket2);

        assertEquals(websocket1, store1.get(KEY_1));
        assertNull(store1.get(KEY_2));
    }

    /**
     * Test method for {@link org.apache.camel.component.websocket.NodeSynchronization#removeSocket(String)} .
     */
    @Test
    public void testRemoveString() {
        sync = new DefaultNodeSynchronization(store1);

        // first call of websocket1.getConnectionKey()
        sync.addSocket(websocket1);
        assertEquals(websocket1, store1.get(KEY_1));

        sync.addSocket(websocket2);
        assertEquals(websocket2, store1.get(KEY_2));

        // second call of websocket1.getConnectionKey()
        sync.removeSocket(KEY_1);
        assertNull(store1.get(KEY_1));

        assertNotNull(store1.get(KEY_2));

        sync.removeSocket(KEY_2);
        assertNull(store1.get(KEY_2));
    }

    /**
     * Test method for {@link org.apache.camel.component.websocket.NodeSynchronization#removeSocket(String)} .
     */
    @Test
    public void testRemoveStringNotExisting() {

        sync = new DefaultNodeSynchronization(store1);

        // first call of websocket1.getConnectionKey()
        sync.addSocket(websocket1);
        assertEquals(websocket1, store1.get(KEY_1));

        assertNull(store1.get(KEY_3));
        sync.removeSocket(KEY_3);

        assertEquals(websocket1, store1.get(KEY_1));
        assertNull(store1.get(KEY_3));

    }

}
