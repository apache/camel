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
package org.apache.camel.component.xmpp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A small XMPP server for tests, on plain TCP, that does what Smack needs to connect, log in (SASL PLAIN, any password,
 * or ANONYMOUS), bind a resource, send messages and enter a multi-user chat room. Like a real server it only delivers
 * stanzas to sessions that logged in and bound a resource, and to room occupants that entered the room on their current
 * session, and it closes the older session with a conflict stream error when a session binds the full JID (account and
 * resource) of another session.
 */
final class FakeXmppServer implements AutoCloseable {

    static final String DOMAIN = "example.com";

    private static final Pattern STREAM = Pattern.compile("<stream:stream[^>]*>");
    private static final Pattern AUTH = Pattern.compile("<auth[^>]*?(/>|>[^<]*</auth>)");
    private static final Pattern IQ = Pattern.compile("<iq[^>]*?(/>|>.*?</iq>)", Pattern.DOTALL);
    private static final Pattern PRESENCE = Pattern.compile("<presence[^>]*?(/>|>.*?</presence>)", Pattern.DOTALL);
    private static final Pattern MESSAGE = Pattern.compile("<message[^>]*?(/>|>.*?</message>)", Pattern.DOTALL);
    private static final Pattern ID = Pattern.compile("\\sid=['\"]([^'\"]+)['\"]");
    private static final Pattern TO = Pattern.compile("\\sto=['\"]([^'\"]+)['\"]");
    private static final Pattern MECHANISM = Pattern.compile("\\smechanism=['\"]([^'\"]+)['\"]");
    private static final Pattern AUTH_DATA = Pattern.compile(">([^<]*)</auth>");
    private static final Pattern RESOURCE = Pattern.compile("<resource>([^<]+)</resource>");
    private static final Pattern BODY = Pattern.compile("<body[^>]*>(.*?)</body>", Pattern.DOTALL);

    private final ServerSocket serverSocket;
    private final List<Session> sessions = new CopyOnWriteArrayList<>();
    private final List<String[]> received = new CopyOnWriteArrayList<>();
    private final AtomicInteger counter = new AtomicInteger();
    private final AtomicInteger binds = new AtomicInteger();
    private final AtomicInteger conflicts = new AtomicInteger();
    private volatile boolean running = true;

