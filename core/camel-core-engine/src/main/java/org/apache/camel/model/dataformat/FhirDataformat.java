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
package org.apache.camel.model.dataformat;

import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

public abstract class FhirDataformat extends DataFormatDefinition {
    @XmlTransient
    @Metadata(label = "advanced")
    private Object fhirContext;

    @XmlAttribute
    @Metadata(enums = "DSTU2,DSTU2_HL7ORG,DSTU2_1,DSTU3,R4", defaultValue = "DSTU3")
    private String fhirVersion;

    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String prettyPrint;

    @XmlTransient
    @Metadata(label = "advanced")
    private Object parserErrorHandler;

    @XmlTransient
    @Metadata(label = "advanced")
    private Object parserOptions;

    @XmlTransient
    @Metadata(label = "advanced")
    private Object preferTypes;

    @XmlTransient
    @Metadata(label = "advanced")
    private Object forceResourceId;

    @XmlAttribute
    @Metadata(label = "advanced")
    private String serverBaseUrl;

    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String omitResourceId;

    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String encodeElementsAppliesToChildResourcesOnly;

    @XmlAttribute
    @Metadata(label = "advanced")
    private Set<String> encodeElements;

    @XmlAttribute
    @Metadata(label = "advanced")
    private Set<String> dontEncodeElements;

    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String stripVersionsFromReferences;

    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String overrideResourceIdWithBundleEntryFullUrl;

    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String summaryMode;

    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String suppressNarratives;

    @XmlAttribute
    @Metadata(label = "advanced")
    private List<String> dontStripVersionsFromReferencesAtPaths;

    protected FhirDataformat(String dataFormatName) {
        super(dataFormatName);
    }

    protected FhirDataformat() {
        // This constructor is needed by jaxb for schema generation
    }

    public Object getFhirContext() {
        return fhirContext;
    }

    public void setFhirContext(Object fhirContext) {
        this.fhirContext = fhirContext;
    }

    public String getFhirVersion() {
        return fhirVersion;
    }

    /**
     * The version of FHIR to use. Possible values are:
     * DSTU2,DSTU2_HL7ORG,DSTU2_1,DSTU3,R4
     */
    public void setFhirVersion(String fhirVersion) {
        this.fhirVersion = fhirVersion;
    }

    public String getPrettyPrint() {
        return prettyPrint;
    }

