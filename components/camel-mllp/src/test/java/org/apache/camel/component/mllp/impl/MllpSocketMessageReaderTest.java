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

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.apache.camel.component.mllp.MllpException;
import org.apache.camel.component.mllp.MllpReceiveException;
import org.apache.camel.component.mllp.MllpTimeoutException;

import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.component.mllp.MllpEndpoint.SEGMENT_DELIMITER;
import static org.apache.camel.component.mllp.MllpEndpoint.START_OF_BLOCK;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

public class MllpSocketMessageReaderTest extends MllpSocketReaderTestSupport {
    MllpSocketReader mllpSocketReader;

    @Before
    public void setUp() throws Exception {
        assertSocketOpen();
        mllpSocketReader = new MllpSocketReader(fakeSocket, 5000, 1000, false);
    }

    @Test
    public void testReadMessage() throws Exception {
        byte[] expected = TEST_MESSAGE.getBytes();

        fakeSocket.fakeSocketInputStream.addPacket(START_PACKET, expected, END_PACKET);

        byte[] actual = mllpSocketReader.readEnvelopedPayload();

        assertArrayEquals(expected, actual);
        assertSocketOpen();
    }

    @Test
    public void testReadMessageWithSeparateEnvelopingAndMessagePackets() throws Exception {
        byte[] expected = TEST_MESSAGE.getBytes();

        fakeSocket.fakeSocketInputStream.addPackets(START_PACKET, TEST_MESSAGE.getBytes(), END_PACKET);

        byte[] actual = mllpSocketReader.readEnvelopedPayload();

        assertArrayEquals(expected, actual);
        assertSocketOpen();
    }

    @Test
    public void testReadMessageWithMultipleMessagePackets() throws Exception {
        byte[] expected = TEST_MESSAGE.getBytes();

        fakeSocket.fakeSocketInputStream
                .addPacket(START_PACKET)
                .addPackets(TEST_MESSAGE, SEGMENT_DELIMITER)
                .addPacket(END_PACKET);

        byte[] actual = mllpSocketReader.readEnvelopedPayload();

        assertArrayEquals(expected, actual);
        assertSocketOpen();
    }

    @Test
    public void testReadEmptyMessage() throws Exception {
        byte[] expected = new byte[0];
        fakeSocket.fakeSocketInputStream.addPacket(START_PACKET, END_PACKET);

        byte[] actual = mllpSocketReader.readEnvelopedPayload();

        assertArrayEquals(expected, actual);
        assertSocketOpen();
    }

    @Test
    public void testReadEmptyMessageWithSeparateEnvelopingPackets() throws Exception {
        byte[] expected = new byte[0];
        fakeSocket.fakeSocketInputStream.addPackets(START_PACKET, END_PACKET);

        byte[] actual = mllpSocketReader.readEnvelopedPayload();

        assertArrayEquals(expected, actual);
        assertSocketOpen();
    }

    @Test()
    public void testGetInputStreamFailure() throws Exception {
        fakeSocket.fakeSocketInputStream = null;

        try {
            mllpSocketReader.readEnvelopedPayload();

            expectedExceptionFailure(MllpReceiveException.class);
        } catch (MllpReceiveException expectedEx) {
            assertEmptyExpectedException(expectedEx);
            assertNotNull(expectedEx.getCause());
            assertThat(expectedEx.getCause(), instanceOf(IOException.class));
            assertSocketReset();
        }
    }

    @Test
    public void testEndOfStreamOnInitialRead() throws Exception {
        fakeSocket.fakeSocketInputStream.addPackets(EMPTY_PACKET, TEST_MESSAGE);

        try {
            mllpSocketReader.readEnvelopedPayload();

            expectedExceptionFailure(MllpReceiveException.class);
        } catch (MllpReceiveException expectedEx) {
            assertEmptyExpectedException(expectedEx);
            assertNull(expectedEx.getCause());
            assertSocketReset();
        }
    }

    @Test
    public void testTimeoutOnInitialRead() throws Exception {
        byte[] actual = mllpSocketReader.readEnvelopedPayload();

        assertArrayEquals(null, actual);
        assertSocketOpen();
    }

    @Test
    public void testTimeoutOnInitialReadWithStartOfBlock() throws Exception {
        fakeSocket.fakeSocketInputStream.addPacket(START_OF_BLOCK);

        try {
            mllpSocketReader.readEnvelopedPayload();

            expectedExceptionFailure(MllpTimeoutException.class);
        } catch (MllpTimeoutException expectedEx) {
            assertEmptyExpectedException(expectedEx);
            assertNotNull(expectedEx.getCause());
            assertThat(expectedEx.getCause().getClass(), sameInstance(SocketTimeoutException.class));
            assertSocketReset();
        }
    }

