/**
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
package org.apache.camel.component.atomix.cluster;

import java.io.InputStream;
import java.util.Properties;

import io.atomix.AtomixReplica;
import io.atomix.catalyst.transport.Address;
import io.atomix.copycat.server.storage.Storage;
import org.apache.camel.CamelContext;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;

public final class AtomixClusterHelper {
    private AtomixClusterHelper() {
    }

    public static AtomixReplica createReplica(CamelContext camelContext, String address, AtomixClusterConfiguration configuration) throws Exception {
        return createReplica(camelContext, new Address(address), configuration);
    }

    public static AtomixReplica createReplica(CamelContext camelContext, Address address, AtomixClusterConfiguration configuration) throws Exception {
        AtomixReplica atomix = configuration.getAtomix();

        if (atomix == null) {
            final AtomixReplica.Builder atomixBuilder;

            String uri = configuration.getConfigurationUri();
            if (ObjectHelper.isNotEmpty(uri)) {
                uri = camelContext.resolvePropertyPlaceholders(uri);
                try (InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, uri)) {
                    Properties properties = new Properties();
                    properties.load(is);

                    atomixBuilder = AtomixReplica.builder(address, properties);
                }
            } else {
                atomixBuilder = AtomixReplica.builder(address);
            }

            Storage.Builder storageBuilder = Storage.builder();
            ObjectHelper.ifNotEmpty(configuration.getStorageLevel(), storageBuilder::withStorageLevel);
            ObjectHelper.ifNotEmpty(configuration.getStoragePath(), storageBuilder::withDirectory);

            atomixBuilder.withStorage(storageBuilder.build());

            if (configuration.getTransport() != null) {
                atomixBuilder.withTransport(
                    camelContext.getInjector().newInstance(configuration.getTransport())
                );
            }
            if (configuration.getClientTransport() != null) {
                atomixBuilder.withClientTransport(
                    camelContext.getInjector().newInstance(configuration.getClientTransport())
                );
            }
            if (configuration.getServerTransport() != null) {
                atomixBuilder.withServerTransport(
                    camelContext.getInjector().newInstance(configuration.getServerTransport())
                );
            }

            atomix = atomixBuilder.build();
        }

        return atomix;
    }
}
