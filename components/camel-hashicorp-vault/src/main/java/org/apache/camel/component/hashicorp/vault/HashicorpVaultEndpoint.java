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

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;

/**
 * Manage secrets in Hashicorp Vault Service
 */
@UriEndpoint(firstVersion = "3.18.0", scheme = "hashicorp-vault", title = "Hashicorp Vault",
             syntax = "hashicorp-vault:secretsEngine", category = {
                     Category.CLOUD, Category.CLOUD },
             producerOnly = true,
             headersClass = HashicorpVaultConstants.class)
public class HashicorpVaultEndpoint extends DefaultEndpoint {

    private VaultTemplate vaultTemplate;

    @UriParam
    private HashicorpVaultConfiguration configuration;

    public HashicorpVaultEndpoint(final String uri, final Component component,
                                  final HashicorpVaultConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public void doInit() throws Exception {
        super.doInit();
        vaultTemplate = configuration.getVaultTemplate() != null
                ? configuration.getVaultTemplate() : createVaultTemplate();
    }

    private VaultTemplate createVaultTemplate() {
        VaultTemplate vaultTemplate;

        VaultEndpoint vaultEndpoint = new VaultEndpoint();
        vaultEndpoint.setHost(configuration.getHost());
        vaultEndpoint.setPort(Integer.parseInt(configuration.getPort()));
        vaultEndpoint.setScheme(configuration.getScheme());

        vaultTemplate = new VaultTemplate(
                vaultEndpoint,
                new TokenAuthentication(configuration.getToken()));

        return vaultTemplate;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new HashicorpVaultProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported");
    }

    /**
     * The component configurations
     */
    public HashicorpVaultConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(HashicorpVaultConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * The vault template
     */
    public VaultTemplate getVaultTemplate() {
        return vaultTemplate;
    }

    public void setVaultTemplate(VaultTemplate vaultTemplate) {
        this.vaultTemplate = vaultTemplate;
    }
}
