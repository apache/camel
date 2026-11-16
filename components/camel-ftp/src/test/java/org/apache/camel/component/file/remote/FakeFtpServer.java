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
package org.apache.camel.component.file.remote;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * A minimal in-memory FTP server (passive mode only) that can stop answering on a connection that stays open, as a
 * server that hangs, or a network path that is lost without closing the connection, does.
 * <p>
 * It serves the commands the ftp client sends with the default options and {@code passiveMode=true}: login, SYST, TYPE,
 * PWD, CWD, CDUP, MKD, NOOP, PASV, LIST, NLST, RETR, STOR, DELE, RNFR/RNTO, SIZE, MDTM and QUIT. A silent connection
 * keeps reading and records every command it receives, but never answers again. New connections are served normally, so
 * a client that opens a new connection recovers.
 */
final class FakeFtpServer implements Closeable {

    /** How the server stops answering when the trigger command arrives. */
    enum Stall {
        /** The trigger command gets no reply: the client waits for the reply on the control connection. */
        BEFORE_REPLY,
        /**
         * The trigger RETR gets its 150 reply and half of the file on the data connection, then nothing more on either
         * connection: the client waits on the data connection.
         */
        MID_TRANSFER
    }

    private final ServerSocket serverSocket;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "fake-ftp-server");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final Set<String> directories = ConcurrentHashMap.newKeySet();
    private final List<Connection> connections = new CopyOnWriteArrayList<>();
    private final AtomicInteger connectionIds = new AtomicInteger();
    private volatile Predicate<String> trigger;
    private volatile Stall stall = Stall.BEFORE_REPLY;
    private volatile boolean closed;

    FakeFtpServer() throws IOException {
        serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        directories.add("/");
        executor.execute(this::acceptLoop);
    }

    int port() {
        return serverSocket.getLocalPort();
    }

    void addFile(String path, byte[] content) {
        String file = normalize(path);
        String parent = parentOf(file);
        while (!parent.equals("/")) {
            directories.add(parent);
            parent = parentOf(parent);
        }
        files.put(file, content);
    }

    Map<String, byte[]> files() {
        return files;
    }

    /**
     * The first connection that receives a command matching {@code command} stops answering, from that command on.
     */
    void stallOn(Predicate<String> command, Stall how) {
        this.stall = how;
        this.trigger = command;
    }

    /** Every open connection stops answering from now on. New connections are served. */
    void silenceOpenConnections() {
        connections.forEach(c -> c.silent = true);
    }

    /** The commands the client sent on a connection after that connection stopped answering. */
    List<String> commandsOnSilentConnections() {
        List<String> answer = new ArrayList<>();
        connections.forEach(c -> answer.addAll(c.unanswered));
        return answer;
    }

    /** How many control connections the client has opened so far. */
    int connectionsOpened() {
        return connectionIds.get();
    }

    /** How many connections that stopped answering the client has not closed. */
    long silentConnectionsOpen() {
        return connections.stream().filter(c -> c.silent && !c.ended).count();
    }

    @Override
    public void close() throws IOException {
        closed = true;
        serverSocket.close();
        for (Connection c : connections) {
            c.close();
        }
        executor.shutdownNow();
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket socket = serverSocket.accept();
                Connection c = new Connection(socket, connectionIds.incrementAndGet());
                connections.add(c);
                executor.execute(c);
            } catch (IOException e) {
                // closed
                return;
            }
        }
    }

    private final class Connection implements Runnable {

        private final Socket socket;
        private final int id;
        private final List<String> unanswered = new CopyOnWriteArrayList<>();
        private volatile boolean silent;
        private volatile boolean ended;
        private ServerSocket passive;
        // a data connection left open by a stalled transfer
        private Socket stalledData;
        private OutputStream out;
        private String cwd = "/";
        private String renameFrom;

        Connection(Socket socket, int id) {
            this.socket = socket;
            this.id = id;
        }

        @Override
        public void run() {
            try {
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out = socket.getOutputStream();
                reply("220 fake FTP server ready (connection " + id + ")");
                String line;
                while ((line = in.readLine()) != null) {
                    Predicate<String> t = trigger;
                    if (!silent && t != null && t.test(line)) {
                        // one shot: only the first matching connection stalls
                        trigger = null;
                        if (stall == Stall.MID_TRANSFER && line.startsWith("RETR ")) {
                            startTransferThenStall(line.substring(5).trim());
                        }
                        silent = true;
                        continue;
                    }
                    if (silent) {
                        unanswered.add(line);
                        continue;
                    }
                    if (!handle(line)) {
                        break;
                    }
                }
            } catch (IOException e) {
                // the client or the test closed the connection
            } finally {
                ended = true;
                close();
            }
        }

        private boolean handle(String line) throws IOException {
            int space = line.indexOf(' ');
            String cmd = (space < 0 ? line : line.substring(0, space)).toUpperCase();
            String arg = space < 0 ? "" : line.substring(space + 1).trim();
            switch (cmd) {
                case "USER" -> reply("331 Password required");
                case "PASS" -> reply("230 Logged in");
                case "SYST" -> reply("215 UNIX Type: L8");
                case "TYPE", "MODE", "STRU", "OPTS", "SITE", "NOOP" -> reply("200 OK");
                case "PWD", "XPWD" -> reply("257 \"" + cwd + "\" is the current directory");
                case "CWD" -> {
                    String dir = resolve(arg);
                    if (directories.contains(dir)) {
                        cwd = dir;
                        reply("250 OK");
                    } else {
                        reply("550 No such directory");
                    }
                }
                case "CDUP" -> {
                    cwd = parentOf(cwd);
                    reply("250 OK");
                }
                case "MKD" -> {
                    directories.add(resolve(arg));
                    reply("257 \"" + resolve(arg) + "\" created");
                }
                case "PASV" -> {
                    closePassive();
                    passive = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                    passive.setSoTimeout(10000);
                    int p = passive.getLocalPort();
                    reply("227 Entering Passive Mode (127,0,0,1," + (p >> 8) + "," + (p & 0xff) + ")");
                }
                case "LIST", "NLST" -> list(cmd, arg);
                case "RETR" -> retrieve(arg);
                case "STOR" -> store(arg);
                case "DELE" -> reply(files.remove(resolve(arg)) != null ? "250 Deleted" : "550 No such file");
                case "RNFR" -> {
                    renameFrom = resolve(arg);
                    reply(files.containsKey(renameFrom) ? "350 Ready for RNTO" : "550 No such file");
                }
                case "RNTO" -> {
                    byte[] data = renameFrom != null ? files.remove(renameFrom) : null;
                    if (data != null) {
                        addFile(resolve(arg), data);
                        reply("250 Renamed");
                    } else {
                        reply("550 No such file");
                    }
                }
                case "SIZE" -> {
                    byte[] data = files.get(resolve(arg));
                    reply(data != null ? "213 " + data.length : "550 No such file");
                }
                case "MDTM" -> reply(files.containsKey(resolve(arg)) ? "213 20250101000000" : "550 No such file");
                case "QUIT" -> {
                    reply("221 Bye");
                    return false;
                }
                default -> reply("502 Command not implemented");
            }
            return true;
        }

        private void list(String cmd, String arg) throws IOException {
            String dir = cwd;
            for (String token : arg.split(" ")) {
                if (!token.isEmpty() && !token.startsWith("-")) {
                    dir = resolve(token);
                }
            }
            StringBuilder listing = new StringBuilder();
            for (Map.Entry<String, byte[]> e : new TreeMap<>(files).entrySet()) {
                if (parentOf(e.getKey()).equals(dir)) {
                    String name = e.getKey().substring(e.getKey().lastIndexOf('/') + 1);
                    if (cmd.equals("NLST")) {
                        listing.append(name).append("\r\n");
                    } else {
                        listing.append("-rw-r--r-- 1 camel camel ").append(e.getValue().length)
                                .append(" Jan 01  2025 ").append(name).append("\r\n");
                    }
                }
            }
            transfer(listing.toString().getBytes(StandardCharsets.UTF_8));
        }

        private void retrieve(String arg) throws IOException {
            byte[] data = files.get(resolve(arg));
            if (data == null) {
                reply("550 No such file");
                return;
            }
            transfer(data);
        }

        private void transfer(byte[] data) throws IOException {
            reply("150 Opening data connection");
            try (Socket d = passive.accept(); OutputStream o = d.getOutputStream()) {
                o.write(data);
            } finally {
                closePassive();
            }
            reply("226 Transfer complete");
        }

        private void startTransferThenStall(String arg) throws IOException {
            byte[] data = files.get(resolve(arg));
            reply("150 Opening data connection");
            stalledData = passive.accept();
            OutputStream o = stalledData.getOutputStream();
            o.write(data, 0, data.length / 2);
            o.flush();
            // the rest of the file and the 226 reply never come, and the data connection stays open
        }

        private void store(String arg) throws IOException {
            reply("150 Opening data connection");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (Socket d = passive.accept(); InputStream i = d.getInputStream()) {
                i.transferTo(bos);
            } finally {
                closePassive();
            }
            addFile(resolve(arg), bos.toByteArray());
            reply("226 Transfer complete");
        }

        private String resolve(String path) {
            if (path.isEmpty()) {
                return cwd;
            }
            return normalize(path.startsWith("/") ? path : cwd + "/" + path);
        }

        private void reply(String line) throws IOException {
            out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        private void closePassive() {
            if (passive != null) {
                try {
                    passive.close();
                } catch (IOException e) {
                    // ignore
                }
                passive = null;
            }
        }

        void close() {
            closePassive();
            try {
                if (stalledData != null) {
                    stalledData.close();
                }
                socket.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    static String normalize(String path) {
        List<String> parts = new ArrayList<>();
        for (String p : path.split("/")) {
            if (p.isEmpty() || p.equals(".")) {
                continue;
            }
            if (p.equals("..")) {
                if (!parts.isEmpty()) {
                    parts.remove(parts.size() - 1);
                }
            } else {
                parts.add(p);
            }
        }
        return "/" + String.join("/", parts);
    }

    static String parentOf(String path) {
        int i = path.lastIndexOf('/');
        return i <= 0 ? "/" : path.substring(0, i);
    }
}
