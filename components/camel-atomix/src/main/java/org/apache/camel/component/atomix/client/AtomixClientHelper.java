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
package org.apache.camel.component.atomix.client;

import java.io.InputStream;
import java.util.Properties;

import io.atomix.AtomixClient;
import org.apache.camel.CamelContext;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;

public final class AtomixClientHelper {
    private AtomixClientHelper() {
    }

    public static AtomixClient createClient(CamelContext camelContext, AtomixClientConfiguration configuration) throws Exception {
        AtomixClient atomix = configuration.getAtomix();

        if (atomix == null) {
            final AtomixClient.Builder atomixBuilder;

            String uri = configuration.getConfigurationUri();
            if (ObjectHelper.isNotEmpty(uri)) {
                uri = camelContext.resolvePropertyPlaceholders(uri);
                try (InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, uri)) {
                    Properties properties = new Properties();
                    properties.load(is);

                    atomixBuilder = AtomixClient.builder(properties);
                }
            } else {
                atomixBuilder = AtomixClient.builder();
            }

            if (configuration.getTransport() != null) {
                atomixBuilder.withTransport(
                    camelContext.getInjector().newInstance(configuration.getTransport())
                );
            }

            atomix = atomixBuilder.build();
        }

        return atomix;
    }
}
