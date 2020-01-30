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
package org.apache.camel.reifier.dataformat;

import java.util.Map;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.dataformat.FhirDataformat;

public class FhirDataFormatReifier<T extends FhirDataformat> extends DataFormatReifier<T> {

    public FhirDataFormatReifier(DataFormatDefinition definition) {
        super((T)definition);
    }

    @Override
    protected void prepareDataFormatConfig(Map<String, Object> properties) {
        properties.put("fhirContext", definition.getFhirContext());
        properties.put("fhirVersion", definition.getFhirVersion());
        properties.put("dontStripVersionsFromReferencesAtPaths", definition.getDontStripVersionsFromReferencesAtPaths());
        properties.put("dontEncodeElements", definition.getDontEncodeElements());
        properties.put("encodeElements", definition.getEncodeElements());
        properties.put("serverBaseUrl", definition.getServerBaseUrl());
        properties.put("forceResourceId", definition.getForceResourceId());
        properties.put("preferTypes", definition.getPreferTypes());
        properties.put("parserOptions", definition.getParserOptions());
        properties.put("parserErrorHandler", definition.getParserErrorHandler());
        properties.put("encodeElementsAppliesToChildResourcesOnly", definition.isEncodeElementsAppliesToChildResourcesOnly());
        properties.put("omitResourceId", definition.isOmitResourceId());
        properties.put("prettyPrint", definition.isPrettyPrint());
        properties.put("suppressNarratives", definition.isSuppressNarratives());
        properties.put("summaryMode", definition.isSummaryMode());
        properties.put("overrideResourceIdWithBundleEntryFullUrl", definition.getOverrideResourceIdWithBundleEntryFullUrl());
        properties.put("stripVersionsFromReferences", definition.getStripVersionsFromReferences());
    }

}