    @Test
    public void testSocketExceptionOnInitialRead() throws Exception {
        fakeSocket.fakeSocketInputStream.addPacket(EXCEPTION_PACKET);

        try {
            mllpSocketReader.readEnvelopedPayload();

            expectedExceptionFailure(MllpReceiveException.class);
        } catch (MllpReceiveException expectedEx) {
            assertEmptyExpectedException(expectedEx);
            assertNotNull(expectedEx.getCause());
            assertThat(expectedEx.getCause().getClass(), sameInstance(SocketException.class));
            assertSocketReset();
        }
    }

    @Test
    public void testIOExceptionOnInitialRead() throws Exception {
        fakeSocket.fakeSocketInputStream.useSocketExceptionOnNullPacket = false;
        fakeSocket.fakeSocketInputStream.addPacket(EXCEPTION_PACKET);

        try {
            mllpSocketReader.readEnvelopedPayload();

            expectedExceptionFailure(MllpReceiveException.class);
        } catch (MllpReceiveException expectedEx) {
            assertEmptyExpectedException(expectedEx);
            assertNotNull(expectedEx.getCause());
            assertThat(expectedEx.getCause().getClass(), sameInstance(IOException.class));
            assertSocketReset();
        }
    }

    @Test
    public void testEndOfStreamOnFirstAdditionalRead() throws Exception {
        fakeSocket.fakeSocketInputStream.addPackets(START_PACKET, EMPTY_PACKET);

        try {
            mllpSocketReader.readEnvelopedPayload();

            expectedExceptionFailure(MllpReceiveException.class);
        } catch (MllpReceiveException expectedEx) {
            assertEmptyExpectedException(expectedEx);
            assertNull(expectedEx.getCause());
            assertSocketReset();
        }
    }

    @Test
    public void testEndOfStreamOnFirstAdditionalReadWithPartialPayload() throws Exception {
        fakeSocket.fakeSocketInputStream.addPacket(START_PACKET, TEST_MESSAGE.getBytes()).addPacket(EMPTY_PACKET);

        try {
            mllpSocketReader.readEnvelopedPayload();

            expectedExceptionFailure(MllpReceiveException.class);
        } catch (MllpReceiveException expectedEx) {
            assertExpectedException(expectedEx);
            assertNull(expectedEx.getCause());
            assertSocketReset();
        }
    }

    @Test
    public void testTimeoutOnFirstAdditionalRead() throws Exception {
        fakeSocket.fakeSocketInputStream.addPackets(START_PACKET);

        try {
            mllpSocketReader.readEnvelopedPayload();

            expectedExceptionFailure(MllpTimeoutException.class);
        } catch (MllpTimeoutException expectedEx) {
            assertEmptyExpectedException(expectedEx);
            assertNotNull(expectedEx.getCause());
            assertThat(expectedEx.getCause().getClass(), sameInstance(SocketTimeoutException.class));
            assertSocketReset();
        }
    }

    @Test
    public void testTimeoutOnFirstAdditionalReadWithPartialPayload() throws Exception {
        fakeSocket.fakeSocketInputStream.addPacket(START_PACKET, TEST_MESSAGE.getBytes());

        try {
            mllpSocketReader.readEnvelopedPayload();

            expectedExceptionFailure(MllpTimeoutException.class);
        } catch (MllpTimeoutException expectedEx) {
            assertExpectedException(expectedEx);
            assertNotNull(expectedEx.getCause());
            assertThat(expectedEx.getCause().getClass(), sameInstance(SocketTimeoutException.class));
            assertSocketReset();
        }
    }

    @Test
    public void testSocketExceptionOnFirstAdditionalRead() throws Exception {
        fakeSocket.fakeSocketInputStream.addPackets(START_PACKET, EXCEPTION_PACKET);

        try {
            mllpSocketReader.readEnvelopedPayload();

            expectedExceptionFailure(MllpReceiveException.class);
        } catch (MllpReceiveException expectedEx) {
            assertEmptyExpectedException(expectedEx);
            assertNotNull(expectedEx.getCause());
            assertThat(expectedEx.getCause().getClass(), sameInstance(SocketException.class));
            assertSocketReset();
        }
    }

    @Test
    public void testSocketExceptionOnFirstAdditionalReadWithPartialPayload() throws Exception {
        fakeSocket.fakeSocketInputStream.addPacket(START_PACKET, TEST_MESSAGE.getBytes()).addPacket(EXCEPTION_PACKET);

        try {
            mllpSocketReader.readEnvelopedPayload();

            expectedExceptionFailure(MllpReceiveException.class);
        } catch (MllpReceiveException expectedEx) {
            assertExpectedException(expectedEx);
            assertNotNull(expectedEx.getCause());
            assertThat(expectedEx.getCause().getClass(), sameInstance(SocketException.class));
            assertSocketReset();
        }
    }

