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
package org.apache.camel.dsl.jbang.core.commands;

import java.nio.file.Path;

import org.apache.camel.dsl.jbang.core.common.QuarkusHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import picocli.CommandLine;

/**
 * Options related to Quarkus Platform Extension Registry.
 */
public class QuarkusExtensionRegistryMixin {

    @CommandLine.Option(names = {
            "--quarkus-ext-registry" },
                        description = """
                                The base URI of Quarkus Extension Registry.
                                The default is {@value RuntimeType#QUARKUS_EXTENSION_REGISTRY_BASE_URL} unless camel.jbang.quarkus.platform.url
                                system property is set (the "/client/platforms" suffix is removed if present).
                                """)
    String quarkusExtensioRegistryBaseUri;

    public static QuarkusExtensionRegistryMixin of(Path localRegistry) {
        QuarkusExtensionRegistryMixin result = new QuarkusExtensionRegistryMixin();
        result.quarkusExtensioRegistryBaseUri = localRegistry.toUri().toString();
        return result;
    }

    public String quarkusExtensioRegistryBaseUri() {
        String result = quarkusExtensioRegistryBaseUri;
        if (result == null) {
            result = System.getProperty(QuarkusHelper.QUARKUS_PLATFORM_URL_PROPERTY);
            final String suffix = "/client/platforms";
            if (result != null && result.endsWith(suffix)) {
                result = result.substring(0, result.length() - suffix.length());
            }
        }
        if (result == null) {
            result = RuntimeType.QUARKUS_EXTENSION_REGISTRY_BASE_URL;
        }
        if (result.endsWith("/")) {
            return result.substring(0, result.length() - 1);
        }
        return result;
    }

}
