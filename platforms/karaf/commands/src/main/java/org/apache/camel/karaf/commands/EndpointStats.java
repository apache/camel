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

import org.apache.camel.commands.EndpointStatisticCommand;
import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.Option;

@Command(scope = "camel", name = "endpoint-stats", description = "Display endpoint runtime statistics")
public class EndpointStats extends CamelCommandSupport {

    @Argument(index = 0, name = "name", description = "The name of the Camel context (support wildcard)", required = false, multiValued = false)
    String name;

    @Option(name = "--filter", aliases = "-f", description = "Filter the list by in,out,static,dynamic",
            required = false, multiValued = true)
    String[] filter;

    @Option(name = "--decode", aliases = "-d", description = "Whether to decode the endpoint uri so its human readable",
            required = false, multiValued = false, valueToShowInHelp = "true")
    boolean decode = true;

    protected Object doExecute() throws Exception {
        EndpointStatisticCommand command = new EndpointStatisticCommand(name, decode, filter);
        return command.execute(camelController, System.out, System.err);
    }

}