    @Test
    public void testIOExceptionOnFirstAdditionalRead() throws Exception {
        fakeSocket.fakeSocketInputStream.useSocketExceptionOnNullPacket = false;
        fakeSocket.fakeSocketInputStream.addPackets(START_PACKET, EXCEPTION_PACKET);

        try {
            mllpSocketReader.readEnvelopedPayload();

            expectedExceptionFailure(MllpReceiveException.class);
        } catch (MllpReceiveException expectedEx) {
            assertEmptyExpectedException(expectedEx);
            assertNotNull(expectedEx.getCause());
            assertThat(expectedEx.getCause().getClass(), sameInstance(IOException.class));
            assertSocketReset();
        }
    }

    @Test
    public void testIOExceptionOnFirstAdditionalReadWithPartialPayload() throws Exception {
        fakeSocket.fakeSocketInputStream.useSocketExceptionOnNullPacket = false;
        fakeSocket.fakeSocketInputStream.addPacket(START_PACKET, TEST_MESSAGE.getBytes()).addPacket(EXCEPTION_PACKET);

        try {
            mllpSocketReader.readEnvelopedPayload();

            expectedExceptionFailure(MllpReceiveException.class);
        } catch (MllpReceiveException expectedEx) {
            assertExpectedException(expectedEx);
            assertNotNull(expectedEx.getCause());
            assertThat(expectedEx.getCause().getClass(), sameInstance(IOException.class));
            assertSocketReset();
        }
    }

    @Test
    public void testEndOfStreamOnSecondAdditionalReadWithPartialPayload() throws Exception {
        fakeSocket.fakeSocketInputStream.addPackets(START_PACKET, TEST_MESSAGE.getBytes(), EMPTY_PACKET);

        try {
            mllpSocketReader.readEnvelopedPayload();

            expectedExceptionFailure(MllpReceiveException.class);
        } catch (MllpReceiveException expectedEx) {
            assertExpectedException(expectedEx);
            assertNull(expectedEx.getCause());
            assertSocketReset();
        }
    }

    @Test
    public void testTimeoutOnSecondAdditionalReadWithPartialPayload() throws Exception {
        fakeSocket.fakeSocketInputStream.addPackets(START_PACKET, TEST_MESSAGE.getBytes());

        try {
            mllpSocketReader.readEnvelopedPayload();

            expectedExceptionFailure(MllpTimeoutException.class);
        } catch (MllpTimeoutException expectedEx) {
            assertExpectedException(expectedEx);
            assertNotNull(expectedEx.getCause());
            assertThat(expectedEx.getCause().getClass(), sameInstance(SocketTimeoutException.class));
            assertSocketReset();
        }
    }

    @Test
    public void testSocketExceptionOnSecondAdditionalReadWithPartialPayload() throws Exception {
        fakeSocket.fakeSocketInputStream.addPackets(START_PACKET, TEST_MESSAGE.getBytes(), EXCEPTION_PACKET);

        try {
            mllpSocketReader.readEnvelopedPayload();

            expectedExceptionFailure(MllpReceiveException.class);
        } catch (MllpReceiveException expectedEx) {
            assertExpectedException(expectedEx);
            assertNotNull(expectedEx.getCause());
            assertThat(expectedEx.getCause().getClass(), sameInstance(SocketException.class));
            assertSocketReset();
        }
    }

    @Test
    public void testIOExceptionOnSecondAdditionalReadWithPartialPayload() throws Exception {
        fakeSocket.fakeSocketInputStream.useSocketExceptionOnNullPacket = false;
        fakeSocket.fakeSocketInputStream.addPackets(START_PACKET, TEST_MESSAGE.getBytes(), EXCEPTION_PACKET);

        try {
            mllpSocketReader.readEnvelopedPayload();

            expectedExceptionFailure(MllpReceiveException.class);
        } catch (MllpReceiveException expectedEx) {
            assertExpectedException(expectedEx);
            assertNotNull(expectedEx.getCause());
            assertThat(expectedEx.getCause().getClass(), sameInstance(IOException.class));
            assertSocketReset();
        }
    }

    @Test
    public void testLeadingOutOfBandBytes() throws Exception {
        byte[] expected = TEST_MESSAGE.getBytes();

        fakeSocket.fakeSocketInputStream.addPacket("Junk".getBytes(), START_PACKET, expected, END_PACKET);

        byte[] actual = mllpSocketReader.readEnvelopedPayload();

        assertArrayEquals(expected, actual);
    }

    @Test
    public void testLeadingOutOfBandBytesWithEmptyMessage() throws Exception {
        byte[] expected = new byte[0];
        fakeSocket.fakeSocketInputStream.addPacket("Junk".getBytes(), START_PACKET, END_PACKET);

        byte[] actual = mllpSocketReader.readEnvelopedPayload();

        assertArrayEquals(expected, actual);
        assertSocketOpen();
    }

