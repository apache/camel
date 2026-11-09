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
package org.apache.camel.cli.connector;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.camel.console.DevConsole;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * The send action with a repeat or a folder of messages ({@code camel cmd send --repeat=50 --interval=200ms}, and
 * {@code --body=file:<folder>/}): it sends the messages through the send dev console one after another, with a pause
 * between them, and answers with a summary when done (CAMEL-26767). It runs on a thread of its own, so the status and
 * the other actions of the connector go on meanwhile, and it stops when the client gives up the request.
 * <p/>
 * The messages of a folder are its files in name order, one message per file, leaving out hidden files and the
 * {@code .properties} files: {@code 001.properties} has the headers of {@code 001.json}. The folder is read by the
 * integration, relative to its working directory, as the file of a single body is.
 */
public final class RepeatedSend {

    /** The most messages one send action sends; camel cmd send checks it before it asks. */
    public static final int MAX_MESSAGES = 10000;
    /** The longest pause between two messages, in millis; camel cmd send checks it before it asks. */
    public static final long MAX_INTERVAL = 60000;
    private static final int MAX_ERRORS = 5;

    private RepeatedSend() {
    }

    /** A message to send: its body, and its own headers (from the properties file next to it). */
    record Message(String body, Map<String, Object> headers) {
    }

    /**
     * The pause between two messages, as text: a time such as {@code 200}, {@code 200ms} or {@code 1s}, or a range such
     * as {@code 100..500} or {@code 100ms..2s} (as the {@code duration=} of a chaos rule), from which each pause is
     * drawn at random, so the messages do not come at a steady pace.
     *
     * @return                          the shortest and the longest pause in millis (the same for a time)
     * @throws IllegalArgumentException when it is not a time or a range, or longer than {@link #MAX_INTERVAL}
     */
    public static long[] interval(String text) {
        if (text == null || text.isBlank()) {
            return new long[] { 0, 0 };
        }
        String[] parts = text.strip().split("\\.\\.", -1);
        if (parts.length > 2) {
            throw new IllegalArgumentException("The interval is not a time or a range such as 100..500: " + text);
        }
        long[] answer = new long[2];
        try {
            answer[0] = TimeUtils.toMilliSeconds(parts[0].strip());
            answer[1] = parts.length == 2 ? TimeUtils.toMilliSeconds(parts[1].strip()) : answer[0];
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("The interval is not a time or a range such as 100..500: " + text);
        }
        if (answer[0] < 0 || answer[1] < answer[0]) {
            throw new IllegalArgumentException("The interval must go from the shortest to the longest pause: " + text);
        }
        if (answer[1] > MAX_INTERVAL) {
            throw new IllegalArgumentException("The interval must be at most 60s, was: " + text);
        }
        return answer;
    }

    /** Whether the send action repeats, or sends a folder of messages, rather than one message. */
    static boolean isRepeated(JsonObject root) {
        return root.getLongOrDefault("repeat", 0) > 1 || folder(root.getString("body")) != null;
    }

    /** The folder a body names ({@code file:<folder>}), or null when it names none. */
    static File folder(String body) {
        if (body == null || !body.startsWith("file:")) {
            return null;
        }
        File file = new File(body.substring(5));
        return file.isDirectory() ? file : null;
    }

    /**
     * The messages to send: the files of the folder the body names, or the body itself.
     *
     * @throws IllegalArgumentException when the folder has no message
     */
    static List<Message> messages(String body) throws IOException {
        File folder = folder(body);
        if (folder == null) {
            return List.of(new Message(body, Map.of()));
        }
        File[] files = folder.listFiles(f -> f.isFile() && !f.getName().startsWith(".")
                && !f.getName().endsWith(".properties"));
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("No messages in the folder " + folder);
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        List<Message> answer = new ArrayList<>();
        for (File file : files) {
            answer.add(new Message("file:" + file.getAbsolutePath(), headers(file)));
        }
        return answer;
    }

