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

import org.apache.camel.commands.ContextInflightCommand;
import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.Option;

@Command(scope = "camel", name = "context-inflight", description = "List inflight exchanges.")
public class ContextInflight extends CamelCommandSupport {

    @Argument(index = 0, name = "name", description = "The Camel context name", required = true, multiValued = false)
    String name;

    @Option(name = "--limit", aliases = "-l", description = "To limit the number of exchanges shown",
            required = false, multiValued = false)
    int limit = -1;

    @Option(name = "--sort", aliases = "-s", description = "true = sort by longest duration, false = sort by exchange id",
            required = false, multiValued = false, valueToShowInHelp = "false")
    boolean sortByLongestDuration;

    protected Object doExecute() throws Exception {
        ContextInflightCommand command = new ContextInflightCommand(name, limit, sortByLongestDuration);
        return command.execute(camelController, System.out, System.err);
    }

}