    @Test
    public void testLeadingOutOfBandBytesWithEmptyMessageWithSeparateEnvelopingPackets() throws Exception {
        byte[] expected = new byte[0];
        fakeSocket.fakeSocketInputStream.addPackets("Junk".getBytes(), START_PACKET, END_PACKET);

        byte[] actual = mllpSocketReader.readEnvelopedPayload();

        assertArrayEquals(expected, actual);
        assertSocketOpen();
    }

    @Test
    public void testLeadingOutOfBandBytesSeparateEnvelopingAndMessagePackets() throws Exception {
        byte[] expected = TEST_MESSAGE.getBytes();

        fakeSocket.fakeSocketInputStream.addPackets("Junk".getBytes(), START_PACKET, TEST_MESSAGE.getBytes(), END_PACKET);

        byte[] actual = mllpSocketReader.readEnvelopedPayload();

        assertArrayEquals(expected, actual);
        assertSocketOpen();
    }

    @Test
    public void testLeadingOutOfBandBytesWithMultipleMessagePackets() throws Exception {
        byte[] expected = TEST_MESSAGE.getBytes();

        fakeSocket.fakeSocketInputStream
                .addPacket("Junk")
                .addPacket(START_PACKET)
                .addPackets(TEST_MESSAGE, SEGMENT_DELIMITER)
                .addPacket(END_PACKET);

        byte[] actual = mllpSocketReader.readEnvelopedPayload();

        assertArrayEquals(expected, actual);
        assertSocketOpen();
    }

    @Test
    public void testTrailingOutOfBandBytes() throws Exception {
        byte[] expected = TEST_MESSAGE.getBytes();

        fakeSocket.fakeSocketInputStream.addPacket(START_PACKET, expected, END_PACKET, "Junk".getBytes());

        byte[] actual = mllpSocketReader.readEnvelopedPayload();

        assertArrayEquals(expected, actual);
    }

    @Test
    public void testTrailingOutOfBandBytesWithEmptyMessage() throws Exception {
        byte[] expected = new byte[0];
        fakeSocket.fakeSocketInputStream.addPacket(START_PACKET, END_PACKET, "Junk".getBytes());

        byte[] actual = mllpSocketReader.readEnvelopedPayload();

        assertArrayEquals(expected, actual);
        assertSocketOpen();
    }

    @Test
    public void testTrailingOutOfBandBytesWithEmptyMessageWithSeparateEnvelopingPackets() throws Exception {
        byte[] expected = new byte[0];
        fakeSocket.fakeSocketInputStream.addPackets(START_PACKET, END_PACKET, "Junk".getBytes());

        byte[] actual = mllpSocketReader.readEnvelopedPayload();

        assertArrayEquals(expected, actual);
        assertSocketOpen();
    }

    @Test
    public void testTrailingOutOfBandBytesSeparateEnvelopingAndMessagePackets() throws Exception {
        byte[] expected = TEST_MESSAGE.getBytes();

        fakeSocket.fakeSocketInputStream.addPackets(START_PACKET, TEST_MESSAGE.getBytes(), END_PACKET, "Junk".getBytes());

        byte[] actual = mllpSocketReader.readEnvelopedPayload();

        assertArrayEquals(expected, actual);
        assertSocketOpen();
    }

    @Test
    public void testTrailingOutOfBandBytesWithMultipleMessagePackets() throws Exception {
        byte[] expected = TEST_MESSAGE.getBytes();

        fakeSocket.fakeSocketInputStream
                .addPacket(START_PACKET)
                .addPackets(TEST_MESSAGE, SEGMENT_DELIMITER)
                .addPacket(END_PACKET)
                .addPacket("Junk");

        byte[] actual = mllpSocketReader.readEnvelopedPayload();

        assertArrayEquals(expected, actual);
        assertSocketOpen();
    }

    private void assertEmptyExpectedException(MllpException expectedEx) {
        assertNotNull(expectedEx);
        assertNotNull(expectedEx.getMessage());
        assertNull(expectedEx.getHl7Message());
        assertNull(expectedEx.getHl7Acknowledgement());
        assertNull(expectedEx.getMllpPayload());
    }

    private void assertExpectedException(MllpException expectedEx) {
        assertNotNull(expectedEx);
        assertNotNull(expectedEx.getMessage());
        assertArrayEquals(TEST_MESSAGE.getBytes(), expectedEx.getHl7Message());
        assertNull(expectedEx.getHl7Acknowledgement());
        assertArrayEquals(TEST_MESSAGE.getBytes(), expectedEx.getMllpPayload());
    }
}