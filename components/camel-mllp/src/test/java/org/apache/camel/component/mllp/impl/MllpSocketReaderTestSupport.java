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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.StringTokenizer;

import org.apache.camel.test.util.PayloadBuilder;

import static org.apache.camel.component.mllp.MllpEndpoint.END_OF_BLOCK;
import static org.apache.camel.component.mllp.MllpEndpoint.END_OF_DATA;
import static org.apache.camel.component.mllp.MllpEndpoint.START_OF_BLOCK;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public abstract class MllpSocketReaderTestSupport {
    static final String TEST_MESSAGE =
        "MSH|^~\\&|ADT|EPIC|JCAPS|CC|20161206193919|RISTECH|ADT^A08|00001|D|2.3^^|||||||" + '\r'
            + "EVN|A08|20150107161440||REG_UPDATE_SEND_VISIT_MESSAGES_ON_PATIENT_CHANGES|RISTECH^RADIOLOGY^TECHNOLOGIST^^^^^^UCLA^^^^^RRMC||" + '\r'
            + "PID|1|2100355^^^MRN^MRN|2100355^^^MRN^MRN||MDCLS9^MC9||19700109|F||U|111 HOVER STREET^^LOS ANGELES^CA^90032^USA^P^^LOS ANGELE|LOS ANGELE|"
                + "(310)725-6952^P^PH^^^310^7256952||ENGLISH|U||60000013647|565-33-2222|||U||||||||N||" + '\r'
            + "PD1|||UCLA HEALTH SYSTEM^^10|10002116^ADAMS^JOHN^D^^^^^EPIC^^^^PROVID||||||||||||||" + '\r'
            + "NK1|1|DOE^MC9^^|OTH|^^^^^USA|(310)888-9999^^^^^310^8889999|(310)999-2222^^^^^310^9992222|Emergency Contact 1|||||||||||||||||||||||||||" + '\r'
            + "PV1|1|OUTPATIENT|RR CT^^^1000^^^^^^^DEPID|EL|||017511^TOBIAS^JONATHAN^^^^^^EPIC^^^^PROVID|017511^TOBIAS^JONATHAN^^^^^^EPIC^^^^PROVID||||||"
                + "CLR|||||60000013647|SELF|||||||||||||||||||||HOV_CONF|^^^1000^^^^^^^||20150107161438||||||||||" + '\r'
            + "PV2||||||||20150107161438||||CT BRAIN W WO CONTRAST||||||||||N|||||||||||||||||||||||||||" + '\r'
            + "ZPV||||||||||||20150107161438|||||||||" + '\r'
            + "AL1|1||33361^NO KNOWN ALLERGIES^^NOTCOMPUTRITION^NO KNOWN ALLERGIES^EXTELG||||||" + '\r'
            + "DG1|1|DX|784.0^Headache^DX|Headache||VISIT" + '\r'
            + "GT1|1|1000235129|MDCLS9^MC9^^||111 HOVER STREET^^LOS ANGELES^CA^90032^USA^^^LOS ANGELE|(310)725-6952^^^^^310^7256952||19700109|F|P/F|SLF|"
                + "565-33-2222|||||^^^^^USA|||UNKNOWN|||||||||||||||||||||||||||||" + '\r'
            + "UB2||||||||" + '\r'
            + '\n';

    static final String TEST_ACKNOWLEDGEMENT =
        "MSH|^~\\&|JCAPS|CC|ADT|EPIC|20161206193919|RISTECH|ACK^A08|00001|D|2.3^^|||||||" + '\r'
            + "MSA|AA|00001|" + '\r'
            + '\n';

    static final byte[] EXCEPTION_PACKET = null;
    static final byte[] EMPTY_PACKET = new byte[0];
    static final byte[] START_PACKET = PayloadBuilder.build(START_OF_BLOCK);
    static final byte[] END_PACKET = PayloadBuilder.build(END_OF_BLOCK, END_OF_DATA);

    FakeSocket fakeSocket = new FakeSocket();

    void assertSocketOpen() throws Exception {
        assertTrue("socket should have been connected", fakeSocket.connected);
        assertFalse("shutdownInput() should not have been called", fakeSocket.inputShutdown);
        assertFalse("shutdownOutput() should not have been called", fakeSocket.outputShutdown);
        assertFalse("close() should not have been called", fakeSocket.closed);
        assertNotNull("socket should have an input stream", fakeSocket.fakeSocketInputStream);
    }

    void assertSocketClosed() throws Exception {
        assertTrue("socket should have been connected", fakeSocket.connected);
        assertTrue("shutdownInput() should have been called", fakeSocket.inputShutdown);
        assertTrue("shutdownOutput() should have been called", fakeSocket.outputShutdown);
        assertTrue("close() should have been called", fakeSocket.closed);
        assertFalse("SO_LINGER should not be enabled", fakeSocket.linger);
    }

    void assertSocketReset() throws Exception {
        assertTrue("socket should have been connected", fakeSocket.connected);
        assertTrue("close() should have been called", fakeSocket.closed);
        assertTrue("SO_LINGER should be enabled", fakeSocket.linger);
        assertEquals("SO_LINGER timeout should be 0", 0, fakeSocket.lingerTimeout);
    }

    <E extends Exception> void expectedExceptionFailure(Class<E> expected) throws Exception {
        fail("Expected exception " + expected.getName() + " was not thrown");
    }

    class FakeSocket extends Socket {
        boolean connected = true;
        boolean inputShutdown;
        boolean outputShutdown;
        boolean closed;
        int receiveBufferSize = 1024;
        int sendBufferSize = 1024;
        int timeout = 1000;
        boolean linger;
        int lingerTimeout = 1024;
        FakeSocketInputStream fakeSocketInputStream = new FakeSocketInputStream();

        FakeSocket() {
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public boolean isInputShutdown() {
            return inputShutdown;
        }

        @Override
        public boolean isOutputShutdown() {
            return outputShutdown;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void shutdownInput() throws IOException {
            inputShutdown = true;
        }

        @Override
        public void shutdownOutput() throws IOException {
            outputShutdown = true;
        }

        @Override
        public synchronized void close() throws IOException {
            closed = true;
        }

        @Override
        public int getSoLinger() throws SocketException {
            if (linger) {
                return lingerTimeout;
            }

            return -1;
        }

        @Override
        public void setSoLinger(boolean on, int linger) throws SocketException {
            this.linger = on;
            this.lingerTimeout = linger;
        }

        @Override
        public synchronized int getReceiveBufferSize() throws SocketException {
            return receiveBufferSize;
        }

        @Override
        public synchronized void setReceiveBufferSize(int size) throws SocketException {
            this.receiveBufferSize = size;
        }

        @Override
        public synchronized int getSendBufferSize() throws SocketException {
            return sendBufferSize;
        }

        @Override
        public synchronized void setSendBufferSize(int size) throws SocketException {
            this.sendBufferSize = size;
        }

        @Override
        public synchronized int getSoTimeout() throws SocketException {
            return timeout;
        }

        @Override
        public synchronized void setSoTimeout(int timeout) throws SocketException {
            this.timeout = timeout;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            if (fakeSocketInputStream == null) {
                throw new IOException("Faking getInputStream failure");
            }
            return fakeSocketInputStream;
        }

    }

    class FakeSocketInputStream extends InputStream {
        boolean useSocketExceptionOnNullPacket = true;
        private Queue<ByteArrayInputStream> packetQueue = new LinkedList<>();

        FakeSocketInputStream() {
        }

        @Override
        public int read() throws IOException {
            if (packetQueue.size() > 0) {
                if (packetQueue.peek() == null) {
                    if (useSocketExceptionOnNullPacket) {
                        throw new SocketException("Faking Socket read() failure - simulating reset");
                    } else {
                        throw new IOException("Faking Socket read() failure");
                    }
                }
                int answer = packetQueue.element().read();
                if (answer == -1 || packetQueue.element().available() == 0) {
                    packetQueue.remove();
                }
                return answer;
            }

            throw new SocketTimeoutException("Faking Socket read() Timeout");
        }

        @Override
        public int read(byte[] buffer) throws IOException {
            if (packetQueue.size() > 0) {
                if (packetQueue.peek() == null) {
                    if (useSocketExceptionOnNullPacket) {
                        throw new SocketException("Faking Socket read(byte[]) failure - simulating reset");
                    } else {
                        throw new IOException("Faking Socket read(byte[]) failure");
                    }
                }
                int answer = packetQueue.element().read(buffer);
                if (answer == -1 || packetQueue.element().available() == 0) {
                    packetQueue.remove();
                }
                return answer;
            }

            throw new SocketTimeoutException("Faking Socket read(byte[]) Timeout");
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (packetQueue.size() > 0) {
                if (packetQueue.peek() == null) {
                    if (useSocketExceptionOnNullPacket) {
                        throw new SocketException("Faking Socket read(byte[], int, int) failure - simulating reset");
                    } else {
                        throw new IOException("Faking Socket read(byte[], int, int) failure");
                    }
                }
                int answer = packetQueue.element().read(buffer, offset, length);
                if (answer == -1 || packetQueue.element().available() == 0) {
                    packetQueue.remove();
                }

                return answer;
            }

            throw new SocketTimeoutException("Faking Socket read(byte[], int, int) Timeout");
        }

        @Override
        public int available() throws IOException {
            if (packetQueue.size() > 0) {
                return packetQueue.element().available();
            }

            return 0;
        }

        public FakeSocketInputStream addPacket(char... packet) {
            this.packetQueue.add(new ByteArrayInputStream(PayloadBuilder.build(packet)));

            return this;
        }

        public FakeSocketInputStream addPacket(byte[] bytes) throws IOException {
            if (bytes != null) {
                this.packetQueue.add(new ByteArrayInputStream(bytes));
            } else {
                this.packetQueue.add(null);
            }

            return this;
        }

        public FakeSocketInputStream addPacket(byte[] bytes, byte[]... byteArrays) throws IOException {
            PayloadBuilder builder = new PayloadBuilder(bytes);
            for (byte[] additionalBytes : byteArrays) {
                builder.append(additionalBytes);
            }
            this.packetQueue.add(new ByteArrayInputStream(builder.build()));

            return this;
        }

        public FakeSocketInputStream addPacket(String... strings) throws IOException {
            this.packetQueue.add(new ByteArrayInputStream(PayloadBuilder.build(strings)));

            return this;
        }

        public FakeSocketInputStream addPackets(String message, char delimiter) throws IOException {
            StringTokenizer tokenizer = new StringTokenizer(message, String.valueOf(delimiter), true);
            while (tokenizer.hasMoreTokens()) {
                addPacket(tokenizer.nextToken());
            }

            return this;
        }

        public FakeSocketInputStream addPackets(char... packets) {
            for (char c : packets) {
                addPacket(c);
            }

            return this;
        }

        public FakeSocketInputStream addPackets(byte[]... packets) throws IOException {
            for (byte[] packet : packets) {
                addPacket(packet);
            }

            return this;
        }

        public FakeSocketInputStream addPackets(byte[] bytes, String s) throws IOException {
            return addPacket(bytes).addPacket(s);
        }
    }
}