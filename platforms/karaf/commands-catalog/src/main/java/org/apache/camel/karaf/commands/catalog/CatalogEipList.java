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
package org.apache.camel.karaf.commands.catalog;

import org.apache.camel.commands.CatalogEipListCommand;
import org.apache.camel.karaf.commands.CamelCommandSupport;
import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.Option;

@Command(scope = "camel", name = "catalog-eip-list", description = "Lists all Camel EIPs from the Camel catalog")
public class CatalogEipList extends CamelCommandSupport {

    @Option(name = "--verbose", aliases = "-v", description = "Verbose output which shows more information",
            required = false, multiValued = false, valueToShowInHelp = "false")
    boolean verbose;

    @Option(name = "--label", aliases = "-l", description = "To filter EIPs by their label(s), such as transformation",
            required = false, multiValued = false)
    String label;

    protected Object doExecute() throws Exception {
        CatalogEipListCommand command = new CatalogEipListCommand(verbose, label);
        return command.execute(camelController, System.out, System.err);
    }

}
