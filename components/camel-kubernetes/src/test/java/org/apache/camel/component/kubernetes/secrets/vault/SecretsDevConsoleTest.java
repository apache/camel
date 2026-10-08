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
package org.apache.camel.component.kubernetes.secrets.vault;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.component.kubernetes.properties.SecretPropertiesFunction;
import org.apache.camel.console.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SecretsDevConsoleTest extends CamelTestSupport {

    @Test
    public void testKubernetesSecretsConsoleResolves() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("kubernetes-secrets");
        assertNotNull(con);
        assertEquals("camel", con.getGroup());
        assertEquals("kubernetes-secrets", con.getId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testJsonWithoutConfiguredNames(String names) throws Exception {
        context.getVaultConfiguration().kubernetes().setSecrets(names);
        DevConsole con = startConsole();
        Map<?, ?> out = (Map<?, ?>) con.call(DevConsole.MediaType.JSON);
        assertEquals(List.of(), out.get("secrets"));
        assertFalse(out.containsKey("masterUrl"));
        assertFalse(out.containsKey("login"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testTextWithoutConfiguredNames(String names) throws Exception {
        context.getVaultConfiguration().kubernetes().setSecrets(names);
        DevConsole con = startConsole();
        String out = (String) con.call(DevConsole.MediaType.TEXT);
        assertTrue(out.contains("Secrets in use:"));
        assertFalse(out.contains("Master Url:"));
        assertFalse(out.contains("Login:"));
    }

    @Test
    public void testConfiguredNamesJson() throws Exception {
        context.getVaultConfiguration().kubernetes().setSecrets("zulu,alpha");
        context.getVaultConfiguration().kubernetesConfigmaps().setConfigmaps("unrelated");
        context.getVaultConfiguration().kubernetes().setRefreshEnabled(true);
        context.getVaultConfiguration().kubernetesConfigmaps().setRefreshEnabled(false);
        DevConsole con = startConsole();
        Map<?, ?> out = (Map<?, ?>) con.call(DevConsole.MediaType.JSON);
        assertEquals(true, out.get("refreshEnabled"));
        assertEquals(List.of(Map.of("name", "alpha"), Map.of("name", "zulu")), out.get("secrets"));
    }

    @Test
    public void testConfiguredNamesText() throws Exception {
        context.getVaultConfiguration().kubernetes().setSecrets("zulu,alpha");
        context.getVaultConfiguration().kubernetesConfigmaps().setConfigmaps("unrelated");
        DevConsole con = startConsole();
        String out = (String) con.call(DevConsole.MediaType.TEXT);
        assertTrue(out.contains("Secrets in use:"));
        assertTrue(out.contains("alpha"));
        assertTrue(out.contains("zulu"));
        assertTrue(out.indexOf("alpha") < out.indexOf("zulu"));
        assertFalse(out.contains("unrelated"));
    }

    @Test
    public void testJsonWithoutVaultConfiguration() throws Exception {
        context.getVaultConfiguration().setKubernetesVaultConfiguration(null);
        DevConsole con = startConsole();
        Map<?, ?> out = (Map<?, ?>) con.call(DevConsole.MediaType.JSON);
        assertEquals(List.of(), out.get("secrets"));
        assertFalse(out.containsKey("refreshEnabled"));
    }

    @Test
    public void testTextWithoutVaultConfiguration() throws Exception {
        context.getVaultConfiguration().setKubernetesVaultConfiguration(null);
        DevConsole con = startConsole();
        String out = (String) con.call(DevConsole.MediaType.TEXT);
        assertFalse(out.contains("Refresh Enabled:"));
    }

    private DevConsole startConsole() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("camel.kubernetes-config.local-mode", "true");
        context.getPropertiesComponent().setInitialProperties(properties);
        context.getPropertiesComponent().addPropertiesFunction(new SecretPropertiesFunction());
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("kubernetes-secrets");
        ServiceHelper.startService(con);
        return con;
    }
}
