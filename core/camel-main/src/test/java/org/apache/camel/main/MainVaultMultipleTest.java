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
package org.apache.camel.main;

import org.apache.camel.CamelContext;
import org.apache.camel.vault.VaultConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MainVaultMultipleTest {

    @Test
    public void testMainMultipleVaults() {
        Main main = new Main();

        main.addInitialProperty("camel.vault.aws.region", "myRegion");
        main.addInitialProperty("camel.vault.hashicorp.host", "myHost");
        main.addInitialProperty("camel.vault.gcp.projectId", "myProject");

        main.start();
        try {
            CamelContext context = main.getCamelContext();
            VaultConfiguration vault = context.getVaultConfiguration();

            Assertions.assertEquals("myRegion", vault.aws().getRegion());
            Assertions.assertEquals("myHost", vault.hashicorp().getHost());
            Assertions.assertEquals("myProject", vault.gcp().getProjectId());
        } finally {
            main.stop();
        }
    }

    @Test
    public void testMainVaultGetters() {
        Main main = new Main();
        main.configure().vault().aws().setRegion("myRegion");

        main.start();
        try {
            VaultConfiguration vault = main.getCamelContext().getVaultConfiguration();

            Assertions.assertNotNull(vault.getAwsVaultConfiguration());
            Assertions.assertEquals("myRegion", vault.getAwsVaultConfiguration().getRegion());
        } finally {
            main.stop();
        }
    }
}
