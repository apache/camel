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

import java.util.Map;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;
import org.apache.camel.vault.HashicorpVaultConfiguration;

@DevConsole(name = "hashicorp-secrets", displayName = "HashiCorp Secrets", description = "HashiCorp Vault Secrets")
public class SecretsDevConsole extends AbstractDevConsole {

    public record Response(
            @Metadata(description = "The Vault host (only present when configured)") String host,
            @Metadata(description = "The Vault port (only present when configured)") String port,
            @Metadata(description = "The Vault scheme (only present when configured)") String scheme,
            @Metadata(description = "The login method (only present when configured)") String login) {
    }

    private HashicorpVaultPropertiesFunction propertiesFunction;

    public SecretsDevConsole() {
        super("camel", "hashicorp-secrets", "HashiCorp Secrets", "HashiCorp Vault Secrets");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (getCamelContext().getPropertiesComponent().hasPropertiesFunction("hashicorp")) {
            PropertiesFunction pf = getCamelContext().getPropertiesComponent().getPropertiesFunction("hashicorp");
            if (pf instanceof HashicorpVaultPropertiesFunction hvpf) {
                propertiesFunction = hvpf;
            }
        }
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        HashicorpVaultConfiguration hashicorp = getCamelContext().getVaultConfiguration().getHashicorpVaultConfiguration();
        sb.append("Hashicorp Vault Secrets:");
        if (hashicorp != null) {
            sb.append(String.format("%n    Host: %s", hashicorp.getHost()));
            sb.append(String.format("%n    Port: %s", hashicorp.getPort()));
            sb.append(String.format("%n    Scheme: %s", hashicorp.getScheme()));
            sb.append("\n    Login: OAuth Token");
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        String host = null;
        String port = null;
        String scheme = null;
        String login = null;

        HashicorpVaultConfiguration hashicorp = getCamelContext().getVaultConfiguration().getHashicorpVaultConfiguration();
        if (hashicorp != null) {
            host = hashicorp.getHost();
            port = hashicorp.getPort();
            scheme = hashicorp.getScheme();
            login = "OAuth Token";
        }

        Response response = new Response(host, port, scheme, login);
        return JsonRecordSupport.toJsonObject(response);
    }
}
