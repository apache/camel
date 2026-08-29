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
package org.apache.camel.component.hashicorp.vault;

import org.apache.camel.console.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.vault.HashicorpVaultConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SecretsDevConsoleTest extends CamelTestSupport {

    @Override
    protected org.apache.camel.CamelContext createCamelContext() throws Exception {
        org.apache.camel.CamelContext ctx = super.createCamelContext();
        HashicorpVaultConfiguration hashicorp = new HashicorpVaultConfiguration();
        hashicorp.setHost("localhost");
        hashicorp.setPort("8200");
        hashicorp.setScheme("http");
        ctx.getVaultConfiguration().setHashicorpVaultConfiguration(hashicorp);
        return ctx;
    }

    @Test
    public void testSecretsConsoleText() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("hashicorp-secrets");
        assertNotNull(con);
        assertEquals("camel", con.getGroup());
        assertEquals("hashicorp-secrets", con.getId());

        String out = (String) con.call(DevConsole.MediaType.TEXT);
        assertNotNull(out);
    }

    @Test
    public void testSecretsConsoleJson() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("hashicorp-secrets");
        assertNotNull(con);

        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON);
        assertNotNull(out);
        assertEquals("localhost", out.getString("host"));
        assertEquals("8200", out.getString("port"));
        assertEquals("http", out.getString("scheme"));
        assertTrue(out.getString("login").contains("OAuth"));
    }
}
