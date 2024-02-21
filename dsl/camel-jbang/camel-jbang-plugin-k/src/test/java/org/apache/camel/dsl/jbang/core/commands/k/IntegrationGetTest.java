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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.v1.Integration;
import org.apache.camel.v1.IntegrationStatus;
import org.apache.camel.v1.integrationstatus.Conditions;
import org.apache.camel.v1.integrationstatus.IntegrationKit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class IntegrationGetTest extends KubeBaseTest {

    @Test
    public void shouldListIntegrationsEmpty() throws Exception {
        createCommand().doCall();

        Assertions.assertEquals("", printer.getOutput());
    }

    @Test
    public void shouldListReadyIntegration() throws Exception {
        Integration integration = createIntegration();

        IntegrationStatus status = new IntegrationStatus();

        IntegrationKit kit = new IntegrationKit();
        kit.setName("kit-123456789");
        status.setIntegrationKit(kit);

        status.setPhase("Running");
        status.setConditions(new ArrayList<>());
        Conditions readyCondition = new Conditions();
        readyCondition.setType("Ready");
        readyCondition.setStatus("True");
        status.getConditions().add(readyCondition);
        integration.setStatus(status);

        kubernetesClient.resources(Integration.class).resource(integration).create();

        createCommand().doCall();

        List<String> output = printer.getLines();
        Assertions.assertEquals("NAME    PHASE    KIT            READY", output.get(0));
        Assertions.assertEquals("routes  Running  kit-123456789   1/1", output.get(1));
    }

    @Test
    public void shouldListPendingIntegration() throws Exception {
        Integration integration = createIntegration("building");
        IntegrationStatus status = new IntegrationStatus();

        status.setPhase("Building Kit");
        status.setConditions(new ArrayList<>());
        Conditions readyCondition = new Conditions();
        readyCondition.setType("Ready");
        readyCondition.setStatus("False");
        status.getConditions().add(readyCondition);
        integration.setStatus(status);

        kubernetesClient.resources(Integration.class).resource(integration).create();

        createCommand().doCall();

        List<String> output = printer.getLines();
        Assertions.assertEquals("NAME      PHASE         KIT  READY", output.get(0));
        Assertions.assertEquals("building  Building Kit        0/1", output.get(1));
    }

    @Test
    public void shouldListIntegrationNames() throws Exception {
        Integration integration1 = createIntegration("foo");
        Integration integration2 = createIntegration("bar");

        kubernetesClient.resources(Integration.class).resource(integration1).create();
        kubernetesClient.resources(Integration.class).resource(integration2).create();

        IntegrationGet command = createCommand();
        command.name = true;
        command.doCall();

        Assertions.assertEquals("foo\nbar", printer.getOutput());
    }

    private IntegrationGet createCommand() {
        IntegrationGet command = new IntegrationGet(new CamelJBangMain().withPrinter(printer));
        command.withClient(kubernetesClient);
        return command;
    }

}
