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

package org.apache.camel.component.mllp.internal;

import java.net.SocketTimeoutException;

import org.apache.camel.component.mllp.MllpProtocolConstants;

import org.apache.camel.test.stub.tcp.SocketStub;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests for the overridden methods in the MllpSocketBuffer class.
 */
public class MllpSocketBufferWriteTest extends SocketBufferTestSupport {
    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testWriteIntWithStartOfBlock() throws Exception {
        instance.write(MllpProtocolConstants.START_OF_BLOCK);

        assertEquals(1, instance.size());
        assertEquals(0, instance.startOfBlockIndex);
        assertEquals(-1, instance.endOfBlockIndex);
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testWriteIntWithEndOfBlock() throws Exception {
        instance.write(MllpProtocolConstants.END_OF_BLOCK);

        assertEquals(1, instance.size());
        assertEquals(-1, instance.startOfBlockIndex);
        assertEquals(0, instance.endOfBlockIndex);
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testWriteIntWithEndOfData() throws Exception {
        instance.write(MllpProtocolConstants.END_OF_DATA);

        assertEquals(1, instance.size());
        assertEquals(-1, instance.startOfBlockIndex);
        assertEquals(-1, instance.endOfBlockIndex);
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testWriteBytesWithNullArray() throws Exception {
        instance.write((byte[]) null);

        assertEquals(0, instance.size());
        assertEquals(-1, instance.startOfBlockIndex);
        assertEquals(-1, instance.endOfBlockIndex);
    }


    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testWriteBytesWithEmptyArray() throws Exception {
        instance.write(new byte[0]);

        assertEquals(0, instance.size());
        assertEquals(-1, instance.startOfBlockIndex);
        assertEquals(-1, instance.endOfBlockIndex);
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testWriteBytesWithFullEnvelope() throws Exception {
        instance.write(buildTestBytes("BLAH", true, true, true));

        assertEquals(7, instance.size());
        assertEquals(0, instance.startOfBlockIndex);
        assertEquals(5, instance.endOfBlockIndex);
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testWriteBytesWithoutEnvelope() throws Exception {
        instance.write("BLAH".getBytes());

        assertEquals(4, instance.size());
        assertEquals(-1, instance.startOfBlockIndex);
        assertEquals(-1, instance.endOfBlockIndex);
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testWriteBytesWithWithoutStartOfBlock() throws Exception {
        instance.write(buildTestBytes("BLAH", false, true, true));

        assertEquals(6, instance.size());
        assertEquals(-1, instance.startOfBlockIndex);
        assertEquals(4, instance.endOfBlockIndex);
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testWriteBytesWithWithoutEndOfBlock() throws Exception {
        instance.write(buildTestBytes("BLAH", true, false, true));

        assertEquals(6, instance.size());
        assertEquals(0, instance.startOfBlockIndex);
        assertEquals(-1, instance.endOfBlockIndex);
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testWriteBytesWithWithoutEndOfData() throws Exception {
        instance.write(buildTestBytes("BLAH", true, true, false));

        assertEquals(6, instance.size());
        assertEquals(0, instance.startOfBlockIndex);
        assertEquals(5, instance.endOfBlockIndex);
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testWriteBytesWithWithoutEndOfBlockOrEndOfData() throws Exception {
        instance.write(buildTestBytes("BLAH", true, false, false));

        assertEquals(5, instance.size());
        assertEquals(0, instance.startOfBlockIndex);
        assertEquals(-1, instance.endOfBlockIndex);
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testWriteByteArraySliceWithNullArray() throws Exception {
        instance.write(null, 0, 5);

        assertEquals(0, instance.size());
        assertEquals(-1, instance.startOfBlockIndex);
        assertEquals(-1, instance.endOfBlockIndex);
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testWriteByteArraySliceWithEmptyArray() throws Exception {
        instance.write(new byte[0], 0, 5);

        assertEquals(0, instance.size());
        assertEquals(-1, instance.startOfBlockIndex);
        assertEquals(-1, instance.endOfBlockIndex);
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testWriteByteArraySliceWithNegativeOffset() throws Exception {
        byte[] payload = "BLAH".getBytes();

        try {
            instance.write(payload, -5, payload.length);
            fail("Exception should have been thrown");
        } catch (IndexOutOfBoundsException expectedEx) {
            assertEquals("offset <-5> is less than zero", expectedEx.getMessage());
        }
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testWriteByteArraySliceWithOffsetGreaterThanLength() throws Exception {
        byte[] payload = "BLAH".getBytes();

        try {
            instance.write(payload, payload.length + 1, payload.length);
            fail("Exception should have been thrown");
        } catch (IndexOutOfBoundsException expectedEx) {
            assertEquals("offset <5> is greater than write count <4>", expectedEx.getMessage());
        }
    }


    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testWriteByteArraySliceWithNegativeLength() throws Exception {
        try {
            instance.write("BLAH".getBytes(), 0, -5);
            fail("Exception should have been thrown");
        } catch (IndexOutOfBoundsException expectedEx) {
            assertEquals("write count <-5> is less than zero", expectedEx.getMessage());
        }
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testWriteByteArraySliceWithLengthGreaterThanAvailable() throws Exception {
        byte[] payload = "BLAH".getBytes();

        try {
            instance.write(payload, 0, payload.length + 1);
            fail("Exception should have been thrown");
        } catch (IndexOutOfBoundsException expectedEx) {
            assertEquals("write count <5> is greater than length of the source byte[] <4>", expectedEx.getMessage());
        }

        try {
            instance.write("BLAH".getBytes(), 1, payload.length);
            fail("Exception should have been thrown");
        } catch (IndexOutOfBoundsException expectedEx) {
            assertEquals("offset <1> plus write count <4> is <5> is greater than length <4> of the source byte[]", expectedEx.getMessage());
        }

        try {
            instance.write("BLAH".getBytes(), 2, payload.length - 1);
            fail("Exception should have been thrown");
        } catch (IndexOutOfBoundsException expectedEx) {
            assertEquals("offset <2> plus write count <3> is <5> is greater than length <4> of the source byte[]", expectedEx.getMessage());
        }
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testEnsureCapacityWithNegativeRequiredAvailability() throws Exception {
        assertEquals(instance.MIN_BUFFER_SIZE, instance.capacity());

        instance.ensureCapacity(-1);

        assertEquals(instance.MIN_BUFFER_SIZE, instance.capacity());
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testEnsureCapacityWithOutOfRangeRequiredAvailability() throws Exception {
        assertEquals(instance.MIN_BUFFER_SIZE, instance.capacity());

        try {
            instance.ensureCapacity(Integer.MAX_VALUE);
            fail("Should have thrown an exception");
        } catch (IllegalStateException expectedEx) {
            String expectedMessage = "Cannot increase the buffer size <2048> in order to increase the available capacity from <2048> to <2147483647>"
                + " because the required buffer size <2147483647> exceeds the maximum buffer size <1073741824>";
            assertEquals(expectedMessage, expectedEx.getMessage());
        }

        try {
            instance.ensureCapacity(instance.MAX_BUFFER_SIZE + 1);
            fail("Should have thrown an exception");
        } catch (IllegalStateException expectedEx) {
            String expectedMessage = "Cannot increase the buffer size <2048> in order to increase the available capacity from <2048> to <1073741825>"
                + " because the required buffer size <1073741825> exceeds the maximum buffer size <1073741824>";
            assertEquals(expectedMessage, expectedEx.getMessage());
        }

        try {
            instance.write("BLAH".getBytes());
            instance.ensureCapacity(instance.MAX_BUFFER_SIZE);
            fail("Should have thrown an exception");
        } catch (IllegalStateException expectedEx) {
            String expectedMessage = "Cannot increase the buffer size <2048> in order to increase the available capacity from <2044> to <1073741824>"
                + " because the required buffer size <1073741828> exceeds the maximum buffer size <1073741824>";
            assertEquals(expectedMessage, expectedEx.getMessage());
        }
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testEnsureCapacityWithAlreadyAllocateMaxBufferSize() throws Exception {
        assertEquals(instance.MIN_BUFFER_SIZE, instance.capacity());

        instance.ensureCapacity(instance.MAX_BUFFER_SIZE);

        try {
            instance.ensureCapacity(instance.MAX_BUFFER_SIZE + 1);
            fail("Should have thrown an exception");
        } catch (IllegalStateException expectedEx) {
            String expectedMessage = "Cannot increase the buffer size from <1073741824> to <1073741825> in order to increase the available capacity"
                + " from <1073741824> to <1073741825> because the buffer is already the maximum size <1073741824>";
            assertEquals(expectedMessage, expectedEx.getMessage());
        }


    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test(expected = SocketTimeoutException.class)
    public void testReadFrom() throws Exception {
        SocketStub socketStub = new SocketStub();
        socketStub.inputStreamStub
            .addPacket("FOO".getBytes())
            .addPacket("BAR".getBytes());

        endpoint.setReceiveTimeout(500);
        endpoint.setReadTimeout(100);

        instance.readFrom(socketStub);

        assertArrayEquals("FOOBAR".getBytes(), instance.toByteArray());
    }

}