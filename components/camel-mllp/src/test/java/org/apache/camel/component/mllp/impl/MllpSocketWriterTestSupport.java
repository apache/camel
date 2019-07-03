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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public abstract class MllpSocketWriterTestSupport {
    static final String TEST_MESSAGE =
        "MSH|^~\\&|REQUESTING|ICE|INHOUSE|RTH00|20161206193919||ORM^O01|00001|D|2.3|||||||" + '\r'
            + "PID|1||ICE999999^^^ICE^ICE||Testpatient^Testy^^^Mr||19740401|M|||123 Barrel Drive^^^^SW18 4RT|||||2||||||||||||||" + '\r'
            + "NTE|1||Free text for entering clinical details|" + '\r'
            + "PV1|1||^^^^^^^^Admin Location|||||||||||||||NHS|" + '\r'
            + "ORC|NW|213||175|REQ||||20080808093202|ahsl^^Administrator||G999999^TestDoctor^GPtests^^^^^^NAT|^^^^^^^^Admin Location | 819600|200808080932||RTH00||ahsl^^Administrator||" + '\r'
            + "OBR|1|213||CCOR^Serum Cortisol ^ JRH06|||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||" + '\r'
            + "OBR|2|213||GCU^Serum Copper ^ JRH06 |||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||" + '\r'
            + "OBR|3|213||THYG^Serum Thyroglobulin ^JRH06|||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||" + '\r'
            + '\n';

    static final String TEST_ACKNOWLEDGEMENT =
        "MSH|^~\\&|INHOUSE|RTH00|REQUESTING|ICE|20161206193919||ACK^O01|00001|D|2.3|||||||" + '\r'
            + "MSA|AA|00001|" + '\r'
            + '\n';

    FakeSocket fakeSocket = new FakeSocket();

    class FakeSocket extends Socket {
        boolean connected = true;
        boolean closed;

        FakeSocketOutputStream fakeSocketOutputStream = new FakeSocketOutputStream();

        FakeSocket() {
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            if (fakeSocketOutputStream == null) {
                return super.getOutputStream();
            }
            return fakeSocketOutputStream;
        }

        byte[] payload() {
            if (fakeSocketOutputStream != null) {
                return fakeSocketOutputStream.fakeOutputStream.toByteArray();
            }

            return null;
        }
    }

    class FakeSocketOutputStream extends OutputStream {
        ByteArrayOutputStream fakeOutputStream = new ByteArrayOutputStream();

        boolean failOnWrite;
        boolean failOnWriteArray;

        Byte writeFailOn;
        byte[] writeArrayFailOn;

        FakeSocketOutputStream() {
        }

        @Override
        public void write(int b) throws IOException {
            if (failOnWrite) {
                throw new IOException("Faking write failure");
            } else if (writeFailOn != null && writeFailOn == b) {
                throw new IOException("Faking write failure");
            }

            fakeOutputStream.write(b);
        }

        @Override
        public void write(byte[] array, int off, int len) throws IOException {
            if (failOnWriteArray) {
                throw new IOException("Faking write array failure");
            }

            if (writeArrayFailOn != null) {
                if (writeArrayFailOn == array) {
                    throw new IOException("Faking write array failure");
                }
                for (int i = 0; i < Math.min(len, writeArrayFailOn.length); ++i) {
                    if (array[off + i] != writeArrayFailOn[i]) {
                        super.write(array, off, len);
                        return;
                    }
                }
                throw new IOException("Faking write array failure");
            } else {
                super.write(array, off, len);
            }
        }
    }

}
