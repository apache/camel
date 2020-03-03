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
package org.apache.camel.test.tcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.fail;

/**
 * Various tests used to validate the behaviour of Java Sockets.
 *
 * The tests were for experimentation and don't have any assertions in them - JUnit provided a convenient framework to explore this behaviour.  These tests shouldn't be run with a normal build since
 * they don't have any assertions and don't validate any results.
 *
 * NOTE:  This class may be deleted in the future
 */
@Ignore(value = "Tests validating Java Socket behaviours")
public class JavaSocketTests {
    Logger log = LoggerFactory.getLogger(this.getClass());

    Socket clientSocket;
    ServerSocket serverSocket;

    int messageCount = 10;

    @Before
    public void setUp() throws Exception {
        serverSocket = new ServerSocket(0);
    }

    @After
    public void tearDown() throws Exception {
        if (null != clientSocket) {
            clientSocket.close();
        }

        if (null != serverSocket) {
            serverSocket.close();
        }
    }

    @Test
    public void testSocketReadOnClosedConnection() throws Exception {
        final Thread acceptThread = new Thread() {
            Logger log = LoggerFactory.getLogger("acceptThread");

            @Override
            public void run() {
                try {
                    Socket echoSocket = serverSocket.accept();

                    log.info("Accepted connection: {}", echoSocket.getInetAddress());

                    echoSocket.setSoTimeout(2000);

                    while (echoSocket.isConnected() && !echoSocket.isClosed()) {
                        StringBuilder responseBuilder = new StringBuilder(500);
                        InputStream reader = echoSocket.getInputStream();
                        OutputStream writer = echoSocket.getOutputStream();

                        do {
                            int readByte = -1;
                            int available = -1;
                            try {
                                available = reader.available();
                                log.info("InputStream.available returned {}", available);
                                readByte = reader.read();
                                log.trace("Processing byte: {}", readByte);
                                switch (readByte) {
                                    case -1:
                                        if (echoSocket.isConnected() && !echoSocket.isClosed()) {
                                            log.info("Available returned {}", reader.available());
                                            log.warn("Socket claims to still be open, but END_OF_STREAM received - closing echoSocket");
                                            try {
                                                echoSocket.close();
                                            } catch (Exception ex) {
                                                log.warn("Exception encountered closing echoSocket after END_OF_STREAM received", ex);
                                            }
                                        }
                                        break;
                                    case 10:
                                        log.info("Complete Message - Sending Response");
                                        byte[] response = responseBuilder.toString().getBytes();
                                        responseBuilder.setLength(0);
                                        writer.write(response, 0, response.length);
                                        writer.write('\n');
                                        break;
                                    default:
                                        responseBuilder.append((char) readByte);
                                }
                            } catch (SocketTimeoutException timeoutEx) {
                                log.info("Timeout reading data - available returned {}", available);
                            }
                        } while (echoSocket.isConnected() && !echoSocket.isClosed());
                    }

                } catch (IOException ioEx) {
                    log.error("IOException in run method", ioEx);
                } finally {
                    try {
                        serverSocket.close();
                    } catch (IOException ioEx) {
                        log.error("Exception encountered closing server socket", ioEx);
                    }
                }


                log.info("Finished processing connection");
            }

        };

        acceptThread.start();

        clientSocket = new Socket();
        clientSocket.setSoTimeout(1000);
        clientSocket.connect(serverSocket.getLocalSocketAddress(), 10000);
        clientSocket.setTcpNoDelay(true);
        log.info("Begining message send loop ");
        byte[] message = "Hello World".getBytes();
        BufferedReader reader;
        for (int i = 1; i <= messageCount; ++i) {
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream writer = clientSocket.getOutputStream();
            log.info("Sending payload");
            writer.write(message, 0, message.length);
            writer.flush();
            log.info("Sending terminator");
            writer.write('\n');
            writer.flush();
            log.info("Received Response #{}: {}", i, reader.readLine());
            Thread.sleep(1000);
        }

        log.info("Message send loop complete - closing connection");
        // Javadoc for Socket says closing the InputStream will close the connection
        clientSocket.getInputStream().close();
        if (!clientSocket.isClosed()) {
            log.warn("Closing input stream didn't close socket");
            clientSocket.close();
        }
        log.info("Sleeping ...");
        Thread.sleep(5000);

    }

