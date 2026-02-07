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
package org.apache.camel.impl.console;

import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.MessageHistory;
import org.apache.camel.NamedRoute;
import org.apache.camel.Route;
import org.apache.camel.spi.BacklogDebugger;
import org.apache.camel.spi.BacklogTracerEventMessage;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

@DevConsole(name = "debug", description = "Camel route debugger")
public class DebugDevConsole extends AbstractDevConsole {

    public static final String COMMAND = "command";
    public static final String BREAKPOINT = "breakpoint";
    public static final String POSITION = "position";
    public static final String CODE_LIMIT = "codeLimit";
    public static final String HISTORY = "history";

    public DebugDevConsole() {
        super("camel", "debug", "Debug", "Camel route debugger");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        String command = (String) options.get(COMMAND);
        String breakpoint = (String) options.get(BREAKPOINT);
        String position = (String) options.get(POSITION);
        int num = position == null || position.isBlank() ? 0 : Integer.parseInt(position);

        if (ObjectHelper.isNotEmpty(command)) {
            doCommand(command, breakpoint, num);
            return "";
        }

        StringBuilder sb = new StringBuilder();

        BacklogDebugger backlog = getCamelContext().hasService(BacklogDebugger.class);
        if (backlog != null) {
            sb.append("Settings:");
            sb.append(String.format("%n    Enabled: %s", backlog.isEnabled()));
            sb.append(String.format("%n    Standby: %s", backlog.isStandby()));
            sb.append(String.format("%n    Suspended Mode: %s", backlog.isSuspendMode()));
            sb.append(String.format("%n    Fallback Timeout: %ss", backlog.getFallbackTimeout())); // is in seconds
            sb.append(String.format("%n    Logging Level: %s", backlog.getLoggingLevel()));
            sb.append(String.format("%n    Include Exchange Properties: %s", backlog.isIncludeExchangeProperties()));
            sb.append(String.format("%n    Include Files: %s", backlog.isBodyIncludeFiles()));
            sb.append(String.format("%n    Include Streams: %s", backlog.isBodyIncludeStreams()));
            sb.append(String.format("%n    Max Chars: %s", backlog.getBodyMaxChars()));

            sb.append("\n\nBreakpoints:");
            sb.append(String.format("%n    Debug Counter: %s", backlog.getDebugCounter()));
            sb.append(String.format("%n    Single Step Mode: %s", backlog.isSingleStepMode()));
            for (String n : backlog.getBreakpoints()) {
                boolean suspended = backlog.getSuspendedBreakpointNodeIds().contains(n);
                if (suspended) {
                    sb.append(String.format("%n    Breakpoint: %s (suspended)", n));
                } else {
                    sb.append(String.format("%n    Breakpoint: %s", n));
                }
            }
            sb.append("\n\nSuspended:");
            for (String n : backlog.getSuspendedBreakpointNodeIds()) {
                sb.append(String.format("%n    Node: %s (suspended)", n));
                BacklogTracerEventMessage trace = backlog.getSuspendedBreakpointMessage(n);
                if (trace != null) {
                    sb.append("\n");
                    sb.append(trace.toXml(4));
                    sb.append("\n");
                }
            }
        }

        return sb.toString();
    }

    private void doCommand(String command, String breakpoint, int position) {
        BacklogDebugger backlog = getCamelContext().hasService(BacklogDebugger.class);
        if (backlog == null) {
            return;
        }

        String cmd = command.toLowerCase();
        switch (cmd) {
            case "enable" -> backlog.enableDebugger();
            case "disable" -> backlog.disableDebugger();
            case "attach" -> backlog.attach();
            case "detach" -> backlog.detach();
            case "resume" -> backlog.resumeAll();
            case "step" -> executeStepCommand(backlog, breakpoint, position);
            case "stepover" -> backlog.stepOver();
            case "skipover" -> backlog.skipOver();
            case "add" -> executeAddCommand(backlog, breakpoint);
            case "remove" -> executeRemoveCommand(backlog, breakpoint);
            default -> {
            }
        }
    }

    private void executeStepCommand(BacklogDebugger backlog, String breakpoint, int position) {
        if (ObjectHelper.isNotEmpty(breakpoint)) {
            backlog.stepBreakpoint(breakpoint);
            return;
        }
        if (position < 1 || !stepToPosition(backlog, position)) {
            backlog.stepBreakpoint();
        }
    }

