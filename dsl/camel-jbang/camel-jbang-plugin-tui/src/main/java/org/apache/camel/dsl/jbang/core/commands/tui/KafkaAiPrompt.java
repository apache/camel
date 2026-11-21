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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.Locale;
import java.util.Set;

/**
 * The question the AI panel is opened with to fix a Kafka consumer (Shift+F8 on the Kafka tab, CAMEL-25362): its state
 * and last error, its endpoint and group, where it last read, where its route is in the source, and the last error of
 * the route. It is put in the input of the panel, for the user to send with Enter or change first.
 */
final class KafkaAiPrompt {

    /** The states of a consumer that is doing its job, or was paused on purpose. */
    private static final Set<String> FINE = Set.of("running", "paused", "resume_requested");

    private KafkaAiPrompt() {
    }

    /** Whether a consumer has something to fix: an error, or a state other than running or paused. */
    static boolean hasTrouble(KafkaConsumerInfo ki) {
        return ki.lastError != null && !ki.lastError.isBlank()
                || ki.state != null && !ki.state.isBlank() && !FINE.contains(ki.state.toLowerCase(Locale.ROOT));
    }

    static String of(IntegrationInfo info, KafkaConsumerInfo ki) {
        StringBuilder sb = new StringBuilder();
        String name = info.name != null ? info.name : info.pid;
        sb.append("Fix the Kafka consumer of route ").append(ki.routeId != null ? ki.routeId : "")
                .append(" in the running integration ").append(name).append(": it is ")
                .append(ki.state != null && !ki.state.isBlank() ? ki.state.toLowerCase(Locale.ROOT) : "in an unknown state")
                .append(".\n");
        if (ki.lastError != null && !ki.lastError.isBlank()) {
            sb.append("Its last error: ").append(ki.lastError.strip()).append('\n');
        }
        if (ki.uri != null && !ki.uri.isBlank()) {
            sb.append("Its endpoint: ").append(ki.uri).append('\n');
        }
        if (ki.groupId != null && !ki.groupId.isBlank()) {
            sb.append("Its consumer group: ").append(ki.groupId);
            if (ki.groupInstanceId != null && !ki.groupInstanceId.isBlank()) {
                sb.append(" (group instance ").append(ki.groupInstanceId).append(')');
            }
            if (ki.memberId != null && !ki.memberId.isBlank()) {
                sb.append(", member ").append(ki.memberId).append(", generation ").append(ki.generationId);
            }
            sb.append(".\n");
        }
        if (ki.lastTopic != null && !ki.lastTopic.isBlank()) {
            sb.append("It last read topic ").append(ki.lastTopic).append(", partition ").append(ki.lastPartition)
                    .append(", offset ").append(ki.lastOffset).append(".\n");
        } else {
            sb.append("It has not read a record yet.\n");
        }
        for (RouteInfo r : info.routes) {
            if (ki.routeId != null && ki.routeId.equals(r.routeId) && r.source != null) {
                sb.append("Its route is at ").append(r.source).append(".\n");
            }
        }
        ErrorInfo last = RouteAiPrompt.lastError(info, ki.routeId);
        if (last != null) {
            sb.append("The last error of the route: ").append(RuntimeFailures.failureOf(last)).append('\n');
        }
        sb.append("Find the cause first, from the integration's errors and log; tui_get_table on the Kafka tab has the")
                .append(" consumers. When it is in the project's routes or configuration, such as the brokers, the")
                .append(" topic, the group, the deserializers or the security settings, fix it with camel_edit_file and")
                .append(" check the file with camel_validate_source; dev mode reloads the file when it is saved. When it")
                .append(" is outside the integration, such as a broker that is not running or cannot be reached, or a")
                .append(" topic that does not exist, say how to fix that, and make no changes to the files.");
        return sb.toString();
    }
}
