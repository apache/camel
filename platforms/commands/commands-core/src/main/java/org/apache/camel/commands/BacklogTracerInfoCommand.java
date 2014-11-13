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
package org.apache.camel.commands;

import java.io.PrintStream;

import org.apache.camel.CamelContext;
import org.apache.camel.processor.interceptor.BacklogTracer;

public class BacklogTracerInfoCommand extends AbstractContextCommand {

    public BacklogTracerInfoCommand(String context) {
        super(context);
    }

    @Override
    protected Object performContextCommand(CamelController camelController, CamelContext camelContext, PrintStream out, PrintStream err) throws Exception {
        BacklogTracer backlogTracer = BacklogTracer.getBacklogTracer(camelContext);
        if (backlogTracer == null) {
            backlogTracer = (BacklogTracer) camelContext.getDefaultBacklogTracer();
        }

        out.println("BacklogTracer context:\t\t" + camelContext.getName());
        out.println("BacklogTracer enabled:\t\t" + backlogTracer.isEnabled());
        out.println("BacklogTracer pattern:\t\t" + (backlogTracer.getTracePattern() != null ? backlogTracer.getTracePattern() : ""));
        out.println("BacklogTracer filter:\t\t" + (backlogTracer.getTraceFilter() != null ? backlogTracer.getTraceFilter() : ""));
        out.println("BacklogTracer removeOnDump:\t" + backlogTracer.isRemoveOnDump());
        out.println("BacklogTracer backlogSize:\t" + backlogTracer.getBacklogSize());
        out.println("BacklogTracer tracerCount:\t" + backlogTracer.getTraceCounter());
        out.println("BacklogTracer body...");
        out.println("\tmaxChars:\t\t" + backlogTracer.getBodyMaxChars());
        out.println("\tincludeFiles:\t\t" + backlogTracer.isBodyIncludeFiles());
        out.println("\tincludeStreams:\t\t" + backlogTracer.isBodyIncludeStreams());
        return null;
    }

}
