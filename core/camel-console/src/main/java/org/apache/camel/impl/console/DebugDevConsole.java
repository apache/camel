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
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

@DevConsole("debug")
public class DebugDevConsole extends AbstractDevConsole {

    public static final String COMMAND = "command";
    public static final String BREAKPOINT = "breakpoint";
    public static final String CODE_LIMIT = "codeLimit";
    public static final String HISTORY = "history";

    public DebugDevConsole() {
        super("camel", "debug", "Debug", "Camel route debugger");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        String command = (String) options.get(COMMAND);
        String breakpoint = (String) options.get(BREAKPOINT);

        if (ObjectHelper.isNotEmpty(command)) {
            doCommand(command, breakpoint);
            return "";
        }

        StringBuilder sb = new StringBuilder();

        BacklogDebugger backlog = getCamelContext().hasService(BacklogDebugger.class);
        if (backlog != null) {
            sb.append("Settings:");
            sb.append(String.format("\n    Enabled: %s", backlog.isEnabled()));
            sb.append(String.format("\n    Standby: %s", backlog.isStandby()));
            sb.append(String.format("\n    Suspended Mode: %s", backlog.isSuspendMode()));
            sb.append(String.format("\n    Fallback Timeout: %ss", backlog.getFallbackTimeout())); // is in seconds
            sb.append(String.format("\n    Logging Level: %s", backlog.getLoggingLevel()));
            sb.append(String.format("\n    Include Exchange Properties: %s", backlog.isIncludeExchangeProperties()));
            sb.append(String.format("\n    Include Files: %s", backlog.isBodyIncludeFiles()));
            sb.append(String.format("\n    Include Streams: %s", backlog.isBodyIncludeStreams()));
            sb.append(String.format("\n    Max Chars: %s", backlog.getBodyMaxChars()));

            sb.append("\n\nBreakpoints:");
            sb.append(String.format("\n    Debug Counter: %s", backlog.getDebugCounter()));
            sb.append(String.format("\n    Single Step Mode: %s", backlog.isSingleStepMode()));
            for (String n : backlog.getBreakpoints()) {
                boolean suspended = backlog.getSuspendedBreakpointNodeIds().contains(n);
                if (suspended) {
                    sb.append(String.format("\n    Breakpoint: %s (suspended)", n));
                } else {
                    sb.append(String.format("\n    Breakpoint: %s", n));
                }
            }
            sb.append("\n\nSuspended:");
            for (String n : backlog.getSuspendedBreakpointNodeIds()) {
                sb.append(String.format("\n    Node: %s (suspended)", n));
                BacklogTracerEventMessage trace = backlog.getSuspendedBreakpointMessage(n);
                if (trace != null) {
                    sb.append("\n");
                    sb.append(trace.toXml(8));
                    sb.append("\n");
                }
            }
        }

        return sb.toString();
    }

    private void doCommand(String command, String breakpoint) {
        BacklogDebugger backlog = getCamelContext().hasService(BacklogDebugger.class);
        if (backlog == null) {
            return;
        }

        if ("enable".equalsIgnoreCase(command)) {
            backlog.enableDebugger();
        } else if ("disable".equalsIgnoreCase(command)) {
            backlog.disableDebugger();
        } else if ("attach".equalsIgnoreCase(command)) {
            backlog.attach();
        } else if ("detach".equalsIgnoreCase(command)) {
            backlog.detach();
        } else if ("resume".equalsIgnoreCase(command)) {
            backlog.resumeAll();
        } else if ("step".equalsIgnoreCase(command)) {
            if (ObjectHelper.isNotEmpty(breakpoint)) {
                backlog.stepBreakpoint(breakpoint);
            } else {
                backlog.stepBreakpoint();
            }
        } else if ("add".equalsIgnoreCase(command) && ObjectHelper.isNotEmpty(breakpoint)) {
            backlog.addBreakpoint(breakpoint);
        } else if ("remove".equalsIgnoreCase(command)) {
            if (ObjectHelper.isNotEmpty(breakpoint)) {
                backlog.removeBreakpoint(breakpoint);
            } else {
                backlog.removeAllBreakpoints();
            }
        }
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        String command = (String) options.get(COMMAND);
        String breakpoint = (String) options.get(BREAKPOINT);
        String codeLimit = (String) options.getOrDefault(CODE_LIMIT, "5");
        boolean history = "true".equals(options.getOrDefault(HISTORY, "true"));

        if (ObjectHelper.isNotEmpty(command)) {
            doCommand(command, breakpoint);
            return root;
        }

        BacklogDebugger backlog = getCamelContext().hasService(BacklogDebugger.class);
        if (backlog != null) {
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
            if (h.getRouteId() != null) {
                jo.put("routeId", h.getRouteId());
            }
            jo.put("elapsed", h.getElapsed());
            if (h.getNode() != null) {
                jo.put("nodeId", h.getNode().getId());
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
