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
package org.apache.camel.runtime.jfr;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * Reports whether the camel-jfr runtime instrumentation is installed, and allows its events to be turned on and off
 * while the application is running.
 *
 * @since 4.22
 */
@DevConsole(name = "jfr", displayName = "JFR Runtime Instrumentation",
            description = "Status and live control of camel-jfr runtime instrumentation")
public class CamelJfrDevConsole extends AbstractDevConsole {

    @Metadata(label = "query", description = "Command to perform", javaType = "java.lang.String",
              defaultValue = "status", enums = "status,enable,disable,jfc")
    public static final String COMMAND = "command";

    @Metadata(label = "query", description = "The runtime event to enable or disable, or all for every event",
              javaType = "java.lang.String", defaultValue = "all",
              enums = "all,route,processor,exchange,send,failed,redelivery")
    public static final String EVENT = "event";

    @Metadata(label = "query",
              description = "Comma separated list of events to disable in the generated jfc settings overlay",
              javaType = "java.lang.String")
    public static final String DISABLE = "disable";

    private static final String ALL = "all";

    public CamelJfrDevConsole() {
        super("camel", "jfr", "JFR Runtime Instrumentation",
              "Status and live control of camel-jfr runtime instrumentation");
    }

    private boolean isInstrumentationRegistered() {
        CamelContext ctx = getCamelContext();
        if (ctx == null) {
            return false;
        }
        for (LifecycleStrategy strategy : ctx.getLifecycleStrategies()) {
            if (strategy instanceof CamelJfrRuntimeInstrumentation instrumentation) {
                return instrumentation.isRegistered();
            }
        }
        return false;
    }

    /**
     * Only a running recording can have its events toggled, so a stopped or closed one is not reported as changed.
     */
    private static List<Recording> runningRecordings() {
        List<Recording> answer = new ArrayList<>();
        for (Recording recording : FlightRecorder.getFlightRecorder().getRecordings()) {
            if (recording.getState() == RecordingState.RUNNING) {
                answer.add(recording);
            }
        }
        return answer;
    }

    private String doStatus() {
        List<Recording> recordings = FlightRecorder.getFlightRecorder().getRecordings();

        StringBuilder sb = new StringBuilder();
        sb.append("registered: ").append(isInstrumentationRegistered()).append('\n');
        if (recordings.isEmpty()) {
            sb.append("recordings: none active\n");
        } else {
            for (Recording recording : recordings) {
                sb.append("recording: ").append(recording.getName())
                        .append(" (state=").append(recording.getState())
                        .append(", destination=").append(recording.getDestination())
                        .append(", duration=").append(recording.getDuration())
                        .append(")\n");
            }
        }
        for (CamelJfrEvents event : CamelJfrEvents.values()) {
            sb.append("event ").append(event.getShortName()).append(": ")
                    .append(event.isEnabled() ? "enabled" : "disabled").append('\n');
        }
        return sb.toString();
    }

    /**
     * Resolves the {@code event} option to the events it addresses.
     *
     * @throws IllegalArgumentException if the option names an unknown event
     */
    private static Set<CamelJfrEvents> resolveEvents(String event) {
        if (event == null || ALL.equals(event)) {
            return EnumSet.allOf(CamelJfrEvents.class);
        }
        CamelJfrEvents answer = CamelJfrEvents.byShortName(event);
        if (answer == null) {
            throw new IllegalArgumentException(
                    "unknown event: " + event + ". Valid values: " + CamelJfrEvents.shortNames() + ", " + ALL);
        }
        return EnumSet.of(answer);
    }

    /**
     * The outcome of a toggle, so callers can tell "nothing was changed" from "the events were changed" without having
     * to parse the message.
     */
    private record ToggleResult(boolean success, String message) {
    }

