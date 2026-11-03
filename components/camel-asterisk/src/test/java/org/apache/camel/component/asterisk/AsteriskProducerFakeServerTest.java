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
package org.apache.camel.component.asterisk;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.asteriskjava.manager.ResponseEvents;
import org.asteriskjava.manager.event.PeerEntryEvent;
import org.asteriskjava.manager.event.PeerlistCompleteEvent;
import org.asteriskjava.manager.event.QueueMemberEvent;
import org.asteriskjava.manager.event.QueueParamsEvent;
import org.asteriskjava.manager.event.QueueStatusCompleteEvent;
import org.asteriskjava.manager.event.ResponseEvent;
import org.asteriskjava.manager.internal.ManagerConnectionImpl;
import org.asteriskjava.manager.response.ExtensionStateResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Runs the producer against a fake Asterisk Manager Interface (AMI) server on a random port.
 */
public class AsteriskProducerFakeServerTest extends CamelTestSupport {

    private FakeAmiServer server;
    private AsteriskProducer producer;

    @BeforeEach
    void startFakeServer() throws Exception {
        server = new FakeAmiServer();
        server.start();

        AsteriskEndpoint endpoint = context.getEndpoint(
                "asterisk:fake?hostname=localhost&username=admin&password=secret&action=EXTENSION_STATE",
                AsteriskEndpoint.class);

        ManagerConnectionImpl managerConnection = new ManagerConnectionImpl();
        managerConnection.setHostname(InetAddress.getLoopbackAddress().getHostAddress());
        managerConnection.setPort(server.getPort());
        managerConnection.setUsername("admin");
        managerConnection.setPassword("secret");

        producer = new AsteriskProducer(endpoint, new AsteriskConnection(managerConnection));
        producer.start();
    }

    @AfterEach
    void stopFakeServer() throws Exception {
        if (producer != null) {
            producer.stop();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void testQueueStatusReturnsTheEvents() throws Exception {
        Exchange exchange = send(AsteriskAction.QUEUE_STATUS);

        ResponseEvents responseEvents = assertInstanceOf(ResponseEvents.class, exchange.getMessage().getBody());
        assertEquals("Success", responseEvents.getResponse().getResponse());

        List<ResponseEvent> events = new ArrayList<>(responseEvents.getEvents());
        assertEquals(3, events.size());
        QueueParamsEvent params = assertInstanceOf(QueueParamsEvent.class, events.get(0));
        assertEquals("support", params.getQueue());
        assertEquals(2, params.getCalls());
        QueueMemberEvent member = assertInstanceOf(QueueMemberEvent.class, events.get(1));
        assertEquals("SIP/1001", member.getLocation());
        assertInstanceOf(QueueStatusCompleteEvent.class, events.get(2));
    }

    @Test
    void testSipPeersReturnsTheEvents() throws Exception {
        Exchange exchange = send(AsteriskAction.SIP_PEERS);

        ResponseEvents responseEvents = assertInstanceOf(ResponseEvents.class, exchange.getMessage().getBody());
        assertEquals("Success", responseEvents.getResponse().getResponse());

        List<ResponseEvent> events = new ArrayList<>(responseEvents.getEvents());
        assertEquals(3, events.size());
        assertEquals("1001", assertInstanceOf(PeerEntryEvent.class, events.get(0)).getObjectName());
        assertEquals("1002", assertInstanceOf(PeerEntryEvent.class, events.get(1)).getObjectName());
        assertInstanceOf(PeerlistCompleteEvent.class, events.get(2));
    }

    @Test
    void testExtensionStateReturnsTheResponse() throws Exception {
        Exchange exchange = producer.getEndpoint().createExchange();
        exchange.getMessage().setHeader(AsteriskConstants.EXTENSION, "1001");
        exchange.getMessage().setHeader(AsteriskConstants.CONTEXT, "default");
        producer.process(exchange);

        ExtensionStateResponse response
                = assertInstanceOf(ExtensionStateResponse.class, exchange.getMessage().getBody());
        assertEquals("1001", response.getExten());
        assertEquals(0, response.getStatus());
    }

    private Exchange send(AsteriskAction action) throws Exception {
        Exchange exchange = producer.getEndpoint().createExchange();
        exchange.getMessage().setHeader(AsteriskConstants.ACTION, action);
        producer.process(exchange);
        return exchange;
    }

    /**
     * Answers the AMI actions of asterisk-java: the login, the version lookup and the actions of the producer.
     */
    private static final class FakeAmiServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final List<Socket> sockets = new ArrayList<>();

        FakeAmiServer() throws IOException {
            serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        void start() {
            Thread thread = new Thread(this::acceptLoop, "FakeAmiServer");
            thread.setDaemon(true);
            thread.start();
        }

        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    synchronized (sockets) {
                        sockets.add(socket);
                    }
                    Thread thread = new Thread(() -> serve(socket), "FakeAmiServer-connection");
                    thread.setDaemon(true);
                    thread.start();
                } catch (IOException e) {
                    // closed
                    return;
                }
            }
        }

