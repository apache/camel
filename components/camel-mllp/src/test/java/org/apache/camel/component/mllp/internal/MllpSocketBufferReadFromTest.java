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
package org.apache.camel.component.mllp.internal;

import java.net.SocketTimeoutException;

import org.apache.camel.test.stub.tcp.SocketInputStreamStub;
import org.apache.camel.test.stub.tcp.SocketStub;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * Tests for the  class.
 */
public class MllpSocketBufferReadFromTest extends SocketBufferTestSupport {
    SocketStub socketStub;
    SocketInputStreamStub inputStreamStub;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        socketStub = new SocketStub();
        inputStreamStub = socketStub.inputStreamStub;
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testReadFromWithTimeoutExceptionOnInitialRead() throws Exception {
        inputStreamStub
            .addPacket(new SocketTimeoutException("Fake Timeout Exception"));

        try {
            endpoint.setReceiveTimeout(500);
            endpoint.setReadTimeout(100);
            instance.readFrom(socketStub);
            fail("Should have thrown and exception");
        } catch (SocketTimeoutException expectedEx) {
            assertNull(instance.toByteArray());
        }
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testReadFromWithTimeoutException() throws Exception {
        inputStreamStub
            .addPacket("FOO".getBytes())
            .addPacket("BAR".getBytes())
            .addPacket(new SocketTimeoutException("Fake Timeout Exception"));

        try {
            endpoint.setReceiveTimeout(500);
            endpoint.setReadTimeout(100);
            instance.readFrom(socketStub);
            fail("Should have thrown and exception");
        } catch (SocketTimeoutException expectedEx) {
            assertNull(instance.toByteArray());
        }
    }
}