    FakeXmppServer() throws IOException {
        serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread acceptor = new Thread(this::accept, "fake-xmpp-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    int getPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * The number of sessions that logged in and bound a resource.
     */
    long boundSessions() {
        return sessions.stream().filter(s -> s.jid != null && !s.socket.isClosed()).count();
    }

    /**
     * The number of times a session bound a resource since the server started.
     */
    int binds() {
        return binds.get();
    }

    /**
     * The number of sessions closed with a conflict stream error, as another session bound the same full JID.
     */
    int conflicts() {
        return conflicts.get();
    }

    /**
     * The number of sessions that are occupants of the given room.
     */
    long occupants(String room) {
        return sessions.stream().filter(s -> room.equals(s.room) && !s.socket.isClosed()).count();
    }

    /**
     * The bodies of the messages the clients sent to the given JID.
     */
    List<String> messagesTo(String jid) {
        return received.stream().filter(m -> jid.equals(m[0])).map(m -> m[1]).toList();
    }

    /**
     * Drops every connection, as a network failure or a server restart does.
     */
    void dropConnections() throws IOException {
        for (Session session : sessions) {
            session.socket.close();
        }
        sessions.clear();
    }

    /**
     * Sends a chat message to every bound session.
     */
    void sendChat(String from, String body) throws IOException {
        for (Session session : sessions) {
            if (session.jid != null && !session.socket.isClosed()) {
                session.write("<message from='" + from + "/phone' to='" + session.jid + "' type='chat' id='m"
                              + counter.incrementAndGet() + "'><body>" + body + "</body></message>");
            }
        }
    }

    /**
     * Sends a groupchat message to every occupant of the room.
     */
    void sendGroupChat(String room, String nick, String body) throws IOException {
        for (Session session : sessions) {
            if (room.equals(session.room) && !session.socket.isClosed()) {
                session.write("<message from='" + room + "/" + nick + "' to='" + session.jid + "' type='groupchat' id='g"
                              + counter.incrementAndGet() + "'><body>" + body + "</body></message>");
            }
        }
    }

    private void accept() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                Session session = new Session(socket);
                sessions.add(session);
                Thread reader = new Thread(session::read, "fake-xmpp-session");
                reader.setDaemon(true);
                reader.start();
            } catch (IOException e) {
                // closed
            }
        }
    }

    @Override
    public void close() throws IOException {
        running = false;
        serverSocket.close();
        dropConnections();
    }

    private final class Session {
        private final Socket socket;
        private final StringBuilder buffer = new StringBuilder();
        private volatile String user;
        private volatile String jid;
        private volatile String room;

        Session(Socket socket) {
            this.socket = socket;
        }

        synchronized void write(String xml) throws IOException {
            OutputStream out = socket.getOutputStream();
            out.write(xml.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        void read() {
            byte[] bytes = new byte[8192];
            try (InputStream in = socket.getInputStream()) {
                int n;
                while ((n = in.read(bytes)) != -1) {
                    buffer.append(new String(bytes, 0, n, StandardCharsets.UTF_8));
                    handle();
                }
            } catch (IOException e) {
                // the connection was closed
            }
        }

        private void handle() throws IOException {
            boolean progress = true;
            while (progress) {
                progress = false;
                for (Pattern pattern : List.of(STREAM, AUTH, IQ, PRESENCE, MESSAGE)) {
                    Matcher m = pattern.matcher(buffer);
                    if (m.find() && buffer.substring(0, m.start()).trim().replaceAll("<\\?xml[^>]*\\?>", "").isEmpty()) {
                        String element = m.group();
                        buffer.delete(0, m.end());
                        onElement(pattern, element);
                        progress = true;
                        break;
                    }
                }
            }
        }

        private void onElement(Pattern pattern, String element) throws IOException {
            if (pattern == STREAM) {
                String features = user != null
                        ? "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/>"
                        : "<mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'><mechanism>PLAIN</mechanism>"
                          + "<mechanism>ANONYMOUS</mechanism></mechanisms>";
                write("<?xml version='1.0'?><stream:stream xmlns='jabber:client' "
                      + "xmlns:stream='http://etherx.jabber.org/streams' id='s" + counter.incrementAndGet()
                      + "' from='" + DOMAIN + "' version='1.0' xml:lang='en'><stream:features>" + features
                      + "</stream:features>");
            } else if (pattern == AUTH) {
                user = "PLAIN".equals(attribute(MECHANISM, element))
                        ? new String(Base64.getDecoder().decode(content(AUTH_DATA, element)), StandardCharsets.UTF_8)
                                .split("\0")[1]
                        : "anon" + counter.incrementAndGet();
                write("<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>");
            } else if (pattern == IQ) {
                String id = attribute(ID, element);
                if (user == null) {
                    return;
                }
                if (element.contains("urn:ietf:params:xml:ns:xmpp-bind")) {
                    String resource = content(RESOURCE, element);
                    bind(user + "@" + DOMAIN + "/" + (resource != null ? resource : "camel"));
                    write("<iq type='result' id='" + id + "'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'><jid>" + jid
                          + "</jid></bind></iq>");
                } else if (element.contains("jabber:iq:roster")) {
                    write("<iq type='result' id='" + id + "' to='" + jid + "'><query xmlns='jabber:iq:roster'/></iq>");
                } else if (element.contains("type='get'") || element.contains("type=\"get\"")
                        || element.contains("type='set'") || element.contains("type=\"set\"")) {
                    String to = attribute(TO, element);
                    String from = to != null ? " from='" + to + "'" : "";
                    if (to != null && to.startsWith("conference.") && element.contains("disco#info")) {
                        // the multi-user chat service
                        write("<iq type='result' id='" + id + "'" + from + " to='" + jid + "'>"
                              + "<query xmlns='http://jabber.org/protocol/disco#info'>"
                              + "<identity category='conference' type='text' name='Rooms'/>"
                              + "<feature var='http://jabber.org/protocol/muc'/></query></iq>");
                    } else {
                        write("<iq type='result' id='" + id + "'" + from + " to='" + jid + "'/>");
                    }
                }
            } else if (pattern == PRESENCE) {
                String to = attribute(TO, element);
                if (jid == null || to == null || !element.contains("http://jabber.org/protocol/muc")) {
                    return;
                }
                if (element.contains("type='unavailable'") || element.contains("type=\"unavailable\"")) {
                    room = null;
                    write("<presence from='" + to + "' to='" + jid + "' type='unavailable'>"
                          + "<x xmlns='http://jabber.org/protocol/muc#user'><item affiliation='none' role='none'/>"
                          + "<status code='110'/></x></presence>");
                    return;
                }
                // entering a room: the room answers with the occupant's own presence (status 110)
                room = to.substring(0, to.indexOf('/'));
                write("<presence from='" + to + "' to='" + jid + "' id='" + attribute(ID, element) + "'>"
                      + "<x xmlns='http://jabber.org/protocol/muc#user'><item affiliation='none' role='participant'/>"
                      + "<status code='110'/></x></presence>");
            } else if (pattern == MESSAGE && jid != null) {
                String to = attribute(TO, element);
                String body = content(BODY, element);
                if (to != null && body != null) {
                    received.add(new String[] { to, body });
                }
            }
        }

        private void bind(String fullJid) throws IOException {
            // a server keeps one session per full JID: the older session is closed
            for (Session other : sessions) {
                if (other != this && fullJid.equals(other.jid) && !other.socket.isClosed()) {
                    conflicts.incrementAndGet();
                    other.write("<stream:error><conflict xmlns='urn:ietf:params:xml:ns:xmpp-streams'/></stream:error>"
                                + "</stream:stream>");
                    other.socket.close();
                }
            }
            jid = fullJid;
            binds.incrementAndGet();
        }

        private String attribute(Pattern pattern, String element) {
            Matcher m = pattern.matcher(element.substring(0, element.indexOf('>') + 1));
            return m.find() ? m.group(1) : null;
        }

        private String content(Pattern pattern, String element) {
            Matcher m = pattern.matcher(element);
            return m.find() ? m.group(1) : null;
        }
    }
}
