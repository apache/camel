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
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import picocli.CommandLine;

@CommandLine.Command(name = "get", description = "Displays current Camel version")
public class VersionGet extends CamelCommand {

    public VersionGet(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        String jv = VersionHelper.getJBangVersion();
        if (jv != null) {
            printer().println("JBang version: " + jv);
        }

        CamelCatalog catalog = new DefaultCamelCatalog();
        String v = catalog.getCatalogVersion();
        printer().println("Camel JBang version: " + v);

        CommandLineHelper.loadProperties(properties -> {
            String uv = properties.getProperty("camel-version");
            String kv = properties.getProperty("kamelets-version");
            String repos = properties.getProperty("repos");
            String runtime = properties.getProperty("runtime");
            if (uv != null || repos != null || runtime != null) {
                printer().println("User configuration:");
                if (uv != null) {
                    printer().println("    camel-version = " + uv);
                }
                if (kv != null) {
                    printer().println("    kamelets-version = " + kv);
                }
                if (runtime != null) {
                    printer().println("    runtime = " + runtime);
                }
                if (repos != null) {
                    printer().println("    repos = " + repos);
                }
            }
        });

        return 0;
    }

}
