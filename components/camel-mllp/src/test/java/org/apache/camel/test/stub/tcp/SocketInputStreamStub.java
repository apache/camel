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

package org.apache.camel.test.stub.tcp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.StringTokenizer;

import org.apache.camel.test.util.PayloadBuilder;

public class SocketInputStreamStub extends InputStream {
    public boolean useSocketExceptionOnNullPacket = true;

    private Queue<Object> packetQueue = new LinkedList<>();

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
            Object element = packetQueue.element();
            if (element instanceof ByteArrayInputStream) {
                ByteArrayInputStream inputStreamElement = (ByteArrayInputStream) element;
                int answer = inputStreamElement.read();
                if (answer == -1 || inputStreamElement.available() == 0) {
                    packetQueue.remove();
                }
                return answer;
            } else if (element instanceof IOException) {
                packetQueue.remove();
                throw (IOException)element;
            }
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
            Object element = packetQueue.element();
            if (element instanceof ByteArrayInputStream) {
                ByteArrayInputStream inputStreamElement = (ByteArrayInputStream) element;
                int answer = inputStreamElement.read(buffer);
                if (answer == -1 || inputStreamElement.available() == 0) {
                    packetQueue.remove();
                }
                return answer;
            } else if (element instanceof IOException) {
                packetQueue.remove();
                throw (IOException)element;
            }
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
            Object element = packetQueue.element();
            if (element instanceof ByteArrayInputStream) {
                ByteArrayInputStream inputStreamElement = (ByteArrayInputStream) element;
                int answer = inputStreamElement.read(buffer, offset, length);
                if (answer == -1 || inputStreamElement.available() == 0) {
                    packetQueue.remove();
                }
                return answer;
            } else if (element instanceof IOException) {
                packetQueue.remove();
                throw (IOException)element;
            }
        }

        throw new SocketTimeoutException("Faking Socket read(byte[], int, int) Timeout");
    }

    @Override
    public int available() throws IOException {
        if (packetQueue.size() > 0) {
            Object element = packetQueue.element();
            if (element instanceof ByteArrayInputStream) {
                ByteArrayInputStream inputStreamElement = (ByteArrayInputStream) element;
                return inputStreamElement.available();
            }

            return 1;
        }

        return 0;
    }

    public SocketInputStreamStub addPacket(Exception exception) {
        this.packetQueue.add(exception);

        return this;
    }

    public SocketInputStreamStub addPacket(char... packet) {
        this.packetQueue.add(new ByteArrayInputStream(PayloadBuilder.build(packet)));

        return this;
    }

    public SocketInputStreamStub addPacket(byte[] bytes) throws IOException {
        if (bytes != null) {
            this.packetQueue.add(new ByteArrayInputStream(bytes));
        } else {
            this.packetQueue.add(null);
        }

        return this;
    }

    public SocketInputStreamStub addPacket(byte[] bytes, byte[]... byteArrays) throws IOException {
        PayloadBuilder builder = new PayloadBuilder(bytes);
        for (byte[] additionalBytes : byteArrays) {
            builder.append(additionalBytes);
        }
        this.packetQueue.add(new ByteArrayInputStream(builder.build()));

        return this;
    }

    public SocketInputStreamStub addPacket(String... strings) throws IOException {
        this.packetQueue.add(new ByteArrayInputStream(PayloadBuilder.build(strings)));

        return this;
    }

    public SocketInputStreamStub addPackets(String message, char delimiter) throws IOException {
        StringTokenizer tokenizer = new StringTokenizer(message, String.valueOf(delimiter), true);
        while (tokenizer.hasMoreTokens()) {
            addPacket(tokenizer.nextToken());
        }

        return this;
    }

    public SocketInputStreamStub addPackets(char... packets) {
        for (char c : packets) {
            addPacket(c);
        }

        return this;
    }

    public SocketInputStreamStub addPackets(byte[]... packets) throws IOException {
        for (byte[] packet : packets) {
            addPacket(packet);
        }

        return this;
    }

    public SocketInputStreamStub addPackets(byte[] bytes, String s) throws IOException {
        return addPacket(bytes).addPacket(s);
    }
}
