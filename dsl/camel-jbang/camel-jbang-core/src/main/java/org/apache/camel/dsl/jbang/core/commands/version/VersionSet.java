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
import picocli.CommandLine;

@CommandLine.Command(name = "set", description = "Set/change current Camel version")
public class VersionSet extends CamelCommand {

    @CommandLine.Parameters(description = "Camel version", arity = "0..1")
    String version;

    @CommandLine.Option(names = { "--runtime" }, completionCandidates = RuntimeCompletionCandidates.class,
                        description = "Runtime (spring-boot, quarkus, or camel-main)")
    String runtime;

    @CommandLine.Option(names = { "--repo", "--repos" }, description = "Maven repository for downloading the dependencies")
    String repo;

    @CommandLine.Option(names = { "--reset" }, description = "Reset by removing any custom version settings")
    boolean reset;

    public VersionSet(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        CommandLineHelper.createPropertyFile();

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
                    properties.put("runtime", runtime);
                }
            }
            CommandLineHelper.storeProperties(properties);
        });

        return 0;
    }

}
