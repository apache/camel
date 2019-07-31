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

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.dataformat.FhirDataformat;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.ObjectHelper;

public class FhirDataFormatReifier<T extends FhirDataformat> extends DataFormatReifier<T> {

    public FhirDataFormatReifier(DataFormatDefinition definition) {
        super((T) definition);
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (definition.getContentTypeHeader() != null) {
            setProperty(camelContext, dataFormat, "contentTypeHeader", definition.getContentTypeHeader());
        }
        if (definition.getFhirContext() != null) {
            setProperty(camelContext, dataFormat, "fhirContext", definition.getFhirContext());
        }
        if (definition.getFhirVersion() != null) {
            setProperty(camelContext, dataFormat, "fhirVersion", definition.getFhirVersion());
        }
        if (ObjectHelper.isNotEmpty(definition.getDontStripVersionsFromReferencesAtPaths())) {
            setProperty(camelContext, dataFormat, "dontStripVersionsFromReferencesAtPaths", definition.getDontStripVersionsFromReferencesAtPaths());
        }
        if (ObjectHelper.isNotEmpty(definition.getDontEncodeElements())) {
            setProperty(camelContext, dataFormat, "dontEncodeElements", definition.getDontEncodeElements());
        }
        if (ObjectHelper.isNotEmpty(definition.getEncodeElements())) {
            setProperty(camelContext, dataFormat, "encodeElements", definition.getEncodeElements());
        }
        if (ObjectHelper.isNotEmpty(definition.getServerBaseUrl())) {
            setProperty(camelContext, dataFormat, "serverBaseUrl", definition.getServerBaseUrl());
        }
        if (ObjectHelper.isNotEmpty(definition.getForceResourceId())) {
            setProperty(camelContext, dataFormat, "forceResourceId", definition.getForceResourceId());
        }
        if (ObjectHelper.isNotEmpty(definition.getPreferTypes())) {
            setProperty(camelContext, dataFormat, "preferTypes", definition.getPreferTypes());
        }
        if (ObjectHelper.isNotEmpty(definition.getParserOptions())) {
            setProperty(camelContext, dataFormat, "parserOptions", definition.getParserOptions());
        }
        if (ObjectHelper.isNotEmpty(definition.getParserErrorHandler())) {
            setProperty(camelContext, dataFormat, "parserErrorHandler", definition.getParserErrorHandler());
        }

        Boolean answer = ObjectHelper.toBoolean(definition.isEncodeElementsAppliesToChildResourcesOnly());
        if (answer != null) {
            setProperty(camelContext, dataFormat, "encodeElementsAppliesToChildResourcesOnly", answer);
        }
        answer = ObjectHelper.toBoolean(definition.isOmitResourceId());
        if (answer != null) {
            setProperty(camelContext, dataFormat, "omitResourceId", answer);
        }
        answer = ObjectHelper.toBoolean(definition.isPrettyPrint());
        if (answer != null) {
            setProperty(camelContext, dataFormat, "prettyPrint", answer);
        }
        answer = ObjectHelper.toBoolean(definition.isSuppressNarratives());
        if (answer != null) {
            setProperty(camelContext, dataFormat, "suppressNarratives", answer);
        }
        answer = ObjectHelper.toBoolean(definition.isSummaryMode());
        if (answer != null) {
            setProperty(camelContext, dataFormat, "summaryMode", answer);
        }
        answer = ObjectHelper.toBoolean(definition.getOverrideResourceIdWithBundleEntryFullUrl());
        if (answer != null) {
            setProperty(camelContext, dataFormat, "overrideResourceIdWithBundleEntryFullUrl", answer);
        }
        answer = ObjectHelper.toBoolean(definition.getStripVersionsFromReferences());
        if (answer != null) {
            setProperty(camelContext, dataFormat, "stripVersionsFromReferences", answer);
        }
    }

}
