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

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;
import org.apache.camel.dsl.jbang.run.export.ExportRequest;

@CommandDefinition(name = "export", description = "Export to other runtimes (Camel Main, Spring Boot, or Quarkus)",
                   generateHelp = true,
                   footer = {
                           "Examples:",
                           "  camel export hello.java",
                           "  camel export --runtime=spring-boot hello.java",
                           "  camel export --runtime=quarkus *",
                           "  camel export --gav=com.example:myapp:1.0 hello.java",
                           "  camel export --dry-run hello.java" })
public class Export extends ExportCommandBase {

    @Option(name = "packaging",
            description = "Project packaging (jar or war; WAR requires --runtime=spring-boot)",
            defaultValue = "jar")
    protected String packaging = "jar";

    @Override
    protected ExportRequest toRequest() {
        ExportRequest request = super.toRequest();
        request.packaging = this.packaging;
        return request;
    }

}
