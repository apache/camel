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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import picocli.CommandLine;

@CommandLine.Command(name = "send",
                     description = "Send messages to endpoints via running Camel", sortOptions = false)
public class CamelSendAction extends ActionBaseCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--endpoint" },
                        description = "Endpoint where to send the message (can be uri, pattern, or refer to a route id)")
    String endpoint;

    @CommandLine.Option(names = { "--poll" },
                        description = "Poll instead of sending a message. This can be used to receive latest message from a Kafka topic or JMS queue.")
    boolean poll;

    @CommandLine.Option(names = { "--reply" },
                        description = "Whether to expect a reply message (InOut vs InOut messaging style)")
    boolean reply;

    @CommandLine.Option(names = { "--reply-file" },
                        description = "Saves reply message to the file with the given name (override if exists)")
    String replyFile;

    @CommandLine.Option(names = { "--body" },
                        description = "Message body to send (prefix with file: to refer to loading message body from file)")
    String body;

    @CommandLine.Option(names = { "--header" },
                        description = "Message header (key=value)")
    List<String> headers;

    @CommandLine.Option(names = { "--timeout" }, defaultValue = "20000",
                        description = "Timeout in millis waiting for message to be sent (and reply message if InOut messaging)")
    long timeout = 20000;

    @CommandLine.Option(names = { "--show-exchange-properties" }, defaultValue = "false",
                        description = "Show exchange properties in traced messages")
    boolean showExchangeProperties;

    @CommandLine.Option(names = { "--show-headers" }, defaultValue = "true",
                        description = "Show message headers in traced messages")
    boolean showHeaders = true;

    @CommandLine.Option(names = { "--show-body" }, defaultValue = "true",
                        description = "Show message body in traced messages")
    boolean showBody = true;

    @CommandLine.Option(names = { "--show-exception" }, defaultValue = "true",
                        description = "Show exception and stacktrace for failed messages")
    boolean showException = true;

    @CommandLine.Option(names = { "--logging-color" }, defaultValue = "true", description = "Use colored logging")
    boolean loggingColor = true;

    @CommandLine.Option(names = { "--pretty" },
                        description = "Pretty print message body when using JSon or XML format")
    boolean pretty;

    private volatile long pid;

    private MessageTableHelper tableHelper;

    public CamelSendAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        List<Long> pids = findPids(name);
        if (pids.isEmpty()) {
            return 0;
        } else if (pids.size() > 1) {
            printer().println("Name or pid " + name + " matches " + pids.size()
                              + " running Camel integrations. Specify a name or PID that matches exactly one.");
            return 0;
        }

        this.pid = pids.get(0);

        // ensure output file is deleted before executing action
        File outputFile = getOutputFile(Long.toString(pid));
        FileUtil.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "send");
        root.put("endpoint", endpoint);
        root.put("poll", poll);
        // timeout cannot be too low
        if (timeout < 5000) {
            timeout = 5000;
        }
        root.put("pollTimeout", Math.min(1000, timeout - 1000)); // poll timeout should be shorter than jbang timeout
        String mep = (reply || replyFile != null) ? "InOut" : "InOnly";
        root.put("exchangePattern", mep);
        if (body != null) {
            root.put("body", body);
        }
        if (headers != null) {
            JsonArray arr = new JsonArray();
            for (String h : headers) {
                JsonObject jo = new JsonObject();
                if (!h.contains("=")) {
                    printer().println("Header must be in key=value format, was: " + h);
                    return 0;
                }
                jo.put("key", StringHelper.before(h, "="));
                jo.put("value", StringHelper.after(h, "="));
                arr.add(jo);
            }
            root.put("headers", arr);
        }
        File f = getActionFile(Long.toString(pid));
        try {
            IOHelper.writeText(root.toJson(), f);
        } catch (Exception e) {
            // ignore
        }

        JsonObject jo = waitForOutputFile(outputFile);
        if (jo != null) {
            printStatusLine(jo);
            String exchangeId = jo.getString("exchangeId");
            JsonObject message = jo.getMap("message");
            JsonObject cause = jo.getMap("exception");
            if (message != null || cause != null) {
                if (replyFile != null) {
                    File target = new File(replyFile);
                    String json = jo.toJson();
                    if (pretty) {
                        json = Jsoner.prettyPrint(json, 2);
                    }
                    IOHelper.writeText(json, target);
                }
                if (!showExchangeProperties && message != null) {
                    message.remove("exchangeProperties");
                }
                if (!showHeaders && message != null) {
                    message.remove("headers");
                }
                if (!showBody && message != null) {
                    message.remove("body");
                }
                if (!showException && cause != null) {
                    cause = null;
                }
                if (replyFile == null) {
                    tableHelper = new MessageTableHelper();
                    tableHelper.setPretty(pretty);
                    tableHelper.setLoggingColor(loggingColor);
                    tableHelper.setShowExchangeProperties(showExchangeProperties);
                    String table = tableHelper.getDataAsTable(exchangeId, mep, jo, null, message, cause);
                    printer().println(table);
                }
            }
        }

        // delete output file after use
        FileUtil.deleteFile(outputFile);

        return 0;
    }

    private void printStatusLine(JsonObject jo) {
        // timstamp
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String ts = sdf.format(new Date(jo.getLong("timestamp")));
        if (loggingColor) {
            AnsiConsole.out().print(Ansi.ansi().fgBrightDefault().a(Ansi.Attribute.INTENSITY_FAINT).a(ts).reset());
        } else {
            printer().print(ts);
        }
        // pid
        printer().print("  ");
        String p = String.format("%5.5s", this.pid);
        if (loggingColor) {
            AnsiConsole.out().print(Ansi.ansi().fgMagenta().a(p).reset());
            AnsiConsole.out().print(Ansi.ansi().fgBrightDefault().a(Ansi.Attribute.INTENSITY_FAINT).a(" --- ").reset());
        } else {
            printer().print(p);
            printer().print(" --- ");
        }
        // endpoint
        String ids = jo.getString("endpoint");
        if (ids.length() > 40) {
            ids = ids.substring(0, 40);
        }
        ids = String.format("%40.40s", ids);
        if (loggingColor) {
            AnsiConsole.out().print(Ansi.ansi().fgCyan().a(ids).reset());
        } else {
            printer().print(ids);
        }
        printer().print(" : ");
        // status
        printer().print(getStatus(jo));
        // elapsed
        String e = TimeUtils.printDuration(jo.getLong("elapsed"), true);
        if (loggingColor) {
            AnsiConsole.out().print(Ansi.ansi().fgBrightDefault().a(" (" + e + ")").reset());
        } else {
            printer().print("(" + e + ")");
        }
        printer().println();
    }

    private String getStatus(JsonObject r) {
        boolean failed = "failed".equals(r.getString("status"));
        boolean timeout = "timeout".equals(r.getString("status"));
        boolean reply = r.containsKey("message");
        String status;
        Ansi.Color c = Ansi.Color.GREEN;
        if (failed) {
            status = "Failed (exception)";
            c = Ansi.Color.RED;
        } else if (replyFile != null) {
            if (poll) {
                status = "Poll save to fill (success)";
            } else {
                status = "Reply save to file (success)";
            }
        } else if (reply) {
            if (poll) {
                status = "Poll received (success)";
            } else {
                status = "Reply received (success)";
            }
        } else if (timeout) {
            status = "Timeout";
            c = Ansi.Color.YELLOW;
        } else if (poll) {
            status = "Poll (success)";
        } else {
            status = "Sent (success)";
        }
        if (loggingColor) {
            return Ansi.ansi().fg(c).a(status).reset().toString();
        } else {
            return status;
        }
    }

    protected JsonObject waitForOutputFile(File outputFile) {
        StopWatch watch = new StopWatch();
        while (watch.taken() < timeout) {
            try {
                // give time for response to be ready
                Thread.sleep(20);

                if (outputFile.exists()) {
                    FileInputStream fis = new FileInputStream(outputFile);
                    String text = IOHelper.loadText(fis);
                    IOHelper.close(fis);
                    return (JsonObject) Jsoner.deserialize(text);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

}
