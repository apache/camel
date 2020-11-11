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
package org.apache.camel.component.fhir;

import java.util.List;
import java.util.Set;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.context.ParserOptions;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.parser.IParserErrorHandler;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatContentTypeHeader;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;

public abstract class FhirDataFormat extends ServiceSupport implements DataFormat, DataFormatName, DataFormatContentTypeHeader {

    private FhirContext fhirContext;
    private String fhirVersion;
    private boolean contentTypeHeader = true;
    private IParserErrorHandler parserErrorHandler;
    private ParserOptions parserOptions;
    private String serverBaseUrl;
    private boolean prettyPrint;
    private List<Class<? extends IBaseResource>> preferTypes;
    private boolean omitResourceId;
    private IIdType forceResourceId;
    private boolean encodeElementsAppliesToChildResourcesOnly;
    private Set<String> encodeElements;
    private Set<String> dontEncodeElements;
    private Boolean stripVersionsFromReferences;
    private Boolean overrideResourceIdWithBundleEntryFullUrl;
    private boolean summaryMode;
    private boolean suppressNarratives;
    private List<String> dontStripVersionsFromReferencesAtPaths;

    public FhirContext getFhirContext() {
        return fhirContext;
    }

    public void setFhirContext(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
    }

    public String getFhirVersion() {
        return fhirVersion;
    }

    public void setFhirVersion(String fhirVersion) {
        this.fhirVersion = fhirVersion;
    }

    public boolean isContentTypeHeader() {
        return contentTypeHeader;
    }

    public void setContentTypeHeader(boolean contentTypeHeader) {
        this.contentTypeHeader = contentTypeHeader;
    }

    public IParserErrorHandler getParserErrorHandler() {
        return parserErrorHandler;
    }

    public void setParserErrorHandler(IParserErrorHandler parserErrorHandler) {
        this.parserErrorHandler = parserErrorHandler;
    }

    public ParserOptions getParserOptions() {
        return parserOptions;
    }

    public void setParserOptions(ParserOptions parserOptions) {
        this.parserOptions = parserOptions;
    }

    public String getServerBaseUrl() {
        return serverBaseUrl;
    }

    public void setServerBaseUrl(String serverBaseUrl) {
        this.serverBaseUrl = serverBaseUrl;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public List<Class<? extends IBaseResource>> getPreferTypes() {
        return preferTypes;
    }

    public void setPreferTypes(List<Class<? extends IBaseResource>> preferTypes) {
        this.preferTypes = preferTypes;
    }

    public boolean isOmitResourceId() {
        return omitResourceId;
    }

    public void setOmitResourceId(boolean omitResourceId) {
        this.omitResourceId = omitResourceId;
    }

    public IIdType getForceResourceId() {
        return forceResourceId;
    }

    public void setForceResourceId(IIdType forceResourceId) {
        this.forceResourceId = forceResourceId;
    }

    public boolean isEncodeElementsAppliesToChildResourcesOnly() {
        return encodeElementsAppliesToChildResourcesOnly;
    }

    public void setEncodeElementsAppliesToChildResourcesOnly(boolean encodeElementsAppliesToChildResourcesOnly) {
        this.encodeElementsAppliesToChildResourcesOnly = encodeElementsAppliesToChildResourcesOnly;
    }

    public Set<String> getEncodeElements() {
        return encodeElements;
    }

    public void setEncodeElements(Set<String> encodeElements) {
        this.encodeElements = encodeElements;
    }

    public Set<String> getDontEncodeElements() {
        return dontEncodeElements;
    }

    public void setDontEncodeElements(Set<String> dontEncodeElements) {
        this.dontEncodeElements = dontEncodeElements;
    }

    public Boolean getStripVersionsFromReferences() {
        return stripVersionsFromReferences;
    }

    public void setStripVersionsFromReferences(Boolean stripVersionsFromReferences) {
        this.stripVersionsFromReferences = stripVersionsFromReferences;
    }

    public Boolean getOverrideResourceIdWithBundleEntryFullUrl() {
        return overrideResourceIdWithBundleEntryFullUrl;
    }

    public void setOverrideResourceIdWithBundleEntryFullUrl(Boolean overrideResourceIdWithBundleEntryFullUrl) {
        this.overrideResourceIdWithBundleEntryFullUrl = overrideResourceIdWithBundleEntryFullUrl;
    }

    public boolean isSummaryMode() {
        return summaryMode;
    }

    public void setSummaryMode(boolean summaryMode) {
        this.summaryMode = summaryMode;
    }

    public boolean isSuppressNarratives() {
        return suppressNarratives;
    }

    public void setSuppressNarratives(boolean suppressNarratives) {
        this.suppressNarratives = suppressNarratives;
    }

    public List<String> getDontStripVersionsFromReferencesAtPaths() {
        return dontStripVersionsFromReferencesAtPaths;
    }

    public void setDontStripVersionsFromReferencesAtPaths(List<String> dontStripVersionsFromReferencesAtPaths) {
        this.dontStripVersionsFromReferencesAtPaths = dontStripVersionsFromReferencesAtPaths;
    }

    protected void configureParser(IParser parser) {
        if (ObjectHelper.isNotEmpty(getServerBaseUrl())) {
            parser.setServerBaseUrl(getServerBaseUrl());
        }
        if (ObjectHelper.isNotEmpty(getDontEncodeElements())) {
            parser.setDontEncodeElements(getDontEncodeElements());
        }
        if (ObjectHelper.isNotEmpty(getDontStripVersionsFromReferencesAtPaths())) {
            parser.setDontStripVersionsFromReferencesAtPaths(getDontStripVersionsFromReferencesAtPaths());
        }
        if (ObjectHelper.isNotEmpty(getEncodeElements())) {
            parser.setEncodeElements(getEncodeElements());
        }
        if (ObjectHelper.isNotEmpty(getForceResourceId())) {
            parser.setEncodeForceResourceId(getForceResourceId());
        }
        if (ObjectHelper.isNotEmpty(getPreferTypes())) {
            parser.setPreferTypes(getPreferTypes());
        }
        if (ObjectHelper.isNotEmpty(getParserErrorHandler())) {
            parser.setParserErrorHandler(getParserErrorHandler());
        }
        if (ObjectHelper.isNotEmpty(getOverrideResourceIdWithBundleEntryFullUrl())) {
            parser.setOverrideResourceIdWithBundleEntryFullUrl(getOverrideResourceIdWithBundleEntryFullUrl());
        }
        if (ObjectHelper.isNotEmpty(getStripVersionsFromReferences())) {
            parser.setStripVersionsFromReferences(getStripVersionsFromReferences());
        }
        parser.setSummaryMode(isSummaryMode());
        parser.setOmitResourceId(isOmitResourceId());
        parser.setPrettyPrint(isPrettyPrint());
        parser.setEncodeElementsAppliesToChildResourcesOnly(isEncodeElementsAppliesToChildResourcesOnly());
    }

    @Override
    protected void doStart() throws Exception {
        if (fhirContext == null && fhirVersion != null) {
            FhirVersionEnum version = FhirVersionEnum.valueOf(fhirVersion);
            fhirContext = new FhirContext(version);
        } else if (fhirContext == null) {
            fhirContext = FhirContext.forDstu3();
        }
        if (ObjectHelper.isNotEmpty(parserOptions)) {
            fhirContext.setParserOptions(parserOptions);
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

}
