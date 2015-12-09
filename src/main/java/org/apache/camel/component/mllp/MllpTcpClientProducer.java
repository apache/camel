/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.mllp;

import org.apache.camel.Exchange;
import org.apache.camel.component.mllp.impl.MllpSocketUtil;
import org.apache.camel.impl.DefaultProducer;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

/**
 * The MLLP producer.
 */
public class MllpTcpClientProducer extends DefaultProducer {
    MllpEndpoint endpoint;

    Socket socket;
    BufferedOutputStream outputStream;
    InputStream inputStream;

    public MllpTcpClientProducer(MllpEndpoint endpoint) throws SocketException {
        super(endpoint);
        log.trace("MllpTcpClientProducer(endpoint)");

        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        log.trace("doStart()");

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        log.trace("doStop()");

        if (null != outputStream) {
            try {
                outputStream.close();
            } finally {
                outputStream = null;
            }
        }

        if (null != inputStream) {
            try {
                inputStream.close();
            } catch (IOException ioEx) {
                log.warn("Exception encountered closing the input stream for the client socket", ioEx);
            } finally {
                inputStream = null;
            }
        }

        if (null != socket) {
            if (!socket.isClosed()) {
                try {
                    socket.shutdownInput();
                } catch (IOException ioEx) {
                    log.warn("Exception encountered shutting down the input stream on the client socket", ioEx);
                }

                try {
                    socket.shutdownOutput();
                } catch (IOException ioEx) {
                    log.warn("Exception encountered shutting down the output stream on the client socket", ioEx);
                }

                try {
                    socket.close();
                } catch (IOException ioEx) {
                    log.warn("Exception encountered closing the client socket", ioEx);
                }
            }
            socket = null;
        }

        super.doStop();
    }

    @Override
    protected void doSuspend() throws Exception {
        log.trace("doSuspend()");

        super.doSuspend();
    }

    @Override
    protected void doResume() throws Exception {
        log.trace("doResume()");

        super.doSuspend();
    }

    @Override
    protected void doShutdown() throws Exception {
        log.trace("doShutdown()");

        super.doShutdown();
    }

    public void process(Exchange exchange) throws Exception {
        log.trace("process(exchange)");

        checkConnection();

        byte[] hl7MessageBytes = null;

        Object messageBody;
        if ( exchange.hasOut() ) {
            messageBody = exchange.getOut().getBody();
        } else {
            messageBody = exchange.getIn().getBody();
        }

        if (null != messageBody) {
            log.debug("Sending message to external system");
            if (messageBody instanceof byte[]) {
                MllpSocketUtil.writeEnvelopedMessageBytes(socket, (byte[]) messageBody);
            } else if (messageBody instanceof String) {
                MllpSocketUtil.writeEnvelopedMessageBytes(socket, ((String) messageBody).getBytes(endpoint.charset));
            }
            log.debug("Reading acknowledgement from external system");
            byte[] acknowledgementBytes = MllpSocketUtil.readEnvelopedAcknowledgementBytes(socket);
            if (null != acknowledgementBytes) {
                log.debug("Populating the exchange out body");
                if (endpoint.useString) {
                    exchange.getOut().setBody(new String(acknowledgementBytes, endpoint.charset), String.class);
                } else {
                    exchange.getOut().setBody(acknowledgementBytes, byte[].class);
                }
            }
        } else {
            log.error("Null Body - ignoring");
        }
    }

    void checkConnection() throws IOException {
        if (null == socket || socket.isClosed() || !socket.isConnected()) {
            socket = new Socket();

            socket.setKeepAlive(endpoint.keepAlive);
            socket.setTcpNoDelay(endpoint.tcpNoDelay);
            socket.setReceiveBufferSize(endpoint.receiveBufferSize);
            socket.setSendBufferSize(endpoint.sendBufferSize);
            socket.setReuseAddress(endpoint.reuseAddress);
            socket.setSoLinger(false, -1);

            // Read Timeout
            socket.setSoTimeout(endpoint.responseTimeout);

            log.debug("Connecting to socket on {}:{}", endpoint.getHostname(), endpoint.getPort());
            socket.connect(new InetSocketAddress(endpoint.getHostname(), endpoint.getPort()), endpoint.connectTimeout);
        }

    }
}
