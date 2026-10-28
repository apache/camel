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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code camel_control} acts on the integration its name (or pid) names, which becomes the selected one, so an agent
 * asking to stop one app does not stop the app that happens to be selected (CAMEL-25424).
 */
class McpFacadeControlTest {

    private final List<String> calls = new ArrayList<>();
    private final AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(new ArrayList<>());
    private final AtomicReference<List<InfraInfo>> infra = new AtomicReference<>(new ArrayList<>());
    private final MonitorContext ctx = new MonitorContext(data, infra);
    private final McpFacade facade = new McpFacade(
            ctx, data, null, null, null, null, null, null, null, null, null, null, recordingBridge());

    @Test
    void theNamedIntegrationIsStoppedNotTheSelectedOne() {
        integration("orders", "100");
        integration("shipping", "200");
        ctx.selectedPid = "200";

        String answer = facade.controlIntegration("stop", "orders");

        assertThat(answer).isEqualTo("Stopping orders");
        assertThat(ctx.selectedPid).isEqualTo("100");
        assertThat(calls).containsExactly("stopProcess [false]");
    }

    @Test
    void aPidWorksLikeAName() {
        integration("orders", "100");
        integration("shipping", "200");

        assertThat(facade.controlIntegration("stop-routes", "200")).isEqualTo("Routes stopped for shipping");
        assertThat(calls).containsExactly("sendRouteCommand [200, *, stop]");
    }

    @Test
    void anInfraServiceIsFoundByItsAlias() {
        InfraInfo kafka = new InfraInfo();
        kafka.alias = "kafka";
        kafka.pid = "300";
        infra.get().add(kafka);

        assertThat(facade.controlIntegration("stop", "kafka")).isEqualTo("Stopping kafka");
        assertThat(ctx.selectedPid).isEqualTo("300");
    }

    @Test
    void anUnknownNameSaysWhatIsRunning() {
        integration("orders", "100");
        ctx.selectedPid = "100";

        assertThat(facade.controlIntegration("stop", "nope"))
                .startsWith("Error: no integration with name or pid nope").contains("orders (pid 100)");
        assertThat(calls).isEmpty();
    }

    @Test
    void anUnknownNameAlsoListsTheInfraServices() {
        integration("orders", "100");
        InfraInfo kafka = new InfraInfo();
        kafka.alias = "kafka";
        kafka.pid = "300";
        infra.get().add(kafka);

        assertThat(facade.controlIntegration("stop", "kafak"))
                .contains("orders (pid 100)").contains("kafka (infra, pid 300)");
    }

    @Test
    void withoutANameTheOnlyOneRunningIsUsed() {
        integration("orders", "100");

        assertThat(facade.controlIntegration("restart", null)).isEqualTo("Restarting orders");
        assertThat(calls).containsExactly("restartProcess []");
    }

    @Test
    void withoutANameAndSeveralRunningOneMustBeNamed() {
        integration("orders", "100");
        integration("shipping", "200");

        assertThat(facade.controlIntegration("stop", null)).contains("give its name or pid");
        assertThat(calls).isEmpty();
    }

    private void integration(String name, String pid) {
        IntegrationInfo info = new IntegrationInfo();
        info.name = name;
        info.pid = pid;
        data.get().add(info);
    }

    private McpFacade.MonitorBridge recordingBridge() {
        return (McpFacade.MonitorBridge) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { McpFacade.MonitorBridge.class }, (proxy, method, args) -> {
                    calls.add(method.getName() + " " + Arrays.toString(args != null ? args : new Object[0]));
                    return method.getReturnType() == boolean.class ? false : null;
                });
    }
}
