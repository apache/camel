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
package org.apache.camel.component.netty.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Sends HTTP requests on a socket as written, so a test controls the chunks of a request and the connection it uses,
 * and reads the responses with a timeout.
 */
final class RawHttpClient implements AutoCloseable {

    static final int TIMEOUT = 5000;

    private final Socket socket;
    private final InputStream in;

    RawHttpClient(int port) throws IOException {
        socket = new Socket("localhost", port);
        socket.setSoTimeout(TIMEOUT);
        in = socket.getInputStream();
    }

    /**
     * Sends the request and reads the response. The status is NO-RESPONSE when no response is received within the
     * timeout, and CLOSED when the connection is closed without a response.
     */
    Response send(String request) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write(request.getBytes(StandardCharsets.US_ASCII));
        out.flush();
        try {
            return readResponse();
        } catch (SocketTimeoutException e) {
            return new Response("NO-RESPONSE", Map.of(), "");
        }
    }

    /**
     * Sends the request and reads until the connection is closed. Returns false when the connection is not closed
     * within the timeout.
     */
    boolean sendAndAwaitClose(String request) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write(request.getBytes(StandardCharsets.US_ASCII));
        out.flush();
        try {
            in.readAllBytes();
            return true;
        } catch (SocketTimeoutException e) {
            return false;
        } catch (SocketException e) {
            // the connection was reset
            return true;
        }
    }

    static Response send(int port, String request) throws IOException {
        try (RawHttpClient client = new RawHttpClient(port)) {
            return client.send(request);
        }
    }

    static String get(String path) {
        return "GET " + path + " HTTP/1.1\r\nHost: localhost\r\n\r\n";
    }

    static String post(String path, String body) {
        return "POST " + path + " HTTP/1.1\r\nHost: localhost\r\nContent-Length: " + body.length() + "\r\n\r\n" + body;
    }

    static String chunkedPost(String path, String... chunks) {
        StringBuilder sb
                = new StringBuilder("POST " + path + " HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n\r\n");
        for (String chunk : chunks) {
            sb.append(Integer.toHexString(chunk.length())).append("\r\n").append(chunk).append("\r\n");
        }
        return sb.append("0\r\n\r\n").toString();
    }

    /**
     * A chunked request with a body of the given number of chunks of 1000 bytes each.
     */
    static String chunkedPost(String path, int chunks) {
        String[] body = new String[chunks];
        for (int i = 0; i < chunks; i++) {
            body[i] = String.valueOf((char) ('a' + i % 26)).repeat(1000);
        }
        return chunkedPost(path, body);
    }

    private Response readResponse() throws IOException {
        String status = readLine();
        if (status == null) {
            return new Response("CLOSED", Map.of(), "");
        }
        Map<String, String> headers = new HashMap<>();
        for (String line = readLine(); line != null && !line.isEmpty(); line = readLine()) {
            int idx = line.indexOf(':');
            headers.put(line.substring(0, idx).trim().toLowerCase(Locale.ENGLISH), line.substring(idx + 1).trim());
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        if ("chunked".equalsIgnoreCase(headers.get("transfer-encoding"))) {
            for (int size = Integer.parseInt(readLine().trim(), 16); size > 0; size = Integer.parseInt(readLine().trim(), 16)) {
                body.write(in.readNBytes(size));
                readLine();
            }
            readLine();
        } else if (headers.containsKey("content-length")) {
            body.write(in.readNBytes(Integer.parseInt(headers.get("content-length"))));
        }
        return new Response(status, headers, body.toString(StandardCharsets.UTF_8));
    }

    private String readLine() throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                int len = sb.length();
                return len > 0 && sb.charAt(len - 1) == '\r' ? sb.substring(0, len - 1) : sb.toString();
            }
            sb.append((char) c);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    record Response(String status, Map<String, String> headers, String body) {
    }
}
