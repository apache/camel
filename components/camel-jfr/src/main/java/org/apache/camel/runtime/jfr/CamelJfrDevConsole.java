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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "jfr", displayName = "JFR Runtime Instrumentation",
            description = "Status and live control of camel-jfr runtime instrumentation")
public class CamelJfrDevConsole extends AbstractDevConsole {

    static final Map<String, Class<?>> EVENT_BY_SHORT_NAME = new LinkedHashMap<>();
    static {
        EVENT_BY_SHORT_NAME.put("route", CamelRouteEvent.class);
        EVENT_BY_SHORT_NAME.put("processor", CamelProcessorEvent.class);
        EVENT_BY_SHORT_NAME.put("exchange", CamelExchangeEvent.class);
        EVENT_BY_SHORT_NAME.put("send", CamelExchangeSendEvent.class);
        EVENT_BY_SHORT_NAME.put("failed", CamelExchangeFailedEvent.class);
        EVENT_BY_SHORT_NAME.put("redelivery", CamelRedeliveryEvent.class);
    }

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

    private String eventShortName(Class<?> eventClass) {
        for (Map.Entry<String, Class<?>> entry : EVENT_BY_SHORT_NAME.entrySet()) {
            if (entry.getValue().equals(eventClass)) {
                return entry.getKey();
            }
        }
        return eventClass.getSimpleName();
    }

    private Map<String, Boolean> eventEnabledStates() {
        Map<String, Boolean> states = new LinkedHashMap<>();
        for (Class<?> eventClass : CamelJfrRuntimeInstrumentation.RUNTIME_EVENTS) {
            EventType type = EventType.getEventType(eventClass.asSubclass(Event.class));
            states.put(eventShortName(eventClass), type != null && type.isEnabled());
        }
        return states;
    }

    private String doStatus() {
        boolean registered = isInstrumentationRegistered();
        List<Recording> recordings = FlightRecorder.getFlightRecorder().getRecordings();
        Map<String, Boolean> eventStates = eventEnabledStates();

        StringBuilder sb = new StringBuilder();
        sb.append("registered: ").append(registered).append('\n');
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
        eventStates.forEach((name, enabled) -> sb.append("event ").append(name).append(": ")
                .append(enabled ? "enabled" : "disabled").append('\n'));
        return sb.toString();
    }

    private String doToggle(Map<String, Object> options, boolean enable) {
        String event = optionString(options, "event");
        if (event == null) {
            event = "all";
        }

        List<Recording> recordings = FlightRecorder.getFlightRecorder().getRecordings();
        if (recordings.isEmpty()) {
            return "no active recording: nothing to toggle live. Start one via --jfr, "
                   + "'jcmd <pid> JFR.start', or JMX first.";
        }

        List<Class<?>> targets;
        if ("all".equals(event)) {
            targets = List.of(CamelJfrRuntimeInstrumentation.RUNTIME_EVENTS);
        } else {
            Class<?> target = EVENT_BY_SHORT_NAME.get(event);
            if (target == null) {
                return "unknown event: " + event + ". Valid values: "
                       + String.join(", ", EVENT_BY_SHORT_NAME.keySet()) + ", all";
            }
            targets = List.of(target);
        }

        for (Recording recording : recordings) {
            for (Class<?> target : targets) {
                Class<? extends Event> eventClass = target.asSubclass(Event.class);
                if (enable) {
                    recording.enable(eventClass);
                } else {
                    recording.disable(eventClass);
                }
            }
        }
        return (enable ? "enabled " : "disabled ") + event + " on " + recordings.size() + " recording(s)";
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        String command = optionString(options, "command");
        if (command == null) {
            command = "status";
        }
        return switch (command) {
            case "status" -> doStatus();
            case "enable" -> doToggle(options, true);
            case "disable" -> doToggle(options, false);
            default -> "unknown command: " + command + ". Valid values: status, enable, disable";
        };
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        String command = optionString(options, "command");
        if (command == null) {
            command = "status";
        }
        JsonObject root = new JsonObject();
        if ("status".equals(command)) {
            root.put("registered", isInstrumentationRegistered());
            JsonArray recordingsJson = new JsonArray();
            for (Recording recording : FlightRecorder.getFlightRecorder().getRecordings()) {
                JsonObject rec = new JsonObject();
                rec.put("name", recording.getName());
                rec.put("state", recording.getState().toString());
                rec.put("destination", recording.getDestination() != null ? recording.getDestination().toString() : null);
                recordingsJson.add(rec);
            }
            root.put("recordings", recordingsJson);
            JsonObject events = new JsonObject();
            eventEnabledStates().forEach(events::put);
            root.put("events", events);
        } else if ("enable".equals(command) || "disable".equals(command)) {
            root.put("result", doToggle(options, "enable".equals(command)));
        } else {
            root.put("error", "unknown command: " + command);
        }
        return root;
    }
}
