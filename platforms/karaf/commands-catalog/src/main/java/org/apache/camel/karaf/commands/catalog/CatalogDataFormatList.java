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

import org.apache.camel.commands.CatalogDataFormatListCommand;
import org.apache.camel.karaf.commands.internal.CamelControllerImpl;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;

@Command(scope = "camel", name = "catalog-dataformat-list", description = "Lists all Camel dataformats from the Camel catalog")
@Service
public class CatalogDataFormatList extends CamelControllerImpl implements Action {

    @Option(name = "--verbose", aliases = "-v", description = "Verbose output which shows more information",
            required = false, multiValued = false, valueToShowInHelp = "false")
    boolean verbose;

    @Option(name = "--label", aliases = "-l", description = "To filter dataformats by their label(s), such as xml",
            required = false, multiValued = false)
    String label;

    public Object execute() throws Exception {
        CatalogDataFormatListCommand command = new CatalogDataFormatListCommand(verbose, label);
        return command.execute(this, System.out, System.err);
    }

}
