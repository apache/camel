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
package org.apache.camel.component.mllp.impl;

import org.apache.camel.component.mllp.MllpAcknowledgementDeliveryException;
import org.apache.camel.test.util.PayloadBuilder;
import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.component.mllp.MllpEndpoint.END_OF_BLOCK;
import static org.apache.camel.component.mllp.MllpEndpoint.END_OF_DATA;
import static org.apache.camel.component.mllp.MllpEndpoint.START_OF_BLOCK;
import static org.apache.camel.component.mllp.impl.MllpSocketWriter.PAYLOAD_TERMINATOR;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

public class MllpSocketAcknowledgementWriterTest extends MllpSocketWriterTestSupport {
    MllpSocketWriter mllpSocketWriter;

    @Before
    public void setUp() throws Exception {
        mllpSocketWriter = new MllpSocketWriter(fakeSocket, true);
    }

    @Test
    public void testWriteAcknowledgement() throws Exception {
        byte[] expected = PayloadBuilder.build(START_OF_BLOCK, TEST_ACKNOWLEDGEMENT, END_OF_BLOCK, END_OF_DATA);

        mllpSocketWriter.writeEnvelopedPayload(TEST_MESSAGE.getBytes(), TEST_ACKNOWLEDGEMENT.getBytes());

        assertArrayEquals(expected, fakeSocket.payload());
    }

    @Test
    public void testWriteNullAcknowledgement() throws Exception {
        byte[] acknowledgement = null;

        byte[] expected = PayloadBuilder.build(START_OF_BLOCK, END_OF_BLOCK, END_OF_DATA);

        mllpSocketWriter.writeEnvelopedPayload(TEST_MESSAGE.getBytes(), acknowledgement);

        assertArrayEquals(expected, fakeSocket.payload());
    }

    @Test
    public void testWriteEmptyAcknowledgement() throws Exception {
        byte[] acknowledgement = new byte[0];

        byte[] expected = PayloadBuilder.build(START_OF_BLOCK, END_OF_BLOCK, END_OF_DATA);

        mllpSocketWriter.writeEnvelopedPayload(TEST_MESSAGE.getBytes(), acknowledgement);

        assertArrayEquals(expected, fakeSocket.payload());
    }

    @Test(expected = MllpAcknowledgementDeliveryException.class)
    public void testGetOutputStreamFailure() throws Exception {
        fakeSocket.fakeSocketOutputStream = null;

        try {
            mllpSocketWriter.writeEnvelopedPayload(TEST_MESSAGE.getBytes(), TEST_ACKNOWLEDGEMENT.getBytes());
        } catch (MllpAcknowledgementDeliveryException expectedEx) {
            verifyException(expectedEx);
            throw expectedEx;
        }
    }

    @Test(expected = MllpAcknowledgementDeliveryException.class)
    public void testWriteToUnconnectedSocket() throws Exception {
        fakeSocket.connected = false;

        try {
            mllpSocketWriter.writeEnvelopedPayload(TEST_MESSAGE.getBytes(), TEST_ACKNOWLEDGEMENT.getBytes());
        } catch (MllpAcknowledgementDeliveryException expectedEx) {
            verifyException(expectedEx);
            throw expectedEx;
        }
    }

    @Test(expected = MllpAcknowledgementDeliveryException.class)
    public void testWriteToClosedSocket() throws Exception {
        fakeSocket.closed = true;

        try {
            mllpSocketWriter.writeEnvelopedPayload(TEST_MESSAGE.getBytes(), TEST_ACKNOWLEDGEMENT.getBytes());
        } catch (MllpAcknowledgementDeliveryException expectedEx) {
            verifyException(expectedEx);
            throw expectedEx;
        }
    }

    @Test(expected = MllpAcknowledgementDeliveryException.class)
    public void testWriteStartOfBlockFailure() throws Exception {
        fakeSocket.fakeSocketOutputStream.writeFailOn = new Byte((byte) START_OF_BLOCK);

        try {
            mllpSocketWriter.writeEnvelopedPayload(TEST_MESSAGE.getBytes(), TEST_ACKNOWLEDGEMENT.getBytes());
        } catch (MllpAcknowledgementDeliveryException expectedEx) {
            verifyException(expectedEx);
            throw expectedEx;
        }
    }

    @Test(expected = MllpAcknowledgementDeliveryException.class)
    public void testWriteAcknowledgementFailure() throws Exception {
        fakeSocket.fakeSocketOutputStream.writeArrayFailOn = TEST_ACKNOWLEDGEMENT.getBytes();

        try {
            mllpSocketWriter.writeEnvelopedPayload(TEST_MESSAGE.getBytes(), TEST_ACKNOWLEDGEMENT.getBytes());
        } catch (MllpAcknowledgementDeliveryException expectedEx) {
            verifyException(expectedEx);
            throw expectedEx;
        }
    }

    @Test(expected = MllpAcknowledgementDeliveryException.class)
    public void testWriteEndOfMessageFailure() throws Exception {
        fakeSocket.fakeSocketOutputStream.writeArrayFailOn = PAYLOAD_TERMINATOR;

        try {
            mllpSocketWriter.writeEnvelopedPayload(TEST_MESSAGE.getBytes(), TEST_ACKNOWLEDGEMENT.getBytes());
        } catch (MllpAcknowledgementDeliveryException expectedEx) {
            verifyException(expectedEx);
            throw expectedEx;
        }
    }

    private void verifyException(MllpAcknowledgementDeliveryException expectedEx) throws Exception {
        assertNotNull(expectedEx.getMessage());
        assertArrayEquals(TEST_MESSAGE.getBytes(), expectedEx.getHl7Message());
        assertArrayEquals(TEST_ACKNOWLEDGEMENT.getBytes(), expectedEx.getHl7Acknowledgement());
        assertArrayEquals(TEST_ACKNOWLEDGEMENT.getBytes(), expectedEx.getMllpPayload());
    }


}