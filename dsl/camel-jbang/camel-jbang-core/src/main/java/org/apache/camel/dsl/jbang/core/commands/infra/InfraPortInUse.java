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
package org.apache.camel.dsl.jbang.core.commands.infra;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Why a service did not start when a port it maps on the host is taken: artemis maps 1883 when mosquitto has it, and
 * Docker fails with "Bind for 0.0.0.0:1883 failed: port is already allocated" deep in a stack trace (CAMEL-24823).
 */
final class InfraPortInUse {

    // Docker: "Bind for 0.0.0.0:1883 failed: port is already allocated"
    // Podman: "rootlessport listen tcp 0.0.0.0:1883: bind: address already in use"
    private static final String[] IN_USE = { "port is already allocated", "address already in use" };
    private static final Pattern PORT = Pattern.compile(":(\\d{1,5})(?!\\d)");

    private InfraPortInUse() {
    }

    /**
     * The port on the host that a failure to start says is taken.
     *
     * @param  failure the failure of the start, with its causes
     * @return         the port, or -1 when the failure is not about a port in use, or does not name it
     */
    static int port(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable t = failure; t != null && seen.add(t); t = t.getCause()) {
            int port = port(t.getMessage());
            if (port > 0) {
                return port;
            }
        }
        return -1;
    }

    static int port(String message) {
        if (message == null) {
            return -1;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        for (String inUse : IN_USE) {
            int end = lower.indexOf(inUse);
            if (end > 0) {
                // the last port before the complaint is the one on the host
                Matcher m = PORT.matcher(message.substring(0, end));
                int port = -1;
                while (m.find()) {
                    port = Integer.parseInt(m.group(1));
                }
                if (port > 0 && port <= 65535) {
                    return port;
                }
            }
        }
        return -1;
    }

    /**
     * Whether the service data of a running service has the port: as a value ({@code "port":1883}) or in an address
     * ({@code "tcp://localhost:1883"}). Not a port that only starts the same ({@code 18830}), nor a container id that
     * starts with its digits.
     */
    static boolean hasPort(String serviceData, int port) {
        if (serviceData == null) {
            return false;
        }
        return Pattern.compile(":\\s*\"?" + port + "(?!\\w)").matcher(serviceData).find();
    }

    /**
     * The one line that tells the user which port is taken, by what, and the way out.
     *
     * @param service the service that did not start
     * @param port    the port that is taken
     * @param holder  the running infra service that has the port, or {@code null} when it is another program
     * @param pid     the pid of the holder
     */
    static String message(String service, int port, String holder, String pid) {
        if (holder != null) {
            return "Cannot start " + service + ": port " + port + " is in use by " + holder + " (PID " + pid
                   + "), stop it first with: camel infra stop " + holder;
        }
        return "Cannot start " + service + ": port " + port + " is in use by another program";
    }
}
