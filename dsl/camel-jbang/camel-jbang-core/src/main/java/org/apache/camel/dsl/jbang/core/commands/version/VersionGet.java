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

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeCompletionCandidates;
import picocli.CommandLine;

@CommandLine.Command(name = "get", description = "Displays current Camel version")
public class VersionGet extends CamelCommand {

    @CommandLine.Option(names = { "--runtime" }, completionCandidates = RuntimeCompletionCandidates.class,
                        description = "Runtime (spring-boot, quarkus, or camel-main)")
    protected String runtime;

    public VersionGet(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer call() throws Exception {
        CamelCatalog catalog = new DefaultCamelCatalog();
        String v = catalog.getCatalogVersion();
        System.out.println("Camel CLI version: " + v);

        CommandLineHelper.loadProperties(properties -> {
            String uv = properties.getProperty("camel-version");
            if (uv != null) {
                System.out.println("User configuration Camel version: " + uv);
            }
        });

        return 0;
    }

}
