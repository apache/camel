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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.dataformat.FhirDataformat;

public class FhirDataFormatReifier<T extends FhirDataformat> extends DataFormatReifier<T> {

    public FhirDataFormatReifier(CamelContext camelContext, DataFormatDefinition definition) {
        super(camelContext, (T) definition);
    }

    @Override
    protected void prepareDataFormatConfig(Map<String, Object> properties) {
        properties.put("fhirVersion", definition.getFhirVersion());
        properties.put("fhirContext", asRef(definition.getFhirContext()));
        properties.put("serverBaseUrl", definition.getServerBaseUrl());
        properties.put("forceResourceId", asRef(definition.getForceResourceId()));
        properties.put("preferTypesNames", definition.getPreferTypes());
        properties.put("parserOptions", asRef(definition.getParserOptions()));
        properties.put("parserErrorHandler", asRef(definition.getParserErrorHandler()));
        properties.put("encodeElementsAppliesToChildResourcesOnly", definition.getEncodeElementsAppliesToChildResourcesOnly());
        properties.put("omitResourceId", definition.getOmitResourceId());
        properties.put("prettyPrint", definition.getPrettyPrint());
        properties.put("suppressNarratives", definition.getSuppressNarratives());
        properties.put("summaryMode", definition.getSummaryMode());
        properties.put("overrideResourceIdWithBundleEntryFullUrl", definition.getOverrideResourceIdWithBundleEntryFullUrl());
        properties.put("stripVersionsFromReferences", definition.getStripVersionsFromReferences());
        // convert string to list/set for the following options
        if (definition.getDontStripVersionsFromReferencesAtPaths() != null) {
            List<String> list = Arrays.stream(definition.getDontStripVersionsFromReferencesAtPaths().split(","))
                    .toList();
            properties.put("dontStripVersionsFromReferencesAtPaths", list);
        }
        if (definition.getDontEncodeElements() != null) {
            Set<String> set = Arrays.stream(definition.getDontEncodeElements().split(",")).collect(Collectors.toSet());
            properties.put("dontEncodeElements", set);
        }
        if (definition.getEncodeElements() != null) {
            Set<String> set = Arrays.stream(definition.getEncodeElements().split(",")).collect(Collectors.toSet());
            properties.put("encodeElements", set);
        }
    }

}
