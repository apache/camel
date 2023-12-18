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

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.v1.Integration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class IntegrationDeleteTest extends KubeBaseTest {

    @Test
    public void shouldVerifyArguments() throws Exception {
        Assertions.assertThrows(RuntimeCamelException.class, createCommand()::doCall,
                "Missing integration name as argument or --all option.");
    }

    @Test
    public void shouldDeleteIntegration() throws Exception {
        Integration integration = createIntegration();
        kubernetesClient.resources(Integration.class).resource(integration).create();

        IntegrationDelete command = createCommand();
        command.names = new String[] { integration.getMetadata().getName() };
        command.doCall();

        Assertions.assertEquals("Integration routes deleted", printer.getOutput());

        Assertions.assertEquals(0, kubernetesClient.resources(Integration.class).list().getItems().size());
    }

    @Test
    public void shouldHandleIntegrationNotFound() throws Exception {
        IntegrationDelete command = createCommand();
        command.names = new String[] { "mickey-mouse" };
        command.doCall();

        Assertions.assertEquals("Integration mickey-mouse deletion skipped - not found", printer.getOutput());
    }

    @Test
    public void shouldDeleteAll() throws Exception {
        Integration integration1 = createIntegration("foo");
        Integration integration2 = createIntegration("bar");

        kubernetesClient.resources(Integration.class).resource(integration1).create();
        kubernetesClient.resources(Integration.class).resource(integration2).create();

        IntegrationDelete command = createCommand();
        command.all = true;
        command.doCall();

        Assertions.assertEquals("Integrations deleted", printer.getOutput());
        Assertions.assertEquals(0, kubernetesClient.resources(Integration.class).list().getItems().size());
    }

    private IntegrationDelete createCommand() {
        IntegrationDelete command = new IntegrationDelete(new CamelJBangMain().withPrinter(printer));
        command.withClient(kubernetesClient);
        return command;
    }

}
