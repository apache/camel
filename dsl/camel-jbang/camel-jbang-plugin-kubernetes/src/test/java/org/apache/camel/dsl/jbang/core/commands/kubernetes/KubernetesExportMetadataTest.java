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
package org.apache.camel.dsl.jbang.core.commands.kubernetes;

import org.aesh.command.impl.internal.ProcessedCommand;
import org.aesh.command.metadata.MetadataProviderRegistry;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandTree;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * KubernetesExport extends Export, and has options of its own: aesh must not use the generated metadata of Export for
 * it, which does not have them.
 */
class KubernetesExportMetadataTest {

    @AfterEach
    void resetRegistries() {
        MetadataProviderRegistry.reset();
    }

    @Test
    void kubernetesExportHasItsOwnOptionsAndTheOnesOfExport() throws Exception {
        CamelJBangMain.useCommandMetadata();

        ProcessedCommand<?, ?> command = new CommandTree(new CamelJBangMain().command(new KubernetesExport()))
                .processedCommand();

        // an option of KubernetesExport, and one of Export
        assertNotNull(command.findLongOptionNoActivatorCheck("image-registry"));
        assertNotNull(command.findLongOptionNoActivatorCheck("runtime"));
    }
}
