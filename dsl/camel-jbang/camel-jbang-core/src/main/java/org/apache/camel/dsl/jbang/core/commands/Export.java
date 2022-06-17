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
package org.apache.camel.dsl.jbang.core.commands;

import picocli.CommandLine.Command;

@Command(name = "export",
         description = "Export to other runtimes such as Spring Boot or Quarkus")
class Export extends ExportBaseCommand {

    public Export(CamelJBangMain main) {
        super(main);
    }

    @Override
    protected Integer export() throws Exception {
        if ("spring-boot".equals(runtime)) {
            return export(new ExportSpringBoot(getMain()));
        } else if ("quarkus".equals(runtime)) {
            return export(new ExportQuarkus(getMain()));
        } else if ("camel-main".equals(runtime)) {
            return export(new ExportCamelMain(getMain()));
        } else {
            System.err.println("Unknown runtime: " + runtime);
            return 1;
        }
    }

    private Integer export(ExportBaseCommand cmd) throws Exception {
        // copy properties from this to cmd
        cmd.runtime = this.runtime;
        cmd.gav = this.gav;
        cmd.exportDir = this.exportDir;
        cmd.fresh = this.fresh;
        cmd.javaVersion = this.javaVersion;
        cmd.kameletsVersion = this.kameletsVersion;
        cmd.logging = this.logging;
        cmd.loggingLevel = this.loggingLevel;
        cmd.mainClassname = this.mainClassname;
        cmd.quarkusVersion = this.quarkusVersion;
        cmd.springBootVersion = this.springBootVersion;
        // run export
        return cmd.export();
    }

}