    @Test
    public void testAvailableOnClosedConnection() throws Exception {
        final Thread acceptThread = new Thread() {
            Logger log = LoggerFactory.getLogger("acceptThread");

            @Override
            public void run() {
                try {
                    Socket echoSocket = serverSocket.accept();

                    log.info("Accepted connection: {}", echoSocket.getInetAddress());

                    echoSocket.setSoTimeout(2000);

                    while (echoSocket.isConnected() && !echoSocket.isClosed()) {
                        StringBuilder responseBuilder = new StringBuilder(500);
                        InputStream reader = echoSocket.getInputStream();
                        OutputStream writer = echoSocket.getOutputStream();

                        do {
                            int readByte = -1;
                            int available = -1;
                            try {
                                available = reader.available();
                                log.info("InputStream.available returned {}", available);
                                readByte = reader.read();
                                log.trace("Processing byte: {}", readByte);
                                switch (readByte) {
                                    case -1:
                                        if (echoSocket.isConnected() && !echoSocket.isClosed()) {
                                            log.info("Available returned {}", reader.available());
                                            log.warn("Socket claims to still be open, but END_OF_STREAM received - closing echoSocket");
                                            try {
                                                echoSocket.close();
                                            } catch (Exception ex) {
                                                log.warn("Exception encountered closing echoSocket after END_OF_STREAM received", ex);
                                            }
                                        }
                                        break;
                                    case 27: // Escape
                                        log.info("Received Escape - closing connection");
                                        echoSocket.close();
                                        break;
                                    case 10:
                                        log.info("Complete Message - Sending Response");
                                        byte[] response = responseBuilder.toString().getBytes();
                                        responseBuilder.setLength(0);
                                        writer.write(response, 0, response.length);
                                        writer.write('\n');
                                        break;
                                    default:
                                        responseBuilder.append((char) readByte);
                                }
                            } catch (SocketTimeoutException timeoutEx) {
                                log.info("Timeout reading data - available returned {}", available);
                            }
                        } while (echoSocket.isConnected() && !echoSocket.isClosed());
                    }

                } catch (IOException ioEx) {
                    log.error("IOException in run method", ioEx);
                } finally {
                    try {
                        serverSocket.close();
                    } catch (IOException ioEx) {
                        log.error("Exception encountered closing server socket", ioEx);
                    }
                }


                log.info("Finished processing connection");
            }

        };

        acceptThread.start();

        clientSocket = new Socket();
        clientSocket.setSoTimeout(1000);
        clientSocket.connect(serverSocket.getLocalSocketAddress(), 10000);
        clientSocket.setTcpNoDelay(true);
        log.info("Begining message send loop ");
        byte[] message = "Hello World".getBytes();
        BufferedReader reader;
        for (int i = 1; i <= messageCount; ++i) {
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream writer = clientSocket.getOutputStream();
            log.info("Sending payload");
            writer.write(message, 0, message.length);
            writer.flush();
            log.info("Sending terminator");
            writer.write('\n');
            writer.flush();
            log.info("Received Response #{}: {}", i, reader.readLine());
            Thread.sleep(1000);
        }

        log.info("Message send loop complete - closing connection");
        log.info("Client Socket available() returned {} before close", clientSocket.getInputStream().available());
        try {
            clientSocket.getInputStream().read();
            fail("read should have timed-out");
        } catch (SocketTimeoutException timeoutEx) {
            log.info("Client Socket read() timed-out before close");
        }
        clientSocket.getOutputStream().write(27);
        Thread.sleep(1000);
        log.info("Client Socket available() returned {} after close", clientSocket.getInputStream().available());
        log.info("Client Socket read() returned {} after close", clientSocket.getInputStream().read());
        // Javadoc for Socket says closing the InputStream will close the connection
        clientSocket.getInputStream().close();
        if (!clientSocket.isClosed()) {
            log.warn("Closing input stream didn't close socket");
            clientSocket.close();
        }
        log.info("Sleeping ...");
        Thread.sleep(5000);

    }
}
