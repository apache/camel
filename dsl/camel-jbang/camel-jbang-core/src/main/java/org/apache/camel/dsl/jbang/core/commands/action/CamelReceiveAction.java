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

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import picocli.CommandLine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;

@CommandLine.Command(name = "receive",
                     description = "Receive messages from endpoints", sortOptions = false)
public class CamelReceiveAction extends ActionBaseCommand {

    public static class ActionCompletionCandidates implements Iterable<String> {

        public ActionCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("dump", "start", "stop").iterator();
        }
    }

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--action" }, completionCandidates = ActionCompletionCandidates.class,
            defaultValue = "dump",
            description = "Action to start, stop, or dump messages")
    String action;

    @CommandLine.Option(names = { "--endpoint" },
                        description = "Endpoint where to receive the messages from (can be uri, pattern, or refer to a route id)")
    String endpoint;

    @CommandLine.Option(names = { "--output-file" },
                        description = "Saves messages received to the file with the given name (override if exists)")
    String outputFile;

    @CommandLine.Option(names = { "--tail" }, defaultValue = "-1",
            description = "The number of messages from the end of the receive to show. Use -1 to read from the beginning. Use 0 to read only new lines. Defaults to showing all messages from beginning.")
    int tail = -1;

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

    @CommandLine.Option(names = { "--follow" }, defaultValue = "true",
            description = "Keep following and outputting new received messages (use ctrl + c to exit).")
    boolean follow = true;

    private volatile long pid;

    private MessageTableHelper tableHelper;

    private static class Pid {
        String pid;
        String name;
        Queue<String> fifo;
        int depth;
        LineNumberReader reader;
    }

    public CamelReceiveAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if ("dump".equals(action)) {
            return doDumpCall();
        } else {
            return doStartStopCall(action);
        }
    }

    protected Integer doStartStopCall(String action) throws Exception {
        // ensure output file is deleted before executing action
        File outputFile = getOutputFile(Long.toString(pid));
        FileUtil.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "receive");
        root.put("endpoint", endpoint);
        root.put("enabled", "start".equals(action) ? "true" : "false");

        File f = getActionFile(Long.toString(pid));
        try {
            IOHelper.writeText(root.toJson(), f);
        } catch (Exception e) {
            // ignore
        }
        waitForOutputFile(outputFile);

        // delete output file after use
        FileUtil.deleteFile(outputFile);

        return 0;
    }

    protected Integer doDumpCall() throws Exception {
        List<Long> pids = findPids(name);
        if (pids.isEmpty()) {
            return 0;
        } else if (pids.size() > 1) {
            printer().println("Name or pid " + name + " matches " + pids.size()
                              + " running Camel integrations. Specify a name or PID that matches exactly one.");
            return 0;
        }

        this.pid = pids.get(0);

        boolean waitMessage = true;
        StopWatch watch = new StopWatch();
        boolean more = true;
        Pid pid = new Pid();
        pid.pid = "" + this.pid;


        if (tail != 0) {
            tailTraceFiles(pids, tail);
        } else {

        }

        do {
            waitMessage = true;
            int lines = readReceiveFiles(pid);
            if (lines > 0) {
                more = dumpReceivedFiles(pids, 0, null);
            } else if (lines == 0) {
                Thread.sleep(100);
            } else {
                break;
            }
        } while (follow || more);

        return 0;
    }

    private int readReceiveFiles(Pid pid) throws Exception {
        int lines = 0;
        if (pid.reader == null) {
            File file = getReceiveFile(pid.pid);
            if (file.exists()) {
                pid.reader = new LineNumberReader(new FileReader(file));
                if (tail == 0) {
                    // only read new lines so forward to end of reader
                    long size = file.length();
                    pid.reader.skip(size);
                }
            }
        }
        if (pid.reader != null) {
            String line;
            do {
                try {
                    line = pid.reader.readLine();
                    if (line != null) {
                        lines++;
                        // switch fifo to be unlimited as we use it for new traces
                        if (pid.fifo == null || pid.fifo instanceof ArrayBlockingQueue) {
                            pid.fifo = new ArrayDeque<>();
                        }
                        pid.fifo.offer(line);
                    }
                } catch (IOException e) {
                    // ignore
                    line = null;
                }
            } while (line != null);
        }

        return lines;
    }

    private boolean dumpReceivedFiles(Pid pid) {
        List<Row> rows = new ArrayList<>();

        Queue<String> queue = pid.fifo;




    }

    private void tailTraceFiles(Map<Long, Pid> pids, int tail) throws Exception {
        for (Pid pid : pids.values()) {
            File file = getReceiveFile(pid.pid);
            if (file.exists()) {
                pid.reader = new LineNumberReader(new FileReader(file));
                String line;
                if (tail <= 0) {
                    pid.fifo = new ArrayDeque<>();
                } else {
                    pid.fifo = new ArrayBlockingQueue<>(tail);
                }
                do {
                    line = pid.reader.readLine();
                    if (line != null) {
                        while (!pid.fifo.offer(line)) {
                            pid.fifo.poll();
                        }
                    }
                } while (line != null);
            }
        }
    }

    protected JsonObject waitForOutputFile(File outputFile) {
        StopWatch watch = new StopWatch();
        while (watch.taken() < 5000) {
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

    private static class Row {
        String pid;
        long uid;
        JsonObject message;
    }

}
