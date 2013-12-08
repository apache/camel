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

/**
 * Command to use the <a href="camel.apache.org/backlogtracer">Backlog Tracer</a>.
 */
@Command(scope = "camel", name = "backlog-tracer-info", description = "Displays the current status of the Backlog tracer")
public class BacklogTracerInfo extends CamelCommandSupport {

    @Argument(index = 0, name = "context", description = "The name of the Camel context.", required = true, multiValued = false)
    String context;

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

        System.out.println("BacklogTracer context:\t\t" + camel.getName());
        System.out.println("BacklogTracer enabled:\t\t" + backlogTracer.isEnabled());
        System.out.println("BacklogTracer pattern:\t\t" + (backlogTracer.getTracePattern() != null ? backlogTracer.getTracePattern() : ""));
        System.out.println("BacklogTracer filter:\t\t" + (backlogTracer.getTraceFilter() != null ? backlogTracer.getTraceFilter() : ""));
        System.out.println("BacklogTracer removeOnDump:\t" + backlogTracer.isRemoveOnDump());
        System.out.println("BacklogTracer backlogSize:\t" + backlogTracer.getBacklogSize());
        System.out.println("BacklogTracer tracerCount:\t" + backlogTracer.getTraceCounter());
        System.out.println("BacklogTracer body...");
        System.out.println("\tmaxChars:\t\t" + backlogTracer.getBodyMaxChars());
        System.out.println("\tincludeFiles:\t\t" + backlogTracer.isBodyIncludeFiles());
        System.out.println("\tincludeStreams:\t\t" + backlogTracer.isBodyIncludeStreams());
        return null;
    }
}
