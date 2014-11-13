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

import org.apache.camel.commands.BacklogTracerDumpCommand;
import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.Option;

/**
 * Command to use the <a href="camel.apache.org/backlogtracer">Backlog Tracer</a>.
 */
@Command(scope = "camel", name = "backlog-tracer-dump", description = "Dumps traced messages from the Backlog tracer")
public class BacklogTracerDump extends CamelCommandSupport {

    @Argument(index = 0, name = "context", description = "The name of the Camel context.", required = true, multiValued = false)
    String context;

    @Argument(index = 1, name = "pattern", description = "To dump trace messages only for nodes or routes matching the given pattern (default is all)", required = false, multiValued = false)
    String pattern;

    @Option(name = "--format", aliases = "-f", description = "Format to use with the dump action (text or xml)", required = false, multiValued = false, valueToShowInHelp = "text")
    String format;

    @Option(name = "--bodySize", aliases = "-bs", description = "To limit the body size when using text format", required = false, multiValued = false)
    Integer bodySize;

    @Override
    protected Object doExecute() throws Exception {
        BacklogTracerDumpCommand command = new BacklogTracerDumpCommand(context, pattern, format, bodySize);
        return command.execute(camelController, System.out, System.err);
    }

}