    private ToggleResult doToggle(Map<String, Object> options, boolean enable) {
        String event = optionString(options, EVENT);
        Set<CamelJfrEvents> targets;
        try {
            targets = resolveEvents(event);
        } catch (IllegalArgumentException e) {
            return new ToggleResult(false, e.getMessage());
        }

        List<Recording> recordings = runningRecordings();
        if (recordings.isEmpty()) {
            return new ToggleResult(
                    false,
                    "no running recording: nothing to toggle live. Start one via --jfr, "
                           + "'jcmd <pid> JFR.start', or JMX first.");
        }

        for (Recording recording : recordings) {
            for (CamelJfrEvents target : targets) {
                if (enable) {
                    recording.enable(target.getEventClass());
                } else {
                    recording.disable(target.getEventClass());
                }
            }
        }
        return new ToggleResult(
                true,
                (enable ? "enabled " : "disabled ") + (event != null ? event : ALL)
                      + " on " + recordings.size() + " recording(s)");
    }

    /**
     * Generates a {@code .jfc} settings overlay, so the events can be turned on for a recording started outside of
     * Camel.
     *
     * @throws IllegalArgumentException if the disable option names an unknown event
     */
    private String doJfc(Map<String, Object> options) {
        String disable = optionString(options, DISABLE);
        Set<CamelJfrEvents> disabled = EnumSet.noneOf(CamelJfrEvents.class);
        if (disable != null) {
            for (String name : disable.split(",")) {
                disabled.addAll(resolveEvents(name.trim()));
            }
        }

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<configuration version=\"2.0\">\n");
        for (CamelJfrEvents event : CamelJfrEvents.values()) {
            xml.append("<event name=\"").append(event.getEventName()).append("\">\n")
                    .append("    <setting name=\"enabled\">").append(!disabled.contains(event)).append("</setting>\n")
                    .append("</event>\n");
        }
        xml.append("</configuration>\n");

        return xml + "\nSave the above to a file, e.g. camel-runtime-events.jfc, then run:\n"
               + "jcmd " + ProcessHandle.current().pid() + " JFR.start settings=default,camel-runtime-events.jfc"
               + "\n(replace 'default' with whatever base profile your recording already uses)";
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        String command = optionString(options, COMMAND);
        return switch (command != null ? command : "status") {
            case "status" -> doStatus();
            case "enable" -> doToggle(options, true).message();
            case "disable" -> doToggle(options, false).message();
            case "jfc" -> {
                try {
                    yield doJfc(options);
                } catch (IllegalArgumentException e) {
                    yield e.getMessage();
                }
            }
            default -> unknownCommand(command);
        };
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        String command = optionString(options, COMMAND);
        JsonObject root = new JsonObject();
        switch (command != null ? command : "status") {
            case "status" -> {
                root.put("registered", isInstrumentationRegistered());
                JsonArray recordingsJson = new JsonArray();
                for (Recording recording : FlightRecorder.getFlightRecorder().getRecordings()) {
                    JsonObject rec = new JsonObject();
                    rec.put("name", recording.getName());
                    rec.put("state", recording.getState().toString());
                    rec.put("destination",
                            recording.getDestination() != null ? recording.getDestination().toString() : null);
                    recordingsJson.add(rec);
                }
                root.put("recordings", recordingsJson);
                JsonObject events = new JsonObject();
                for (CamelJfrEvents event : CamelJfrEvents.values()) {
                    events.put(event.getShortName(), event.isEnabled());
                }
                root.put("events", events);
            }
            case "enable", "disable" -> {
                ToggleResult result = doToggle(options, "enable".equals(command));
                root.put("success", result.success());
                root.put("result", result.message());
            }
            case "jfc" -> {
                try {
                    root.put("jfc", doJfc(options));
                } catch (IllegalArgumentException e) {
                    root.put("error", e.getMessage());
                }
            }
            default -> root.put("error", unknownCommand(command));
        }
        return root;
    }

    private static String unknownCommand(String command) {
        return "unknown command: " + command + ". Valid values: status, enable, disable, jfc";
    }
}