    /** The headers of a message of a folder: the properties file with the same name, such as 001.properties. */
    private static Map<String, Object> headers(File file) throws IOException {
        File props = new File(file.getParentFile(), FileUtil.stripExt(file.getName()) + ".properties");
        if (!props.isFile()) {
            return Map.of();
        }
        Properties properties = new Properties();
        try (InputStream is = Files.newInputStream(props.toPath())) {
            properties.load(is);
        }
        Map<String, Object> answer = new LinkedHashMap<>();
        properties.stringPropertyNames().stream().sorted().forEach(k -> answer.put(k, properties.getProperty(k)));
        return answer;
    }

    /**
     * Sends the messages and sums up how it went.
     *
     * @param  console              the send dev console
     * @param  options              the options of the send console without the body and headers (endpoint, exchange
     *                              pattern and so on)
     * @param  headers              the headers of every message; the headers of a message of a folder win
     * @param  body                 the body: a message, a file, or a folder of messages
     * @param  repeat               how many messages to send in all, going round the messages again; 0 or less to send
     *                              each message once
     * @param  interval             the pause between two messages, in millis, or the shortest pause of a range
     * @param  intervalMax          the longest pause of a range, from which each pause is drawn at random; the interval
     *                              or less for the same pause each time
     * @throws InterruptedException when the client gave up the request
     */
    static JsonObject send(
            DevConsole console, Map<String, Object> options, Map<String, Object> headers, String body, long repeat,
            long interval, long intervalMax)
            throws IOException, InterruptedException {
        if (repeat > MAX_MESSAGES) {
            throw new IllegalArgumentException("At most " + MAX_MESSAGES + " messages can be sent at a time, not " + repeat);
        }
        long longest = Math.max(interval, intervalMax);
        if (interval < 0 || longest > MAX_INTERVAL) {
            throw new IllegalArgumentException("The interval must be between 0 and " + MAX_INTERVAL + " millis");
        }
        List<Message> messages = messages(body);
        long count = repeat > 0 ? repeat : messages.size();
        long start = System.currentTimeMillis();
        String endpoint = null;
        long sent = 0;
        long failed = 0;
        Map<String, Long> errors = new LinkedHashMap<>();
        for (long i = 0; i < count; i++) {
            if (i > 0 && longest > 0) {
                Thread.sleep(longest > interval ? ThreadLocalRandom.current().nextLong(interval, longest + 1) : interval);
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("The client gave up the request");
            }
            Message message = messages.get((int) (i % messages.size()));
            Map<String, Object> args = new LinkedHashMap<>(options);
            args.putAll(headers);
            args.putAll(message.headers());
            if (message.body() != null) {
                args.put("body", message.body());
            }
            JsonObject response = (JsonObject) console.call(DevConsole.MediaType.JSON, args);
            if (endpoint == null) {
                endpoint = response.getString("endpoint");
            }
            sent++;
            if ("success".equals(response.getString("status"))) {
                continue;
            }
            failed++;
            JsonObject exception = response.getMap("exception");
            String type = exception != null ? exception.getString("type") : null;
            String error = exception != null
                    ? simpleName(type) + ": " + exception.getString("message")
                    : response.getString("status");
            if (errors.containsKey(error) || errors.size() < MAX_ERRORS) {
                errors.merge(error, 1L, Long::sum);
            }
            if (type != null && type.endsWith("NoSuchEndpointException")) {
                // nothing will reach an endpoint that is not there
                break;
            }
        }
        JsonObject answer = new JsonObject();
        answer.put("timestamp", start);
        answer.put("status", failed > 0 ? "failed" : "success");
        answer.put("elapsed", System.currentTimeMillis() - start);
        if (endpoint != null) {
            answer.put("endpoint", endpoint);
        }
        answer.put("messages", count);
        answer.put("sent", sent);
        answer.put("failed", failed);
        JsonArray list = new JsonArray();
        errors.forEach((message, n) -> {
            JsonObject jo = new JsonObject();
            jo.put("message", message);
            jo.put("count", n);
            list.add(jo);
        });
        answer.put("errors", list);
        return answer;
    }

    private static String simpleName(String type) {
        return type != null && type.lastIndexOf('.') >= 0 ? type.substring(type.lastIndexOf('.') + 1) : type;
    }
}
