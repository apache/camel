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
package org.apache.camel.maven.packaging.dsl.component;

import org.apache.camel.tooling.model.ComponentModel;

public class EnrichedComponentModel extends ComponentModel {
    protected boolean isAlias;

    public EnrichedComponentModel() {
    }

    public EnrichedComponentModel(final ComponentModel componentModel, final boolean isAlias) {
        name = componentModel.getName();
        title = componentModel.getTitle();
        description = componentModel.getDescription();
        firstVersion = componentModel.getFirstVersion();
        javaType = componentModel.getJavaType();
        label = componentModel.getLabel();
        deprecated = componentModel.isDeprecated();
        deprecationNote = componentModel.getDeprecationNote();
        scheme = componentModel.getScheme();
        extendsScheme = componentModel.getExtendsScheme();
        alternativeSchemes = componentModel.getAlternativeSchemes();
        syntax = componentModel.getSyntax();
        alternativeSyntax = componentModel.getAlternativeSyntax();
        async = componentModel.isAsync();
        consumerOnly = componentModel.isConsumerOnly();
        producerOnly = componentModel.isProducerOnly();
        lenientProperties = componentModel.isLenientProperties();
        verifiers = componentModel.getVerifiers();
        groupId = componentModel.getGroupId();
        artifactId = componentModel.getArtifactId();
        version = componentModel.getVersion();
        options.addAll(componentModel.getComponentOptions());
        endpointOptions.addAll(componentModel.getEndpointOptions());

        this.isAlias = isAlias;
    }

    public boolean isAlias() {
        return isAlias;
    }
}
