/*
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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.io.File;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

public abstract class CamelRouteAction extends ActionBaseCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name;

    @CommandLine.Option(names = { "--all" },
                        description = "To select all running Camel integrations")
    boolean all;

    @CommandLine.Option(names = { "--id" },
                        description = "Route ids (multiple ids can be separated by comma)", defaultValue = "*")
    String id = "*";

    public CamelRouteAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (!all && name == null) {
            return 0;
        } else if (all) {
            name = "*";
        }

        List<Long> pids = findPids(name);

        for (long pid : pids) {
            JsonObject root = new JsonObject();
            root.put("action", "route");
            root.put("id", id);
            File f = getActionFile(Long.toString(pid));
            onAction(root);
            IOHelper.writeText(root.toJson(), f);
        }

        return 0;
    }

    protected abstract void onAction(JsonObject root);
}
