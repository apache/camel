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

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.builder.DataFormatBuilder;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

public abstract class FhirDataformat extends DataFormatDefinition implements ContentTypeHeaderAware {
    @XmlAttribute
    @Metadata(enums = "DSTU2,DSTU2_HL7ORG,DSTU2_1,DSTU3,R4,R5", defaultValue = "R4")
    private String fhirVersion;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String fhirContext;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String prettyPrint;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String parserErrorHandler;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String parserOptions;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String preferTypes;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String forceResourceId;
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
    private String encodeElements;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String dontEncodeElements;
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
    private String dontStripVersionsFromReferencesAtPaths;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "true",
              description = "Whether the data format should set the Content-Type header with the type from the data format."
                            + " For example application/xml for data formats marshalling to XML, or application/json for data formats marshalling to JSON")
    private String contentTypeHeader;

    protected FhirDataformat(String dataFormatName) {
        super(dataFormatName);
    }

    protected FhirDataformat() {
        // This constructor is needed by jaxb for schema generation
    }

    protected FhirDataformat(String dataFormatName, AbstractBuilder<?, ?> builder) {
        this(dataFormatName);
        this.fhirContext = builder.fhirContext;
        this.fhirVersion = builder.fhirVersion;
        this.prettyPrint = builder.prettyPrint;
        this.parserErrorHandler = builder.parserErrorHandler;
        this.parserOptions = builder.parserOptions;
        this.preferTypes = builder.preferTypes;
        this.forceResourceId = builder.forceResourceId;
        this.serverBaseUrl = builder.serverBaseUrl;
        this.omitResourceId = builder.omitResourceId;
        this.encodeElementsAppliesToChildResourcesOnly = builder.encodeElementsAppliesToChildResourcesOnly;
        this.encodeElements = builder.encodeElements;
        this.dontEncodeElements = builder.dontEncodeElements;
        this.stripVersionsFromReferences = builder.stripVersionsFromReferences;
        this.overrideResourceIdWithBundleEntryFullUrl = builder.overrideResourceIdWithBundleEntryFullUrl;
        this.summaryMode = builder.summaryMode;
        this.suppressNarratives = builder.suppressNarratives;
        this.dontStripVersionsFromReferencesAtPaths = builder.dontStripVersionsFromReferencesAtPaths;
        this.contentTypeHeader = builder.contentTypeHeader;
    }

    public String getFhirContext() {
        return fhirContext;
    }

    /**
     * To use a custom fhir context.
     *
     * Reference to object of type ca.uhn.fhir.context.FhirContext
     */
    public void setFhirContext(String fhirContext) {
        this.fhirContext = fhirContext;
    }

    public String getFhirVersion() {
        return fhirVersion;
    }

    /**
     * The version of FHIR to use. Possible values are: DSTU2,DSTU2_HL7ORG,DSTU2_1,DSTU3,R4,R5
     */
    public void setFhirVersion(String fhirVersion) {
        this.fhirVersion = fhirVersion;
    }

    public String getPrettyPrint() {
        return prettyPrint;
    }

    /**
     * Sets the "pretty print" flag, meaning that the parser will encode resources with human-readable spacing and
     * newlines between elements instead of condensing output as much as possible.
     *
     * @param prettyPrint The flag
     */
    public void setPrettyPrint(String prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public String getParserErrorHandler() {
        return parserErrorHandler;
    }

    /**
     * Registers an error handler which will be invoked when any parse errors are found.
     *
     * Reference to object of type ca.uhn.fhir.parser.IParserErrorHandler
     */
    public void setParserErrorHandler(String parserErrorHandler) {
        this.parserErrorHandler = parserErrorHandler;
    }

    public String getParserOptions() {
        return parserOptions;
    }

    /**
     * Sets the parser options object which will be used to supply default options to newly created parsers. Reference
     * to object of type ca.uhn.fhir.context.ParserOptions.
     */
    public void setParserOptions(String parserOptions) {
        this.parserOptions = parserOptions;
    }

    public String getPreferTypes() {
        return preferTypes;
    }

    /**
     * If set (FQN class names), when parsing resources the parser will try to use the given types when possible, in the
     * order that they are provided (from highest to lowest priority). For example, if a custom type which declares to
     * implement the Patient resource is passed in here, and the parser is parsing a Bundle containing a Patient
     * resource, the parser will use the given custom type.
     *
     * Multiple class names can be separated by comma.
     *
     * @param preferTypes The preferred types, or <code>null</code>
     */
    public void setPreferTypes(String preferTypes) {
        this.preferTypes = preferTypes;
    }

    public String getForceResourceId() {
        return forceResourceId;
    }

    /**
     * When encoding, force this resource ID to be encoded as the resource ID.
     *
     * Reference to object of type org.hl7.fhir.instance.model.api.IIdType
     */
    public void setForceResourceId(String forceResourceId) {
        this.forceResourceId = forceResourceId;
    }

    public String getServerBaseUrl() {
        return serverBaseUrl;
    }

    /**
     * Sets the server's base URL used by this parser. If a value is set, resource references will be turned into
     * relative references if they are provided as absolute URLs but have a base matching the given base.
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
     * If set to <code>true</code> (default is <code>false</code>) the ID of any resources being encoded will not be
     * included in the output. Note that this does not apply to contained resources, only to root resources. In other
     * words, if this is set to <code>true</code>, contained resources will still have local IDs but the
     * outer/containing ID will not have an ID.
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
     * If set to <code>true</code> (default is false), the values supplied to {@link #setEncodeElements(Set)} will not
     * be applied to the root resource (typically a Bundle), but will be applied to any sub-resources contained within
     * it (i.e. search result resources in that bundle)
     */
    public void setEncodeElementsAppliesToChildResourcesOnly(String encodeElementsAppliesToChildResourcesOnly) {
        this.encodeElementsAppliesToChildResourcesOnly = encodeElementsAppliesToChildResourcesOnly;
    }

    public String getEncodeElements() {
        return encodeElements;
    }

    /**
     * If provided, specifies the elements which should be encoded, to the exclusion of all others. Multiple elements
     * can be separated by comma when using String parameter.
     *
     * Valid values for this field would include:
     * <ul>
     * <li><b>Patient</b> - Encode patient and all its children</li>
     * <li><b>Patient.name</b> - Encode only the patient's name</li>
     * <li><b>Patient.name.family</b> - Encode only the patient's family name</li>
     * <li><b>*.text</b> - Encode the text element on any resource (only the very first position may contain a
     * wildcard)</li>
     * <li><b>*.(mandatory)</b> - This is a special case which causes any mandatory fields (min > 0) to be encoded</li>
     * </ul>
     *
     * @param encodeElements The elements to encode
     * @see                  #setDontEncodeElements(Set)
     */
    public void setEncodeElements(Set<String> encodeElements) {
        this.encodeElements = String.join(",", encodeElements);
    }

    /**
     * If provided, specifies the elements which should be encoded, to the exclusion of all others. Multiple elements
     * can be separated by comma when using String parameter.
     *
     * Valid values for this field would include:
     * <ul>
     * <li><b>Patient</b> - Encode patient and all its children</li>
     * <li><b>Patient.name</b> - Encode only the patient's name</li>
     * <li><b>Patient.name.family</b> - Encode only the patient's family name</li>
     * <li><b>*.text</b> - Encode the text element on any resource (only the very first position may contain a
     * wildcard)</li>
     * <li><b>*.(mandatory)</b> - This is a special case which causes any mandatory fields (min > 0) to be encoded</li>
     * </ul>
     *
     * @param encodeElements The elements to encode
     * @see                  #setDontEncodeElements(Set)
     */
    public void setEncodeElements(String encodeElements) {
        this.encodeElements = encodeElements;
    }

    public String getDontEncodeElements() {
        return dontEncodeElements;
    }

    /**
     * If provided, specifies the elements which should NOT be encoded. Multiple elements can be separated by comma when
     * using String parameter.
     *
     * Valid values for this field would include:
     * <ul>
     * <li><b>Patient</b> - Don't encode patient and all its children</li>
     * <li><b>Patient.name</b> - Don't encode the patient's name</li>
     * <li><b>Patient.name.family</b> - Don't encode the patient's family name</li>
     * <li><b>*.text</b> - Don't encode the text element on any resource (only the very first position may contain a
     * wildcard)</li>
     * </ul>
     * <p>
     * DSTU2 note: Note that values including meta, such as <code>Patient.meta</code> will work for DSTU2 parsers, but
     * values with subelements on meta such as <code>Patient.meta.lastUpdated</code> will only work in DSTU3+ mode.
     * </p>
     *
     * @param dontEncodeElements The elements to NOT encode
     * @see                      #setEncodeElements(Set)
     */
    public void setDontEncodeElements(Set<String> dontEncodeElements) {
        this.dontEncodeElements = String.join(",", dontEncodeElements);
    }

    /**
     * If provided, specifies the elements which should NOT be encoded. Multiple elements can be separated by comma when
     * using String parameter.
     *
     * Valid values for this field would include:
     * <ul>
     * <li><b>Patient</b> - Don't encode patient and all its children</li>
     * <li><b>Patient.name</b> - Don't encode the patient's name</li>
     * <li><b>Patient.name.family</b> - Don't encode the patient's family name</li>
     * <li><b>*.text</b> - Don't encode the text element on any resource (only the very first position may contain a
     * wildcard)</li>
     * </ul>
     * <p>
     * DSTU2 note: Note that values including meta, such as <code>Patient.meta</code> will work for DSTU2 parsers, but
     * values with subelements on meta such as <code>Patient.meta.lastUpdated</code> will only work in DSTU3+ mode.
     * </p>
     *
     * @param dontEncodeElements The elements to NOT encode
     * @see                      #setEncodeElements(Set)
     */
    public void setDontEncodeElements(String dontEncodeElements) {
        this.dontEncodeElements = dontEncodeElements;
    }

    public String getStripVersionsFromReferences() {
        return stripVersionsFromReferences;
    }

    /**
     * If set to <code>true<code> (which is the default), resource references containing a version
     * will have the version removed when the resource is encoded. This is generally good behaviour because
     * in most situations, references from one resource to another should be to the resource by ID, not
     * by ID and version. In some cases though, it may be desirable to preserve the version in resource
     * links. In that case, this value should be set to <code>false</code>.
     * <p>
     * This method provides the ability to globally disable reference encoding. If finer-grained control is needed, use
     * {@link #setDontStripVersionsFromReferencesAtPaths(List)}
     * </p>
     *
     * @param stripVersionsFromReferences Set this to
     *                                    <code>false<code> to prevent the parser from removing resource versions
     *                                    from references (or <code>null</code> to apply the default setting from the
     *                                    parser options
     */
    public void setStripVersionsFromReferences(String stripVersionsFromReferences) {
        this.stripVersionsFromReferences = stripVersionsFromReferences;
    }

    public String getOverrideResourceIdWithBundleEntryFullUrl() {
        return overrideResourceIdWithBundleEntryFullUrl;
    }

    /**
     * If set to <code>true</code> (which is the default), the Bundle.entry.fullUrl will override the
     * Bundle.entry.resource's resource id if the fullUrl is defined. This behavior happens when parsing the source data
     * into a Bundle object. Set this to <code>false</code> if this is not the desired behavior (e.g. the client code
     * wishes to perform additional validation checks between the fullUrl and the resource id).
     *
     * @param overrideResourceIdWithBundleEntryFullUrl Set this to <code>false</code> to prevent the parser from
     *                                                 overriding resource ids with the Bundle.entry.fullUrl (or
     *                                                 <code>null</code> to apply the default setting from the parser
     *                                                 options
     */
    public void setOverrideResourceIdWithBundleEntryFullUrl(String overrideResourceIdWithBundleEntryFullUrl) {
        this.overrideResourceIdWithBundleEntryFullUrl = overrideResourceIdWithBundleEntryFullUrl;
    }

    public String getSummaryMode() {
        return summaryMode;
    }

    /**
     * If set to <code>true</code> (default is <code>false</code>) only elements marked by the FHIR specification as
     * being "summary elements" will be included.
     */
    public void setSummaryMode(String summaryMode) {
        this.summaryMode = summaryMode;
    }

    public String getSuppressNarratives() {
        return suppressNarratives;
    }

    /**
     * If set to <code>true</code> (default is <code>false</code>), narratives will not be included in the encoded
     * values.
     */
    public void setSuppressNarratives(String suppressNarratives) {
        this.suppressNarratives = suppressNarratives;
    }

    public String getDontStripVersionsFromReferencesAtPaths() {
        return dontStripVersionsFromReferencesAtPaths;
    }

    /**
     * If supplied value(s), any resource references at the specified paths will have their resource versions encoded
     * instead of being automatically stripped during the encoding process. This setting has no effect on the parsing
     * process. Multiple elements can be separated by comma when using String parameter.
     * <p>
     * This method provides a finer-grained level of control than {@link #setStripVersionsFromReferences(String)} and
     * any paths specified by this method will be encoded even if {@link #setStripVersionsFromReferences(String)} has
     * been set to <code>true</code> (which is the default)
     * </p>
     *
     * @param dontStripVersionsFromReferencesAtPaths A collection of paths for which the resource versions will not be
     *                                               removed automatically when serializing, e.g.
     *                                               "Patient.managingOrganization" or "AuditEvent.object.reference".
     *                                               Note that only resource name and field names with dots separating
     *                                               is allowed here (no repetition indicators, FluentPath expressions,
     *                                               etc.). Set to <code>null</code> to use the value set in the parser
     *                                               options
     */
    public void setDontStripVersionsFromReferencesAtPaths(List<String> dontStripVersionsFromReferencesAtPaths) {
        this.dontStripVersionsFromReferencesAtPaths = String.join(",", dontStripVersionsFromReferencesAtPaths);
    }

    /**
     * If supplied value(s), any resource references at the specified paths will have their resource versions encoded
     * instead of being automatically stripped during the encoding process. This setting has no effect on the parsing
     * process. Multiple elements can be separated by comma when using String parameter.
     * <p>
     * This method provides a finer-grained level of control than {@link #setStripVersionsFromReferences(String)} and
     * any paths specified by this method will be encoded even if {@link #setStripVersionsFromReferences(String)} has
     * been set to <code>true</code> (which is the default)
     * </p>
     *
     * @param dontStripVersionsFromReferencesAtPaths A collection of paths for which the resource versions will not be
     *                                               removed automatically when serializing, e.g.
     *                                               "Patient.managingOrganization" or "AuditEvent.object.reference".
     *                                               Note that only resource name and field names with dots separating
     *                                               is allowed here (no repetition indicators, FluentPath expressions,
     *                                               etc.). Set to <code>null</code> to use the value set in the parser
     *                                               options
     */
    public void setDontStripVersionsFromReferencesAtPaths(String dontStripVersionsFromReferencesAtPaths) {
        this.dontStripVersionsFromReferencesAtPaths = dontStripVersionsFromReferencesAtPaths;
    }

    public String getContentTypeHeader() {
        return contentTypeHeader;
    }

    public void setContentTypeHeader(String contentTypeHeader) {
        this.contentTypeHeader = contentTypeHeader;
    }

    /**
     * {@code AbstractBuilder} is the base builder for {@link FhirDataformat}.
     */
    @XmlTransient
    @SuppressWarnings("unchecked")
    abstract static class AbstractBuilder<T extends AbstractBuilder<T, F>, F extends FhirDataformat>
            implements DataFormatBuilder<F> {

        private String fhirContext;
        private String fhirVersion;
        private String prettyPrint;
        private String parserErrorHandler;
        private String parserOptions;
        private String preferTypes;
        private String forceResourceId;
        private String serverBaseUrl;
        private String omitResourceId;
        private String encodeElementsAppliesToChildResourcesOnly;
        private String encodeElements;
        private String dontEncodeElements;
        private String stripVersionsFromReferences;
        private String overrideResourceIdWithBundleEntryFullUrl;
        private String summaryMode;
        private String suppressNarratives;
        private String dontStripVersionsFromReferencesAtPaths;
        private String contentTypeHeader;

        /**
         * To use a custom fhir context.
         *
         * Reference to object of type ca.uhn.fhir.context.FhirContext
         */
        public T fhirContext(String fhirContext) {
            this.fhirContext = fhirContext;
            return (T) this;
        }

        /**
         * The version of FHIR to use. Possible values are: DSTU2,DSTU2_HL7ORG,DSTU2_1,DSTU3,R4,R5
         */
        public T fhirVersion(String fhirVersion) {
            this.fhirVersion = fhirVersion;
            return (T) this;
        }

        /**
         * Sets the "pretty print" flag, meaning that the parser will encode resources with human-readable spacing and
         * newlines between elements instead of condensing output as much as possible.
         *
         * @param prettyPrint The flag
         */
        public T prettyPrint(String prettyPrint) {
            this.prettyPrint = prettyPrint;
            return (T) this;
        }

        /**
         * Sets the "pretty print" flag, meaning that the parser will encode resources with human-readable spacing and
         * newlines between elements instead of condensing output as much as possible.
         *
         * @param prettyPrint The flag
         */
        public T prettyPrint(boolean prettyPrint) {
            this.prettyPrint = Boolean.toString(prettyPrint);
            return (T) this;
        }

        /**
         * Registers an error handler which will be invoked when any parse errors are found.
         *
         * Reference to object of type ca.uhn.fhir.parser.IParserErrorHandler
         */
        public T parserErrorHandler(String parserErrorHandler) {
            this.parserErrorHandler = parserErrorHandler;
            return (T) this;
        }

        /**
         * Sets the parser options object which will be used to supply default options to newly created parsers.
         *
         * Reference to object of type ca.uhn.fhir.context.ParserOptions
         */
        public T parserOptions(String parserOptions) {
            this.parserOptions = parserOptions;
            return (T) this;
        }

        /**
         * If set, when parsing resources the parser will try to use the given types when possible, in the order that
         * they are provided (from highest to lowest priority). For example, if a custom type which declares to
         * implement the Patient resource is passed in here, and the parser is parsing a Bundle containing a Patient
         * resource, the parser will use the given custom type.
         *
         * @param preferTypes The preferred types, or <code>null</code>
         */
        public T preferTypes(String preferTypes) {
            this.preferTypes = preferTypes;
            return (T) this;
        }

        /**
         * When encoding, force this resource ID to be encoded as the resource ID
         *
         * Reference to object of type org.hl7.fhir.instance.model.api.IIdType
         */
        public T forceResourceId(String forceResourceId) {
            this.forceResourceId = forceResourceId;
            return (T) this;
        }

        /**
         * Sets the server's base URL used by this parser. If a value is set, resource references will be turned into
         * relative references if they are provided as absolute URLs but have a base matching the given base.
         *
         * @param serverBaseUrl The base URL, e.g. "http://example.com/base"
         */
        public T serverBaseUrl(String serverBaseUrl) {
            this.serverBaseUrl = serverBaseUrl;
            return (T) this;
        }

        /**
         * If set to <code>true</code> (default is <code>false</code>) the ID of any resources being encoded will not be
         * included in the output. Note that this does not apply to contained resources, only to root resources. In
         * other words, if this is set to <code>true</code>, contained resources will still have local IDs but the
         * outer/containing ID will not have an ID.
         *
         * @param omitResourceId Should resource IDs be omitted
         */
        public T omitResourceId(String omitResourceId) {
            this.omitResourceId = omitResourceId;
            return (T) this;
        }

        /**
         * If set to <code>true</code> (default is <code>false</code>) the ID of any resources being encoded will not be
         * included in the output. Note that this does not apply to contained resources, only to root resources. In
         * other words, if this is set to <code>true</code>, contained resources will still have local IDs but the
         * outer/containing ID will not have an ID.
         *
         * @param omitResourceId Should resource IDs be omitted
         */
        public T omitResourceId(boolean omitResourceId) {
            this.omitResourceId = Boolean.toString(omitResourceId);
            return (T) this;
        }

        /**
         * If set to <code>true</code> (default is false), the values supplied to {@link #setEncodeElements(Set)} will
         * not be applied to the root resource (typically a Bundle), but will be applied to any sub-resources contained
         * within it (i.e. search result resources in that bundle)
         */
        public T encodeElementsAppliesToChildResourcesOnly(String encodeElementsAppliesToChildResourcesOnly) {
            this.encodeElementsAppliesToChildResourcesOnly = encodeElementsAppliesToChildResourcesOnly;
            return (T) this;
        }

        /**
         * If set to <code>true</code> (default is false), the values supplied to {@link #setEncodeElements(Set)} will
         * not be applied to the root resource (typically a Bundle), but will be applied to any sub-resources contained
         * within it (i.e. search result resources in that bundle)
         */
        public T encodeElementsAppliesToChildResourcesOnly(boolean encodeElementsAppliesToChildResourcesOnly) {
            this.encodeElementsAppliesToChildResourcesOnly = Boolean.toString(encodeElementsAppliesToChildResourcesOnly);
            return (T) this;
        }

        /**
         * If provided, specifies the elements which should be encoded, to the exclusion of all others. Valid values for
         * this field would include:
         * <ul>
         * <li><b>Patient</b> - Encode patient and all its children</li>
         * <li><b>Patient.name</b> - Encode only the patient's name</li>
         * <li><b>Patient.name.family</b> - Encode only the patient's family name</li>
         * <li><b>*.text</b> - Encode the text element on any resource (only the very first position may contain a
         * wildcard)</li>
         * <li><b>*.(mandatory)</b> - This is a special case which causes any mandatory fields (min > 0) to be
         * encoded</li>
         * </ul>
         *
         * @param encodeElements The elements to encode
         * @see                  #setDontEncodeElements(Set)
         */
        public T encodeElements(Set<String> encodeElements) {
            this.encodeElements = String.join(",", encodeElements);
            return (T) this;
        }

        /**
         * If provided, specifies the elements which should be encoded, to the exclusion of all others. Multiple
         * elements can be separated by comma when using String parameter.
         *
         * Valid values for this field would include:
         * <ul>
         * <li><b>Patient</b> - Encode patient and all its children</li>
         * <li><b>Patient.name</b> - Encode only the patient's name</li>
         * <li><b>Patient.name.family</b> - Encode only the patient's family name</li>
         * <li><b>*.text</b> - Encode the text element on any resource (only the very first position may contain a
         * wildcard)</li>
         * <li><b>*.(mandatory)</b> - This is a special case which causes any mandatory fields (min > 0) to be
         * encoded</li>
         * </ul>
         * Multiple elements can be separated by comma.
         *
         * @param encodeElements The elements to encode (multiple elements can be separated by comma)
         * @see                  #setDontEncodeElements(Set)
         */
        public T encodeElements(String encodeElements) {
            this.encodeElements = encodeElements;
            return (T) this;
        }

        /**
         * If provided, specifies the elements which should NOT be encoded. Multiple elements can be separated by comma
         * when using String parameter.
         *
         * Valid values for this field would include:
         * <ul>
         * <li><b>Patient</b> - Don't encode patient and all its children</li>
         * <li><b>Patient.name</b> - Don't encode the patient's name</li>
         * <li><b>Patient.name.family</b> - Don't encode the patient's family name</li>
         * <li><b>*.text</b> - Don't encode the text element on any resource (only the very first position may contain a
         * wildcard)</li>
         * </ul>
         * <p>
         * DSTU2 note: Note that values including meta, such as <code>Patient.meta</code> will work for DSTU2 parsers,
         * but values with subelements on meta such as <code>Patient.meta.lastUpdated</code> will only work in DSTU3+
         * mode.
         * </p>
         *
         * @param dontEncodeElements The elements to NOT encode (multiple elements can be separated by comma)
         * @see                      #setEncodeElements(Set)
         */
        public T dontEncodeElements(Set<String> dontEncodeElements) {
            this.dontEncodeElements = String.join(",", dontEncodeElements);
            return (T) this;
        }

        /**
         * If provided, specifies the elements which should NOT be encoded. Multiple elements can be separated by comma
         * when using String parameter.
         *
         * Valid values for this field would include:
         * <ul>
         * <li><b>Patient</b> - Don't encode patient and all its children</li>
         * <li><b>Patient.name</b> - Don't encode the patient's name</li>
         * <li><b>Patient.name.family</b> - Don't encode the patient's family name</li>
         * <li><b>*.text</b> - Don't encode the text element on any resource (only the very first position may contain a
         * wildcard)</li>
         * </ul>
         * <p>
         * DSTU2 note: Note that values including meta, such as <code>Patient.meta</code> will work for DSTU2 parsers,
         * but values with subelements on meta such as <code>Patient.meta.lastUpdated</code> will only work in DSTU3+
         * mode.
         * </p>
         *
         * @param dontEncodeElements The elements to NOT encode
         * @see                      #setEncodeElements(Set)
         */
        public T dontEncodeElements(String dontEncodeElements) {
            this.dontEncodeElements = dontEncodeElements;
            return (T) this;
        }

        /**
         * If set to <code>true<code> (which is the default), resource references containing a version
         * will have the version removed when the resource is encoded. This is generally good behaviour because
         * in most situations, references from one resource to another should be to the resource by ID, not
         * by ID and version. In some cases though, it may be desirable to preserve the version in resource
         * links. In that case, this value should be set to <code>false</code>.
         * <p>
         * This method provides the ability to globally disable reference encoding. If finer-grained control is needed,
         * use {@link #setDontStripVersionsFromReferencesAtPaths(List)}
         * </p>
         *
         * @param stripVersionsFromReferences Set this to
         *                                    <code>false<code> to prevent the parser from removing resource versions
         *                                    from references (or <code>null</code> to apply the default setting from
         *                                    the parser options.
         */
        public T stripVersionsFromReferences(String stripVersionsFromReferences) {
            this.stripVersionsFromReferences = stripVersionsFromReferences;
            return (T) this;
        }

        /**
         * If set to <code>true<code> (which is the default), resource references containing a version
         * will have the version removed when the resource is encoded. This is generally good behaviour because
         * in most situations, references from one resource to another should be to the resource by ID, not
         * by ID and version. In some cases though, it may be desirable to preserve the version in resource
         * links. In that case, this value should be set to <code>false</code>.
         * <p>
         * This method provides the ability to globally disable reference encoding. If finer-grained control is needed,
         * use {@link #setDontStripVersionsFromReferencesAtPaths(List)}
         * </p>
         *
         * @param stripVersionsFromReferences Set this to
         *                                    <code>false<code> to prevent the parser from removing resource versions
         *                                    from references (or <code>null</code> to apply the default setting from
         *                                    the parser options.
         */
        public T stripVersionsFromReferences(boolean stripVersionsFromReferences) {
            this.stripVersionsFromReferences = Boolean.toString(stripVersionsFromReferences);
            return (T) this;
        }

        /**
         * If set to <code>true</code> (which is the default), the Bundle.entry.fullUrl will override the
         * Bundle.entry.resource's resource id if the fullUrl is defined. This behavior happens when parsing the source
         * data into a Bundle object. Set this to <code>false</code> if this is not the desired behavior (e.g. the
         * client code wishes to perform additional validation checks between the fullUrl and the resource id).
         *
         * @param overrideResourceIdWithBundleEntryFullUrl Set this to <code>false</code> to prevent the parser from
         *                                                 overriding resource ids with the Bundle.entry.fullUrl (or
         *                                                 <code>null</code> to apply the default setting from the
         *                                                 parser options.
         */
        public T overrideResourceIdWithBundleEntryFullUrl(String overrideResourceIdWithBundleEntryFullUrl) {
            this.overrideResourceIdWithBundleEntryFullUrl = overrideResourceIdWithBundleEntryFullUrl;
            return (T) this;
        }

        /**
         * If set to <code>true</code> (which is the default), the Bundle.entry.fullUrl will override the
         * Bundle.entry.resource's resource id if the fullUrl is defined. This behavior happens when parsing the source
         * data into a Bundle object. Set this to <code>false</code> if this is not the desired behavior (e.g. the
         * client code wishes to perform additional validation checks between the fullUrl and the resource id).
         *
         * @param overrideResourceIdWithBundleEntryFullUrl Set this to <code>false</code> to prevent the parser from
         *                                                 overriding resource ids with the Bundle.entry.fullUrl (or
         *                                                 <code>null</code> to apply the default setting from the
         *                                                 parser options.
         */
        public T overrideResourceIdWithBundleEntryFullUrl(boolean overrideResourceIdWithBundleEntryFullUrl) {
            this.overrideResourceIdWithBundleEntryFullUrl = Boolean.toString(overrideResourceIdWithBundleEntryFullUrl);
            return (T) this;
        }

        /**
         * If set to <code>true</code> (default is <code>false</code>) only elements marked by the FHIR specification as
         * being "summary elements" will be included.
         */
        public T summaryMode(String summaryMode) {
            this.summaryMode = summaryMode;
            return (T) this;
        }

        /**
         * If set to <code>true</code> (default is <code>false</code>) only elements marked by the FHIR specification as
         * being "summary elements" will be included.
         */
        public T summaryMode(boolean summaryMode) {
            this.summaryMode = Boolean.toString(summaryMode);
            return (T) this;
        }

        /**
         * If set to <code>true</code> (default is <code>false</code>), narratives will not be included in the encoded
         * values.
         */
        public T suppressNarratives(String suppressNarratives) {
            this.suppressNarratives = suppressNarratives;
            return (T) this;
        }

        /**
         * If set to <code>true</code> (default is <code>false</code>), narratives will not be included in the encoded
         * values.
         */
        public T suppressNarratives(boolean suppressNarratives) {
            this.suppressNarratives = Boolean.toString(suppressNarratives);
            return (T) this;
        }

        /**
         * If supplied value(s), any resource references at the specified paths will have their resource versions
         * encoded instead of being automatically stripped during the encoding process. This setting has no effect on
         * the parsing process. Multiple elements can be separated by comma when using String parameter.
         * <p>
         * This method provides a finer-grained level of control than {@link #setStripVersionsFromReferences(String)}
         * and any paths specified by this method will be encoded even if
         * {@link #setStripVersionsFromReferences(String)} has been set to <code>true</code> (which is the default)
         * </p>
         *
         * @param dontStripVersionsFromReferencesAtPaths A collection of paths for which the resource versions will not
         *                                               be removed automatically when serializing, e.g.
         *                                               "Patient.managingOrganization" or
         *                                               "AuditEvent.object.reference". Note that only resource name and
         *                                               field names with dots separating is allowed here (no repetition
         *                                               indicators, FluentPath expressions, etc.). Set to
         *                                               <code>null</code> to use the value set in the parser options.
         */
        public T dontStripVersionsFromReferencesAtPaths(List<String> dontStripVersionsFromReferencesAtPaths) {
            this.dontStripVersionsFromReferencesAtPaths = String.join(",", dontStripVersionsFromReferencesAtPaths);
            return (T) this;
        }

        /**
         * If supplied value(s), any resource references at the specified paths will have their resource versions
         * encoded instead of being automatically stripped during the encoding process. This setting has no effect on
         * the parsing process. Multiple elements can be separated by comma when using String parameter.
         * <p>
         * This method provides a finer-grained level of control than {@link #setStripVersionsFromReferences(String)}
         * and any paths specified by this method will be encoded even if
         * {@link #setStripVersionsFromReferences(String)} has been set to <code>true</code> (which is the default)
         * </p>
         *
         * @param dontStripVersionsFromReferencesAtPaths A collection of paths for which the resource versions will not
         *                                               be removed automatically when serializing, e.g.
         *                                               "Patient.managingOrganization" or
         *                                               "AuditEvent.object.reference". Note that only resource name and
         *                                               field names with dots separating is allowed here (no repetition
         *                                               indicators, FluentPath expressions, etc.). Set to
         *                                               <code>null</code> to use the value set in the parser options.
         */
        public T dontStripVersionsFromReferencesAtPaths(String dontStripVersionsFromReferencesAtPaths) {
            this.dontStripVersionsFromReferencesAtPaths = dontStripVersionsFromReferencesAtPaths;
            return (T) this;
        }

        public T contentTypeHeader(String contentTypeHeader) {
            this.contentTypeHeader = contentTypeHeader;
            return (T) this;
        }

        public T contentTypeHeader(boolean contentTypeHeader) {
            this.contentTypeHeader = Boolean.toString(contentTypeHeader);
            return (T) this;
        }
    }
}
