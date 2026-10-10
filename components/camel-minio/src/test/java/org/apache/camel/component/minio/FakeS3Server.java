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
package org.apache.camel.component.minio;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A minimal in-process fake of the S3 API calls the minio consumer makes for a single bucket: head bucket, list objects
 * (v2, paginated with max-keys and start-after/continuation-token), stat, get and delete object.
 */
final class FakeS3Server {

    private final String bucket;
    private final HttpServer server;
    private final NavigableMap<String, byte[]> objects = new ConcurrentSkipListMap<>();
    private final Map<String, AtomicInteger> gets = new ConcurrentHashMap<>();
    private final Set<String> deleteWhenListed = ConcurrentHashMap.newKeySet();
    private final Set<String> deleteWhenStatted = ConcurrentHashMap.newKeySet();

    FakeS3Server(String bucket) throws IOException {
        this.bucket = bucket;
        this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        this.server.createContext("/", this::handle);
    }

    void start() {
        server.start();
    }

    void stop() {
        server.stop(0);
    }

    String endpoint() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    void put(String key, String content) {
        objects.put(key, content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The object is deleted right after a listing returned it, as if another consumer deleted it before the stat.
     */
    void deleteWhenListed(String key) {
        deleteWhenListed.add(key);
    }

    /**
     * The object is deleted right after a stat (HEAD) of it, as if another consumer deleted it before the get.
     */
    void deleteWhenStatted(String key) {
        deleteWhenStatted.add(key);
    }

    int gets(String key) {
        AtomicInteger n = gets.get(key);
        return n != null ? n.get() : 0;
    }

    private void handle(HttpExchange http) throws IOException {
        String path = http.getRequestURI().getRawPath();
        String key = path.length() > bucket.length() + 2 ? decode(path.substring(bucket.length() + 2)) : null;
        String method = http.getRequestMethod();
        if (key == null) {
            if ("GET".equals(method) && query(http).containsKey("list-type")) {
                list(http, query(http));
            } else {
                // head bucket, location, ...
                respond(http, 200, null, null);
            }
            return;
        }
        byte[] data = objects.get(key);
        switch (method) {
            case "HEAD" -> {
                if (data == null) {
                    respond(http, 404, null, null);
                } else {
                    objectHeaders(http, data);
                    http.sendResponseHeaders(200, -1);
                    http.close();
                    if (deleteWhenStatted.remove(key)) {
                        objects.remove(key);
                    }
                }
            }
            case "GET" -> {
                if (data == null) {
                    noSuchKey(http, key);
                } else {
                    gets.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
                    objectHeaders(http, data);
                    http.sendResponseHeaders(200, data.length);
                    try (OutputStream os = http.getResponseBody()) {
                        os.write(data);
                    }
                }
            }
            case "DELETE" -> {
                objects.remove(key);
                http.sendResponseHeaders(204, -1);
                http.close();
            }
            default -> respond(http, 405, null, null);
        }
    }

    private void list(HttpExchange http, Map<String, String> query) throws IOException {
        int maxKeys = query.containsKey("max-keys") ? Integer.parseInt(query.get("max-keys")) : 1000;
        String after = query.get("continuation-token");
        if (after == null) {
            after = query.get("start-after");
        }
        NavigableMap<String, byte[]> view = after != null ? objects.tailMap(after, false) : objects;
        StringBuilder contents = new StringBuilder();
        int count = 0;
        String last = null;
        for (Map.Entry<String, byte[]> e : view.entrySet()) {
            if (count == maxKeys) {
                break;
            }
            contents.append("<Contents><Key>").append(e.getKey()).append("</Key>")
                    .append("<LastModified>2026-10-09T00:00:00.000Z</LastModified>")
                    .append("<ETag>\"0123456789abcdef0123456789abcdef\"</ETag>")
                    .append("<Size>").append(e.getValue().length).append("</Size>")
                    .append("<StorageClass>STANDARD</StorageClass></Contents>");
            last = e.getKey();
            count++;
        }
        for (String key : deleteWhenListed) {
            if (contents.indexOf("<Key>" + key + "</Key>") >= 0 && deleteWhenListed.remove(key)) {
                objects.remove(key);
            }
        }
        boolean truncated = last != null && objects.higherKey(last) != null;
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                     + "<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">"
                     + "<Name>" + bucket + "</Name><Prefix></Prefix>"
                     + "<KeyCount>" + count + "</KeyCount><MaxKeys>" + maxKeys + "</MaxKeys>"
                     + "<IsTruncated>" + truncated + "</IsTruncated>"
                     + (truncated ? "<NextContinuationToken>" + last + "</NextContinuationToken>" : "")
                     + contents + "</ListBucketResult>";
        respond(http, 200, "application/xml", xml);
    }

    private static void objectHeaders(HttpExchange http, byte[] data) {
        http.getResponseHeaders().add("ETag", "\"0123456789abcdef0123456789abcdef\"");
        http.getResponseHeaders().add("Last-Modified", "Fri, 09 Oct 2026 00:00:00 GMT");
        http.getResponseHeaders().add("Content-Type", "text/plain");
        http.getResponseHeaders().add("Content-Length", String.valueOf(data.length));
    }

    private void noSuchKey(HttpExchange http, String key) throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Error><Code>NoSuchKey</Code>"
                     + "<Message>The specified key does not exist.</Message><Key>" + key + "</Key>"
                     + "<BucketName>" + bucket + "</BucketName><Resource>/" + bucket + "/" + key + "</Resource>"
                     + "<RequestId>1</RequestId><HostId>1</HostId></Error>";
        respond(http, 404, "application/xml", xml);
    }

    private static void respond(HttpExchange http, int status, String contentType, String body) throws IOException {
        if (body == null) {
            http.sendResponseHeaders(status, -1);
            http.close();
            return;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        http.getResponseHeaders().add("Content-Type", contentType);
        http.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = http.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> query(HttpExchange http) {
        Map<String, String> answer = new HashMap<>();
        String q = http.getRequestURI().getRawQuery();
        if (q != null) {
            for (String part : q.split("&")) {
                int eq = part.indexOf('=');
                answer.put(decode(eq >= 0 ? part.substring(0, eq) : part), eq >= 0 ? decode(part.substring(eq + 1)) : "");
            }
        }
        return answer;
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