    private void executeAddCommand(BacklogDebugger backlog, String breakpoint) {
        if (ObjectHelper.isNotEmpty(breakpoint)) {
            backlog.addBreakpoint(breakpoint);
        }
    }

    private void executeRemoveCommand(BacklogDebugger backlog, String breakpoint) {
        if (ObjectHelper.isNotEmpty(breakpoint)) {
            backlog.removeBreakpoint(breakpoint);
        } else {
            backlog.removeAllBreakpoints();
        }
    }

    private boolean stepToPosition(BacklogDebugger backlog, int position) {
        if (!canStepToPosition(backlog, position)) {
            return false;
        }

        String id = backlog.getSuspendedBreakpointNodeIds().iterator().next();
        int diff = calculateStepDifference(backlog, id, position);
        if (diff <= 0) {
            return false;
        }

        return executeSteps(backlog, diff);
    }

    private boolean canStepToPosition(BacklogDebugger backlog, int position) {
        return position >= 1
                && backlog.isSingleStepMode()
                && backlog.getSuspendedBreakpointNodeIds().size() == 1;
    }

    private int calculateStepDifference(BacklogDebugger backlog, String id, int position) {
        Exchange exchange = backlog.getSuspendedExchange(id);
        if (exchange == null) {
            return -1;
        }
        List<MessageHistory> list = exchange.getProperty(ExchangePropertyKey.MESSAGE_HISTORY, List.class);
        if (list == null) {
            return -1;
        }
        return position - list.size() + 1;
    }

    private boolean executeSteps(BacklogDebugger backlog, int diff) {
        StopWatch watch = new StopWatch();
        for (int i = 0; i < diff; i++) {
            if (backlog.getSuspendedBreakpointNodeIds().isEmpty()) {
                return true;
            }

            String id = backlog.getSuspendedBreakpointNodeIds().iterator().next();
            var msg = backlog.getSuspendedBreakpointMessage(id);
            if (msg.isLast()) {
                return true;
            }

            if (!stepAndWait(backlog, watch)) {
                return false;
            }
        }
        return true;
    }

    private boolean stepAndWait(BacklogDebugger backlog, StopWatch watch) {
        watch.restart();
        backlog.stepBreakpoint();
        while (backlog.isSingleStepMode() && backlog.getSuspendedBreakpointNodeIds().isEmpty()) {
            if (watch.taken() > 10000) {
                return false;
            }
            try {
                Thread.sleep(10);
            } catch (Exception e) {
                // ignore
            }
        }
        return true;
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        String command = (String) options.get(COMMAND);
        String breakpoint = (String) options.get(BREAKPOINT);
        String codeLimit = (String) options.getOrDefault(CODE_LIMIT, "5");
        boolean history = "true".equals(options.getOrDefault(HISTORY, "true"));
        String repeat = (String) options.get(POSITION);
        int num = repeat == null || repeat.isBlank() ? 0 : Integer.parseInt(repeat);

        if (ObjectHelper.isNotEmpty(command)) {
            doCommand(command, breakpoint, num);
            return root;
        }

        BacklogDebugger backlog = getCamelContext().hasService(BacklogDebugger.class);
        if (backlog != null) {
            root.put("version", getCamelContext().getVersion());
            root.put("enabled", backlog.isEnabled());
            root.put("standby", backlog.isStandby());
            root.put("suspendedMode", backlog.isSuspendMode());
            root.put("fallbackTimeout", backlog.getFallbackTimeout());
            root.put("loggingLevel", backlog.getLoggingLevel());
            root.put("includeExchangeProperties", backlog.isIncludeExchangeProperties());
            root.put("includeFiles", backlog.isBodyIncludeFiles());
            root.put("includeStreams", backlog.isBodyIncludeStreams());
            root.put("maxChars", backlog.getBodyMaxChars());
            root.put("debugCounter", backlog.getDebugCounter());
            root.put("singleStepMode", backlog.isSingleStepMode());

            JsonArray arr = new JsonArray();
            for (String n : backlog.getBreakpoints()) {
                JsonObject jo = new JsonObject();
                jo.put("nodeId", n);
                boolean suspended = backlog.getSuspendedBreakpointNodeIds().contains(n);
                jo.put("suspended", suspended);
                arr.add(jo);
            }
            if (!arr.isEmpty()) {
                root.put("breakpoints", arr);
            }

            arr = new JsonArray();
            for (String n : backlog.getSuspendedBreakpointNodeIds()) {
                BacklogTracerEventMessage t = backlog.getSuspendedBreakpointMessage(n);
                if (t != null) {
                    JsonObject to = (JsonObject) t.asJSon();
                    arr.add(to);

                    // enrich with source code +/- lines around location
                    int limit = Integer.parseInt(codeLimit);
                    if (limit > 0) {
                        String rid = to.getString("routeId");
                        String loc = to.getString("location");
                        if (rid != null) {
                            List<JsonObject> code = enrichSourceCode(rid, loc, limit);
                            if (code != null && !code.isEmpty()) {
                                to.put("code", code);
                            }
                        }
                    }
                    // enrich with message history
                    if (history) {
                        List<JsonObject> steps = enrichHistory(backlog, n);
                        if (!steps.isEmpty()) {
                            to.put("history", steps);
                        }
                    }
                }
            }
            if (!arr.isEmpty()) {
                root.put("suspended", arr);
            }
        }

        return root;
    }

