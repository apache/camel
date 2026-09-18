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
package org.apache.camel.dsl.jbang.core.commands.infra;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import picocli.CommandLine;

@CommandLine.Command(name = "ps", description = "Displays running services", sortOptions = false,
                     showDefaultValues = true,
                     footer = {
                             "%nExamples:",
                             "  camel infra ps",
                             "  camel infra ps kafka" })
public class InfraPs extends InfraBaseCommand {

    @CommandLine.Parameters(description = "Service name", arity = "0..1")
    List<String> serviceName;

    public InfraPs(CamelJBangMain main) {
        super(main);
    }

    @Override
    protected boolean showPidColumn() {
        return true;
    }

    /**
     * Emits one row per running instance rather than one row per alias, so starting the same service twice shows both
     * processes, each with its own PID and SERVICE_DATA. The alias metadata only fills in the descriptive columns; the
     * rows themselves come from the pid files, so a service that is running but absent from the catalog is still
     * listed.
     */
    @Override
    protected List<Row> buildRows(Map<String, InfraServiceAlias> services) {
        String name = serviceName == null || serviceName.isEmpty() ? null : serviceName.get(0);

        List<RunningService> instances = findRunningServices(name);

        List<Row> rows = new ArrayList<>(instances.size());
        for (RunningService instance : instances) {
            InfraServiceAlias alias = services.get(instance.alias());
            rows.add(new Row(
                    instance.pid(),
                    instance.alias(),
                    implementationsOf(alias),
                    alias != null ? alias.getDescription() : null,
                    readServiceData(instance.pidFile()),
                    alias != null && alias.isUiSupported()));
        }

        return rows;
    }

    @Override
    public Integer doCall() throws Exception {
        return listServices(rows -> {
        });
    }
}
