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
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonObject;
import org.fusesource.jansi.Ansi;
import picocli.CommandLine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

@CommandLine.Command(name = "log",
                     description = "Tail logs from running Camel integrations")
public class CamelLogAction extends ActionBaseCommand {

    private static final char FIRST_ESC_CHAR = 27;
    private static final char SECOND_ESC_CHAR = '[';

//    private static final String ANSI_ESCAPE = ("" + FIRST_ESC_CHAR) + ("" + SECOND_ESC_CHAR) + ("" + '\\') + ("" + 'd') + ("" + '+') + ("" + 'm');

    private static final String ANSI_ESCAPE = "0x1b\\[\\d*m";

    @CommandLine.Parameters(description = "Name or pid of running Camel integration. (default selects all)", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--logging-color" }, defaultValue = "true", description = "Use colored logging")
    boolean loggingColor = true;

    @CommandLine.Option(names = { "--tail" },
            description = "The number of lines from the end of the logs to show. Defaults to showing all logs.")
    int tail;

    public CamelLogAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer call() throws Exception {
        List<Long> pids = findPids(name);

        if (pids.size() == 1) {
            // single log file then no need to interleave logs
            File log = logFile(pids.get(0));
            if (log.exists()) {
                LineNumberReader lnr = new LineNumberReader(new FileReader(log));
                String line;

                // dump only last N lines
                if (tail > 0) {
                    Queue<String> fifo = new ArrayBlockingQueue<>(tail);
                    do {
                        line = lnr.readLine();
                        if (line != null) {
                            while (!fifo.offer(line)) {
                                fifo.poll();
                            }
                        }
                    } while (line != null);
                    fifo.forEach(this::printLine);
                }

                // continue read new log lines
                do {
                    line = lnr.readLine();
                    if (line != null) {
                        printLine(line);
                    } else {
                        Thread.sleep(50);
                    }
                } while (true);
            }
        } else {
            // TODO: interleave logs based on PID + timestamp
            System.out.println("Logs from multiple Camel integrations is currently not yet implemented");
        }
        return 0;
    }

    protected void printLine(String line) {
        if (loggingColor) {
            System.out.println(line);
        } else {
            // unescape ANSI colors
            StringBuilder sb = new StringBuilder();
            boolean escaping = false;
            char[] arr = line.toCharArray();
            for (int i = 0; i < arr.length; i++) {
                char ch = arr[i];
                if (escaping) {
                    if (ch == 'm') {
                        escaping = false;
                    }
                    continue;
                }
                char ch2 = i < arr.length - 1 ? arr[i + 1] : 0;
                if (ch == 27 && ch2 == '[') {
                    escaping = true;
                    continue;
                }

                sb.append(ch);
            }
            line = sb.toString();
            System.out.println(line);
        }
    }

    private static File logFile(long pid) {
        File dir = new File(System.getProperty("user.home"), ".camel");
        String name = pid + ".log";
        return new File(dir, name);
    }

}
