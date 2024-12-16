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
package org.apache.camel.dsl.jbang.core.commands.k;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.KubernetesPodLogs;
import org.apache.camel.v1.Integration;
import picocli.CommandLine.Command;

@Command(name = "logs", description = "Print the logs of an integration", sortOptions = false)
public class IntegrationLogs extends KubernetesPodLogs {

    public IntegrationLogs(CamelJBangMain main) {
        super(main);
        projectNameSuppliers.add(() -> projectNameFromFilePath(() -> filePath));
    }

    public Integer doCall() throws Exception {

        String integrationName = getProjectName();

        if (label == null) {
            Integration integration = client(Integration.class).withName(integrationName).get();
            if (integration == null) {
                printer().printf("Integration %s not found%n", integrationName);
                return 1;
            }

            label = "%s=%s".formatted(CamelKCommand.INTEGRATION_LABEL, integrationName);
        }

        container = CamelKCommand.INTEGRATION_CONTAINER_NAME;

        return super.doCall();
    }

    public void withName(String name) {
        this.name = name;
    }
}