    private List<JsonObject> enrichHistory(BacklogDebugger backlog, String id) {
        List<JsonObject> arr = new ArrayList<>();

        Exchange exchange = backlog.getSuspendedExchange(id);
        if (exchange == null) {
            return arr;
        }
        List<MessageHistory> list = exchange.getProperty(ExchangePropertyKey.MESSAGE_HISTORY, List.class);
        if (list == null) {
            return arr;
        }

        int counter = 0;
        for (MessageHistory h : list) {
            JsonObject jo = new JsonObject();

            if (h.getNode() != null) {
                NamedRoute nr = CamelContextHelper.getRoute(h.getNode());
                if (nr != null) {
                    // skip debugging inside rest-dsl (just a tiny facade) or kamelets / route-templates
                    boolean skip = nr.isCreatedFromRest() || nr.isCreatedFromTemplate();
                    if (skip) {
                        continue;
                    }
                }
            }
            jo.put("index", counter++);
            if (h.getRouteId() != null) {
                jo.put("routeId", h.getRouteId());
            }
            jo.put("elapsed", h.getElapsed());
            jo.put("acceptDebugger", h.isAcceptDebugger());
            jo.put("skipOver", h.isDebugSkipOver());
            if (h.getNode() != null) {
                jo.put("nodeId", h.getNode().getId());
                jo.put("nodeShortName", h.getNode().getShortName());
                jo.put("nodeLabel", h.getNode().getLabel());
                jo.put("level", h.getNode().getLevel());
                if (h.getNode().getLocation() != null) {
                    String loc = h.getNode().getLocation();
                    // strip schema
                    if (loc.contains(":")) {
                        loc = StringHelper.after(loc, ":");
                    }
                    jo.put("location", loc);
                }
                if (h.getNode().getLineNumber() != -1) {
                    jo.put("line", h.getNode().getLineNumber());
                }
                String t = ConsoleHelper.loadSourceLine(getCamelContext(), h.getNode().getLocation(),
                        h.getNode().getLineNumber());
                if (t != null) {
                    jo.put("code", Jsoner.escape(t));
                }
            }
            arr.add(jo);
        }

        return arr;
    }

    private List<JsonObject> enrichSourceCode(String routeId, String location, int lines) {
        Route route = getCamelContext().getRoute(routeId);
        if (route == null) {
            return null;
        }
        Resource resource = route.getSourceResource();
        if (resource == null) {
            return null;
        }

        List<JsonObject> code = new ArrayList<>();

        location = StringHelper.afterLast(location, ":");
        int line = 0;
        try {
            if (location != null) {
                line = Integer.parseInt(location);
            }
            LineNumberReader reader = new LineNumberReader(resource.getReader());
            for (int i = 1; i <= line + lines; i++) {
                String t = reader.readLine();
                if (t != null) {
                    int low = line - lines + 2; // grab more of the following code than previous code (+2)
                    int high = line + lines + 1 + 2;
                    if (i >= low && i <= high) {
                        JsonObject c = new JsonObject();
                        c.put("line", i);
                        if (line == i) {
                            c.put("match", true);
                        }
                        c.put("code", Jsoner.escape(t));
                        code.add(c);
                    }
                }
            }
            IOHelper.close(reader);
        } catch (Exception e) {
            // ignore
        }

        return code;
    }
}