        private void serve(Socket socket) {
            try (socket;
                 BufferedReader reader
                         = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                OutputStream out = socket.getOutputStream();
                write(out, "Asterisk Call Manager/5.0.1\r\n");

                Map<String, String> packet = new HashMap<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isEmpty()) {
                        int pos = line.indexOf(':');
                        if (pos > 0) {
                            packet.put(line.substring(0, pos).trim().toLowerCase(), line.substring(pos + 1).trim());
                        }
                        continue;
                    }
                    if (!packet.isEmpty()) {
                        write(out, answer(packet.get("action"), packet.get("actionid")));
                        packet.clear();
                    }
                }
            } catch (IOException e) {
                // the client logged off or the server is closed
            }
        }

        private static String answer(String action, String actionId) {
            String id = "ActionID: " + actionId + "\r\n";
            switch (action == null ? "" : action.toLowerCase()) {
                case "challenge":
                    return "Response: Success\r\n" + id + "Challenge: 123456789\r\n\r\n";
                case "login":
                    return "Response: Success\r\n" + id + "Message: Authentication accepted\r\n\r\n";
                case "coresettings":
                    return "Response: Success\r\n" + id + "AMIversion: 5.0.1\r\nAsteriskVersion: 18.20.0\r\n\r\n";
                case "queuestatus":
                    return "Response: Success\r\n" + id + "EventList: start\r\nMessage: Queue status will follow\r\n\r\n"
                           + "Event: QueueParams\r\nQueue: support\r\nMax: 0\r\nStrategy: ringall\r\nCalls: 2\r\n" + id
                           + "\r\n"
                           + "Event: QueueMember\r\nQueue: support\r\nName: Alice\r\nLocation: SIP/1001\r\n"
                           + "StateInterface: SIP/1001\r\nMembership: static\r\nPenalty: 0\r\nCallsTaken: 0\r\n"
                           + "Status: 1\r\nPaused: 0\r\n" + id + "\r\n"
                           + "Event: QueueStatusComplete\r\n" + id + "EventList: Complete\r\nListItems: 2\r\n\r\n";
                case "sippeers":
                    return "Response: Success\r\n" + id + "EventList: start\r\nMessage: Peer status list will follow\r\n\r\n"
                           + "Event: PeerEntry\r\n" + id + "Channeltype: SIP\r\nObjectName: 1001\r\nStatus: OK (5 ms)\r\n\r\n"
                           + "Event: PeerEntry\r\n" + id + "Channeltype: SIP\r\nObjectName: 1002\r\nStatus: UNKNOWN\r\n\r\n"
                           + "Event: PeerlistComplete\r\n" + id + "EventList: Complete\r\nListItems: 2\r\n\r\n";
                case "extensionstate":
                    return "Response: Success\r\n" + id + "Message: Extension Status\r\nExten: 1001\r\nContext: default\r\n"
                           + "Hint: SIP/1001\r\nStatus: 0\r\n\r\n";
                case "logoff":
                    return "Response: Goodbye\r\n" + id + "Message: Thanks for all the fish.\r\n\r\n";
                default:
                    return "Response: Success\r\n" + id + "\r\n";
            }
        }

        private static void write(OutputStream out, String text) throws IOException {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
            synchronized (sockets) {
                for (Socket socket : sockets) {
                    socket.close();
                }
            }
        }
    }
}
