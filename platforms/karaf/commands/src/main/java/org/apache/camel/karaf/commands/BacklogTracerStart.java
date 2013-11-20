/**
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
package org.apache.camel.karaf.commands;

import org.apache.camel.CamelContext;
import org.apache.camel.processor.interceptor.BacklogTracer;
import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.Option;

/**
 * Command to use the <a href="camel.apache.org/backlogtracer">Backlog Tracer</a>.
 */
@Command(scope = "camel", name = "backlog-tracer-start", description = "Starts the Backlog tracer")
public class BacklogTracerStart extends CamelCommandSupport {

    @Argument(index = 0, name = "context", description = "The name of the Camel context.",
            required = true, multiValued = false)
    String context;

    @Option(name = "--pattern", aliases = "-p", description = "To trace messages only for nodes or routes matching the given pattern (default is all)",
            required = false, multiValued = false)
    String pattern;

    @Option(name = "--filter", aliases = "-f", description = "To trace messages only for nodes or routes matching the given filter (using simple language by default)",
            required = false, multiValued = false)
    String filter;

    @Option(name = "--backlogSize", aliases = "-s", description = "Number of maximum traced messages in total to keep in the backlog (FIFO queue)",
            required = false, multiValued = false, valueToShowInHelp = "1000")
    Integer backlogSize;

    @Option(name = "--removeOnDump", aliases = "-r", description = "Whether to remove traced messages when dumping the messages",
            required = false, multiValued = false)
    Boolean removeOnDump;

    @Override
    protected Object doExecute() throws Exception {
        CamelContext camel = camelController.getCamelContext(context);
        if (camel == null) {
            System.err.println("CamelContext " + context + " not found.");
            return null;
        }

        BacklogTracer backlogTracer = BacklogTracer.getBacklogTracer(camel);
        if (backlogTracer == null) {
            backlogTracer = (BacklogTracer) camel.getDefaultBacklogTracer();
        }

        backlogTracer.setEnabled(true);
        if (backlogSize != null) {
            backlogTracer.setBacklogSize(backlogSize);
        }
        if (removeOnDump != null) {
            backlogTracer.setRemoveOnDump(removeOnDump);
        }
        backlogTracer.setTracePattern(pattern);
        backlogTracer.setTraceFilter(filter);

        System.out.println("BacklogTracer started on " + camel.getName() + " with size: " + backlogTracer.getBacklogSize());
        return null;
    }

}