    /**
     * Sets the "pretty print" flag, meaning that the parser will encode
     * resources with human-readable spacing and newlines between elements
     * instead of condensing output as much as possible.
     *
     * @param prettyPrint The flag
     */
    public void setPrettyPrint(String prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public Object getParserErrorHandler() {
        return parserErrorHandler;
    }

    /**
     * Registers an error handler which will be invoked when any parse errors
     * are found
     *
     * @param parserErrorHandler The error handler to set. Must not be null.
     */
    public void setParserErrorHandler(Object parserErrorHandler) {
        this.parserErrorHandler = parserErrorHandler;
    }

    public Object getParserOptions() {
        return parserOptions;
    }

    /**
     * Sets the parser options object which will be used to supply default
     * options to newly created parsers.
     *
     * @param parserOptions The parser options object
     */
    public void setParserOptions(Object parserOptions) {
        this.parserOptions = parserOptions;
    }

    public Object getPreferTypes() {
        return preferTypes;
    }

    /**
     * If set, when parsing resources the parser will try to use the given types
     * when possible, in the order that they are provided (from highest to
     * lowest priority). For example, if a custom type which declares to
     * implement the Patient resource is passed in here, and the parser is
     * parsing a Bundle containing a Patient resource, the parser will use the
     * given custom type.
     *
     * @param preferTypes The preferred types, or <code>null</code>
     */
    public void setPreferTypes(Object preferTypes) {
        this.preferTypes = preferTypes;
    }

    public Object getForceResourceId() {
        return forceResourceId;
    }

    /**
     * When encoding, force this resource ID to be encoded as the resource ID
     */
    public void setForceResourceId(Object forceResourceId) {
        this.forceResourceId = forceResourceId;
    }

    public String getServerBaseUrl() {
        return serverBaseUrl;
    }

    /**
     * Sets the server's base URL used by this parser. If a value is set,
     * resource references will be turned into relative references if they are
     * provided as absolute URLs but have a base matching the given base.
     *
     * @param serverBaseUrl The base URL, e.g. "http://example.com/base"
     */
    public void setServerBaseUrl(String serverBaseUrl) {
        this.serverBaseUrl = serverBaseUrl;
    }

    public String getOmitResourceId() {
        return omitResourceId;
    }

    /**
     * If set to <code>true</code> (default is <code>false</code>) the ID of any
     * resources being encoded will not be included in the output. Note that
     * this does not apply to contained resources, only to root resources. In
     * other words, if this is set to <code>true</code>, contained resources
     * will still have local IDs but the outer/containing ID will not have an
     * ID.
     *
     * @param omitResourceId Should resource IDs be omitted
     */
    public void setOmitResourceId(String omitResourceId) {
        this.omitResourceId = omitResourceId;
    }

    public String getEncodeElementsAppliesToChildResourcesOnly() {
        return encodeElementsAppliesToChildResourcesOnly;
    }

    /**
     * If set to <code>true</code> (default is false), the values supplied to
     * {@link #setEncodeElements(Set)} will not be applied to the root resource
     * (typically a Bundle), but will be applied to any sub-resources contained
     * within it (i.e. search result resources in that bundle)
     */
    public void setEncodeElementsAppliesToChildResourcesOnly(String encodeElementsAppliesToChildResourcesOnly) {
        this.encodeElementsAppliesToChildResourcesOnly = encodeElementsAppliesToChildResourcesOnly;
    }

    public Set<String> getEncodeElements() {
        return encodeElements;
    }

    /**
     * If provided, specifies the elements which should be encoded, to the
     * exclusion of all others. Valid values for this field would include:
     * <ul>
     * <li><b>Patient</b> - Encode patient and all its children</li>
     * <li><b>Patient.name</b> - Encode only the patient's name</li>
     * <li><b>Patient.name.family</b> - Encode only the patient's family
     * name</li>
     * <li><b>*.text</b> - Encode the text element on any resource (only the
     * very first position may contain a wildcard)</li>
     * <li><b>*.(mandatory)</b> - This is a special case which causes any
     * mandatory fields (min > 0) to be encoded</li>
     * </ul>
     *
     * @param encodeElements The elements to encode
     * @see #setDontEncodeElements(Set)
     */
    public void setEncodeElements(Set<String> encodeElements) {
        this.encodeElements = encodeElements;
    }

    public Set<String> getDontEncodeElements() {
        return dontEncodeElements;
    }

    /**
     * If provided, specifies the elements which should NOT be encoded. Valid
     * values for this field would include:
     * <ul>
     * <li><b>Patient</b> - Don't encode patient and all its children</li>
     * <li><b>Patient.name</b> - Don't encode the patient's name</li>
     * <li><b>Patient.name.family</b> - Don't encode the patient's family
     * name</li>
     * <li><b>*.text</b> - Don't encode the text element on any resource (only
     * the very first position may contain a wildcard)</li>
     * </ul>
     * <p>
     * DSTU2 note: Note that values including meta, such as
     * <code>Patient.meta</code> will work for DSTU2 parsers, but values with
     * subelements on meta such as <code>Patient.meta.lastUpdated</code> will
     * only work in DSTU3+ mode.
     * </p>
     *
     * @param dontEncodeElements The elements to encode
     * @see #setEncodeElements(Set)
     */
    public void setDontEncodeElements(Set<String> dontEncodeElements) {
        this.dontEncodeElements = dontEncodeElements;
    }

    public String getStripVersionsFromReferences() {
        return stripVersionsFromReferences;
    }

    /**
     * If set to
     * <code>true<code> (which is the default), resource references containing a version
     * will have the version removed when the resource is encoded. This is generally good behaviour because
     * in most situations, references from one resource to another should be to the resource by ID, not
     * by ID and version. In some cases though, it may be desirable to preserve the version in resource
     * links. In that case, this value should be set to <code>false</code>.
     * <p>
     * This method provides the ability to globally disable reference encoding.
     * If finer-grained control is needed, use
     * {@link #setDontStripVersionsFromReferencesAtPaths(List)}
     * </p>
     *
     * @param stripVersionsFromReferences Set this to
     *            <code>false<code> to prevent the parser from removing resource versions
     *                                    from references (or <code>null</code>
     *            to apply the default setting from the
     *            {@link #setParserOptions(Object)}
     * @see #setDontStripVersionsFromReferencesAtPaths(List)
     */
    public void setStripVersionsFromReferences(String stripVersionsFromReferences) {
        this.stripVersionsFromReferences = stripVersionsFromReferences;
    }

    public String getOverrideResourceIdWithBundleEntryFullUrl() {
        return overrideResourceIdWithBundleEntryFullUrl;
    }

    /**
     * If set to <code>true</code> (which is the default), the
     * Bundle.entry.fullUrl will override the Bundle.entry.resource's resource
     * id if the fullUrl is defined. This behavior happens when parsing the
     * source data into a Bundle object. Set this to <code>false</code> if this
     * is not the desired behavior (e.g. the client code wishes to perform
     * additional validation checks between the fullUrl and the resource id).
     *
     * @param overrideResourceIdWithBundleEntryFullUrl Set this to
     *            <code>false</code> to prevent the parser from overriding
     *            resource ids with the Bundle.entry.fullUrl (or
     *            <code>null</code> to apply the default setting from the
     *            {@link #setParserOptions(Object)})
     */
    public void setOverrideResourceIdWithBundleEntryFullUrl(String overrideResourceIdWithBundleEntryFullUrl) {
        this.overrideResourceIdWithBundleEntryFullUrl = overrideResourceIdWithBundleEntryFullUrl;
    }

    public String getSummaryMode() {
        return summaryMode;
    }

    /**
     * If set to <code>true</code> (default is <code>false</code>) only elements
     * marked by the FHIR specification as being "summary elements" will be
     * included.
     */
    public void setSummaryMode(String summaryMode) {
        this.summaryMode = summaryMode;
    }

    public String getSuppressNarratives() {
        return suppressNarratives;
    }

    /**
     * If set to <code>true</code> (default is <code>false</code>), narratives
     * will not be included in the encoded values.
     */
    public void setSuppressNarratives(String suppressNarratives) {
        this.suppressNarratives = suppressNarratives;
    }

    public List<String> getDontStripVersionsFromReferencesAtPaths() {
        return dontStripVersionsFromReferencesAtPaths;
    }

    /**
     * If supplied value(s), any resource references at the specified paths will
     * have their resource versions encoded instead of being automatically
     * stripped during the encoding process. This setting has no effect on the
     * parsing process.
     * <p>
     * This method provides a finer-grained level of control than
     * {@link #setStripVersionsFromReferences(String)} and any paths specified
     * by this method will be encoded even if
     * {@link #setStripVersionsFromReferences(String)} has been set to
     * <code>true</code> (which is the default)
     * </p>
     *
     * @param dontStripVersionsFromReferencesAtPaths A collection of paths for
     *            which the resource versions will not be removed automatically
     *            when serializing, e.g. "Patient.managingOrganization" or
     *            "AuditEvent.object.reference". Note that only resource name
     *            and field names with dots separating is allowed here (no
     *            repetition indicators, FluentPath expressions, etc.). Set to
     *            <code>null</code> to use the value set in the
     *            {@link #setParserOptions(Object)}
     * @see #setStripVersionsFromReferences(String)
     */
    public void setDontStripVersionsFromReferencesAtPaths(List<String> dontStripVersionsFromReferencesAtPaths) {
        this.dontStripVersionsFromReferencesAtPaths = dontStripVersionsFromReferencesAtPaths;
    }

}
