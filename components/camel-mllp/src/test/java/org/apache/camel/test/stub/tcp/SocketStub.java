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
package org.apache.camel.test.stub.tcp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;

public class SocketStub extends Socket {
    public boolean connected = true;
    public boolean inputShutdown;
    public boolean outputShutdown;
    public boolean closed;
    public int receiveBufferSize = 1024;
    public int sendBufferSize = 1024;
    public int timeout = 1000;
    public boolean linger;
    public int lingerTimeout = 1024;
    public SocketInputStreamStub inputStreamStub = new SocketInputStreamStub();
    public SocketOutputStreamStub outputStreamStub = new SocketOutputStreamStub();

    public boolean returnNullInputStream;
    public boolean returnNullOutputStream;

    public boolean throwExceptionOnClose;
    public boolean throwExceptionOnShutdownInput;
    public boolean throwExceptionOnShutdownOutput;

    @Override
    public InputStream getInputStream() throws IOException {
        if (returnNullInputStream) {
            return null;
        }

        if (isClosed()) {
            throw new SocketException("Socket is closed");
        }
        if (!isConnected()) {
            throw new SocketException("Socket is not connected");
        }
        if (isOutputShutdown()) {
            throw new SocketException("Socket output is shutdown");
        }

        if (inputStreamStub == null) {
            throw new IOException("Faking getInputStream failure");
        }

        return inputStreamStub;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        if (returnNullOutputStream) {
            return null;
        }

        if (isClosed()) {
            throw new SocketException("Socket is closed");
        }
        if (!isConnected()) {
            throw new SocketException("Socket is not connected");
        }
        if (isOutputShutdown()) {
            throw new SocketException("Socket output is shutdown");
        }

        if (outputStreamStub == null) {
            throw new IOException("Faking getOutputStream failure");
        }

        return outputStreamStub;
    }


    @Override
    public void setSoLinger(boolean on, int linger) throws SocketException {
        this.linger = on;
        this.lingerTimeout = linger;
    }

    @Override
    public int getSoLinger() throws SocketException {
        if (linger) {
            return lingerTimeout;
        }

        return -1;
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
        if (throwExceptionOnShutdownInput) {
            throw new IOException("Faking a shutdownInput failure");
        }
    }

    @Override
    public void shutdownOutput() throws IOException {
        outputShutdown = true;
        if (throwExceptionOnShutdownOutput) {
            throw new IOException("Faking a shutdownOutput failure");
        }
    }


    @Override
    public synchronized void close() throws IOException {
        closed = true;
        if (throwExceptionOnClose) {
            throw new IOException("Faking a close failure");
        }
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

}
