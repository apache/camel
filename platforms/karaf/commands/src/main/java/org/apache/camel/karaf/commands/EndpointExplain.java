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

import org.apache.camel.commands.EndpointExplainCommand;
import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.Option;

@Command(scope = "camel", name = "endpoint-explain", description = "Explain all Camel endpoints available in CamelContexts.")
public class EndpointExplain extends CamelCommandSupport {

    @Argument(index = 0, name = "name", description = "The Camel context name where to look for the endpoints", required = false, multiValued = false)
    String name;

    @Option(name = "--verbose", aliases = "-v", description = "Verbose output to explain all options",
            required = false, multiValued = false, valueToShowInHelp = "false")
    boolean verbose;

    @Option(name = "--filter", aliases = "-f", description = "To filter endpoints by pattern",
            required = false, multiValued = false)
    String filter;

    protected Object doExecute() throws Exception {
        EndpointExplainCommand command = new EndpointExplainCommand(name, verbose, filter);
        return command.execute(camelController, System.out, System.err);
    }

}
