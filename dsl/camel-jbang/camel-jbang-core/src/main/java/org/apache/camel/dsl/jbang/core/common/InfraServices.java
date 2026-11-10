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
package org.apache.camel.dsl.jbang.core.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.LongPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * The services that {@code camel infra run} started on this machine, from their pid files in the Camel directory
 * ({@code infra-<service>-<pid>.json}, which hold what the service tells about itself, such as its brokers), with the
 * ports they listen on. A chaos experiment uses it to know that a broker is a local one from camel infra, not a real
 * system (CAMEL-26804).
 */
public final class InfraServices {

    // host:port, as in localhost:9092, PLAINTEXT://localhost:9092 or tcp://127.0.0.1:61616
    private static final Pattern HOST_PORT = Pattern.compile("([A-Za-z0-9._-]+|\\[[0-9A-Fa-f:]+\\]):(\\d{1,5})(?!\\d)");
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "0.0.0.0", "::1", "[::1]");

    /** A service camel infra runs: its name (such as kafka), its pid, and the ports it listens on. */
    public record Service(String name, long pid, Set<Integer> ports) {
    }

    private InfraServices() {
    }

    /** The services camel infra runs now, whose process is alive. */
    public static List<Service> running() {
        return running(CommandLineHelper.getCamelDir(),
                pid -> ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
    }

    static List<Service> running(Path camelDir, LongPredicate alive) {
        List<Service> answer = new ArrayList<>();
        try (Stream<Path> files = Files.list(camelDir)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (!name.startsWith("infra-") || !name.endsWith(".json") || name.lastIndexOf('-') <= 6) {
                    continue;
                }
                long pid;
                try {
                    pid = Long.parseLong(name.substring(name.lastIndexOf('-') + 1, name.length() - 5));
                } catch (NumberFormatException e) {
                    continue;
                }
                if (!alive.test(pid)) {
                    continue;
                }
                Set<Integer> ports = new TreeSet<>();
                try {
                    ports(Jsoner.deserialize(Files.readString(file)), null, ports);
                } catch (Exception e) {
                    // a pid file being written, or not ours: no ports known
                }
                answer.add(new Service(name.substring(6, name.lastIndexOf('-')), pid, ports));
            }
        } catch (IOException e) {
            // no Camel directory yet: nothing runs
        }
        answer.sort(Comparator.comparing(Service::name).thenComparingLong(Service::pid));
        return answer;
    }

    /** The ports in the data of a service: a field whose name has port in it, and the port of each host:port. */
    private static void ports(Object value, String key, Set<Integer> ports) {
        if (value instanceof JsonObject jo) {
            jo.forEach((k, v) -> ports(v, k, ports));
        } else if (value instanceof JsonArray ja) {
            ja.forEach(v -> ports(v, key, ports));
        } else if (value instanceof Number n && key != null && key.toLowerCase(Locale.ROOT).contains("port")) {
            ports.add(n.intValue());
        } else if (value instanceof String s) {
            if (key != null && key.toLowerCase(Locale.ROOT).contains("port") && s.matches("\\d{1,5}")) {
                ports.add(Integer.parseInt(s));
            }
            addresses(s).forEach((host, port) -> ports.add(port));
        }
    }

    /** The host:port addresses in a text, such as the brokers of Kafka or the URL of a broker. */
    static Map<String, Integer> addresses(String text) {
        Map<String, Integer> answer = new LinkedHashMap<>();
        if (text == null) {
            return answer;
        }
        Matcher m = HOST_PORT.matcher(text);
        while (m.find()) {
            int port = Integer.parseInt(m.group(2));
            if (port > 0 && port <= 65535) {
                answer.put(m.group(1).toLowerCase(Locale.ROOT) + ":" + port, port);
            }
        }
        return answer;
    }

    /**
     * The service camel infra runs that the addresses are all on, such as {@code kafka} for localhost:9092 when camel
     * infra runs Kafka on that port; null when one of them is on another host or another port, or there are none.
     */
    public static Service serving(List<String> texts, List<Service> services) {
        Service found = null;
        boolean any = false;
        for (String text : texts) {
            for (String address : addresses(text).keySet()) {
                any = true;
                String host = address.substring(0, address.lastIndexOf(':'));
                int port = Integer.parseInt(address.substring(address.lastIndexOf(':') + 1));
                Service service = LOCAL_HOSTS.contains(host)
                        ? services.stream().filter(s -> s.ports().contains(port)).findFirst().orElse(null) : null;
                if (service == null) {
                    return null;
                }
                found = found == null ? service : found;
            }
        }
        return any ? found : null;
    }
}
