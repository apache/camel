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
package org.apache.camel.dsl.jbang.core.commands.version;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.RuntimeTypeConverter;
import picocli.CommandLine;

@CommandLine.Command(name = "set", description = "Set/change current Camel version", sortOptions = false,
                     showDefaultValues = true)
public class VersionSet extends CamelCommand {

    @CommandLine.Parameters(description = "Camel version", arity = "0..1")
    String version;

    @CommandLine.Option(names = { "--runtime" },
                        completionCandidates = RuntimeCompletionCandidates.class,
                        converter = RuntimeTypeConverter.class,
                        description = "Runtime (${COMPLETION-CANDIDATES})")
    RuntimeType runtime;

    @CommandLine.Option(names = { "--repo", "--repos" }, description = "Maven repository for downloading the dependencies")
    String repo;

    @CommandLine.Option(names = { "--reset" }, description = "Reset by removing any custom version settings")
    boolean reset;

    @CommandLine.Option(names = { "--global" }, description = "Use global or local configuration")
    boolean global = true;

    public VersionSet(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        boolean local = !global;
        CommandLineHelper.createPropertyFile(local);

        CommandLineHelper.loadProperties(properties -> {
            if (reset) {
                properties.remove("camel-version");
                properties.remove("repos");
                properties.remove("runtime");
            } else {
                if (version != null) {
                    properties.put("camel-version", version);
                }
                if (repo != null) {
                    properties.put("repos", repo);
                }
                if (runtime != null) {
                    properties.put("runtime", runtime.runtime());
                }
            }
            CommandLineHelper.storeProperties(properties, printer(), local);
        }, local);

        return 0;
    }

}
