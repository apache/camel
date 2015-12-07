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
package org.apache.camel.component.mllp;

import org.apache.camel.Exchange;
import org.apache.camel.component.mllp.impl.MllpSocketUtil;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

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

        String hl7Message = exchange.getOut().getBody(String.class);
        if (null == hl7Message) {
            hl7Message = exchange.getIn().getBody(String.class);
            log.debug("Processing message from 'inputStream' message");
        } else {
            log.debug("Processing message from 'outputStream' message");
        }

        MllpSocketUtil.writeEnvelopedMessage(hl7Message, endpoint.charset, socket, outputStream);

        String acknowledgement = MllpSocketUtil.readEnvelopedAcknowledgement(endpoint.charset, socket, inputStream);
        log.debug("Populating the exchange");

        exchange.getIn().setBody(acknowledgement, String.class);
    }

    void checkConnection() throws IOException {
        if (null == socket || socket.isClosed() || ! socket.isConnected() ) {
            socket = new Socket();

            socket.setKeepAlive(endpoint.keepAlive);
            socket.setTcpNoDelay(endpoint.tcpNoDelay);
            socket.setReceiveBufferSize( endpoint.receiveBufferSize );
            socket.setSendBufferSize( endpoint.sendBufferSize );
            socket.setReuseAddress(endpoint.reuseAddress);
            socket.setSoLinger(false, -1);

            // Read Timeout
            socket.setSoTimeout(endpoint.responseTimeout);

            log.debug("Connecting to socket on {}:{}", endpoint.getHostname(), endpoint.getPort());
            socket.connect(new InetSocketAddress(endpoint.getHostname(), endpoint.getPort()), endpoint.connectTimeout);
            outputStream = new BufferedOutputStream(socket.getOutputStream(), endpoint.sendBufferSize);
            inputStream = socket.getInputStream();
        }

    }
}
