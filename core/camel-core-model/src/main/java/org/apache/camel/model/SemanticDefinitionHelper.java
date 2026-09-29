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
package org.apache.camel.model;

import org.apache.camel.CamelContext;
import org.apache.camel.model.app.SemanticDefinition;
import org.apache.camel.model.spi.SemanticDefinitionConfigurer;
import org.apache.camel.spi.Resource;

/** Connects semantic declarations to the optional semantic language module. */
public final class SemanticDefinitionHelper {
    private SemanticDefinitionHelper() {
    }

    /** Register declarations before route initialization, without requiring semantic support for ordinary routes. */
    public static void configure(
            CamelContext context, Resource resource, String source, SemanticDefinition definition) {
        SemanticDefinitionConfigurer configurer
                = context.getCamelContextExtension().getContextPlugin(SemanticDefinitionConfigurer.class);
        if (configurer == null) {
            if (definition == null || definition.getQuestions().isEmpty()) {
                return;
            }
            // Concurrent discovery may create equivalent instances of the stateless default configurer.
            // The semantic module synchronizes access to shared question state.
            configurer = context.getCamelContextExtension().getDefaultFactoryFinder()
                    .newInstance("semantic-configurer", SemanticDefinitionConfigurer.class)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Semantic question declarations require camel-semantic on the classpath"));
            context.getCamelContextExtension().addContextPlugin(SemanticDefinitionConfigurer.class, configurer);
        }
        configurer.configure(context, resource, source, definition);
    }
}
