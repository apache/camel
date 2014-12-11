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

import org.apache.camel.commands.ContextInfoCommand;
import org.apache.camel.commands.StringEscape;
import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.Option;

@Command(scope = "camel", name = "context-info", description = "Display detailed information about a Camel context.")
public class ContextInfo extends CamelCommandSupport {

    @Argument(index = 0, name = "name", description = "The name of the Camel context", required = true, multiValued = false)
    String name;

    @Option(name = "--verbose", aliases = "-v", description = "Verbose output",
            required = false, multiValued = false, valueToShowInHelp = "false")
    boolean verbose;

    private StringEscape stringEscape;

    public void setStringEscape(StringEscape stringEscape) {
        this.stringEscape = stringEscape;
    }

    @Override
    protected Object doExecute() throws Exception {
        ContextInfoCommand command = new ContextInfoCommand(name, verbose);
        command.setStringEscape(stringEscape);
        return command.execute(camelController, System.out, System.err);
    }
}
