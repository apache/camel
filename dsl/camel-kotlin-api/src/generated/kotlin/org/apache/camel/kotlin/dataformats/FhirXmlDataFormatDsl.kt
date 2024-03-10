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
package org.apache.camel.kotlin.dataformats

import kotlin.Boolean
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.DataFormatDsl
import org.apache.camel.model.dataformat.FhirXmlDataFormat

/**
 * Marshall and unmarshall FHIR objects to/from XML.
 */
public fun DataFormatDsl.fhirXml(i: FhirXmlDataFormatDsl.() -> Unit) {
  def = FhirXmlDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class FhirXmlDataFormatDsl {
  public val def: FhirXmlDataFormat

  init {
    def = FhirXmlDataFormat()}

  /**
   * The id of this node
   */
  public fun id(id: String) {
    def.id = id
  }

  /**
   * The version of FHIR to use. Possible values are: DSTU2,DSTU2_HL7ORG,DSTU2_1,DSTU3,R4,R5
   */
  public fun fhirVersion(fhirVersion: String) {
    def.fhirVersion = fhirVersion
  }

  /**
   * To use a custom fhir context. Reference to object of type ca.uhn.fhir.context.FhirContext
   */
  public fun fhirContext(fhirContext: String) {
    def.fhirContext = fhirContext
  }

  /**
   * Sets the pretty print flag, meaning that the parser will encode resources with human-readable
   * spacing and newlines between elements instead of condensing output as much as possible.
   */
  public fun prettyPrint(prettyPrint: Boolean) {
    def.prettyPrint = prettyPrint.toString()
  }

  /**
   * Sets the pretty print flag, meaning that the parser will encode resources with human-readable
   * spacing and newlines between elements instead of condensing output as much as possible.
   */
  public fun prettyPrint(prettyPrint: String) {
    def.prettyPrint = prettyPrint
  }

  /**
   * Registers an error handler which will be invoked when any parse errors are found. Reference to
   * object of type ca.uhn.fhir.parser.IParserErrorHandler
   */
  public fun parserErrorHandler(parserErrorHandler: String) {
    def.parserErrorHandler = parserErrorHandler
  }

  /**
   * Sets the parser options object which will be used to supply default options to newly created
   * parsers. Reference to object of type ca.uhn.fhir.context.ParserOptions.
   */
  public fun parserOptions(parserOptions: String) {
    def.parserOptions = parserOptions
  }

  /**
   * If set (FQN class names), when parsing resources the parser will try to use the given types
   * when possible, in the order that they are provided (from highest to lowest priority). For example,
   * if a custom type which declares to implement the Patient resource is passed in here, and the
   * parser is parsing a Bundle containing a Patient resource, the parser will use the given custom
   * type. Multiple class names can be separated by comma.
   */
  public fun preferTypes(preferTypes: String) {
    def.preferTypes = preferTypes
  }

  /**
   * When encoding, force this resource ID to be encoded as the resource ID. Reference to object of
   * type org.hl7.fhir.instance.model.api.IIdType
   */
  public fun forceResourceId(forceResourceId: String) {
    def.forceResourceId = forceResourceId
  }

  /**
   * Sets the server's base URL used by this parser. If a value is set, resource references will be
   * turned into relative references if they are provided as absolute URLs but have a base matching the
   * given base.
   */
  public fun serverBaseUrl(serverBaseUrl: String) {
    def.serverBaseUrl = serverBaseUrl
  }

  /**
   * If set to true (default is false) the ID of any resources being encoded will not be included in
   * the output. Note that this does not apply to contained resources, only to root resources. In other
   * words, if this is set to true, contained resources will still have local IDs but the
   * outer/containing ID will not have an ID.
   */
  public fun omitResourceId(omitResourceId: Boolean) {
    def.omitResourceId = omitResourceId.toString()
  }

  /**
   * If set to true (default is false) the ID of any resources being encoded will not be included in
   * the output. Note that this does not apply to contained resources, only to root resources. In other
   * words, if this is set to true, contained resources will still have local IDs but the
   * outer/containing ID will not have an ID.
   */
  public fun omitResourceId(omitResourceId: String) {
    def.omitResourceId = omitResourceId
  }

  /**
   * If set to true (default is false), the values supplied to setEncodeElements(Set) will not be
   * applied to the root resource (typically a Bundle), but will be applied to any sub-resources
   * contained within it (i.e. search result resources in that bundle)
   */
  public
      fun encodeElementsAppliesToChildResourcesOnly(encodeElementsAppliesToChildResourcesOnly: Boolean) {
    def.encodeElementsAppliesToChildResourcesOnly =
        encodeElementsAppliesToChildResourcesOnly.toString()
  }

  /**
   * If set to true (default is false), the values supplied to setEncodeElements(Set) will not be
   * applied to the root resource (typically a Bundle), but will be applied to any sub-resources
   * contained within it (i.e. search result resources in that bundle)
   */
  public
      fun encodeElementsAppliesToChildResourcesOnly(encodeElementsAppliesToChildResourcesOnly: String) {
    def.encodeElementsAppliesToChildResourcesOnly = encodeElementsAppliesToChildResourcesOnly
  }

  /**
   * If provided, specifies the elements which should be encoded, to the exclusion of all others.
   * Multiple elements can be separated by comma when using String parameter. Valid values for this
   * field would include: Patient - Encode patient and all its children Patient.name - Encode only the
   * patient's name Patient.name.family - Encode only the patient's family name .text - Encode the text
   * element on any resource (only the very first position may contain a wildcard) .(mandatory) - This
   * is a special case which causes any mandatory fields (min 0) to be encoded
   */
  public fun encodeElements(encodeElements: String) {
    def.encodeElements = encodeElements
  }

  /**
   * If provided, specifies the elements which should NOT be encoded. Multiple elements can be
   * separated by comma when using String parameter. Valid values for this field would include:
   * Patient - Don't encode patient and all its children Patient.name - Don't encode the patient's name
   * Patient.name.family - Don't encode the patient's family name .text - Don't encode the text element
   * on any resource (only the very first position may contain a wildcard) DSTU2 note: Note that values
   * including meta, such as Patient.meta will work for DSTU2 parsers, but values with subelements on
   * meta such as Patient.meta.lastUpdated will only work in DSTU3 mode.
   */
  public fun dontEncodeElements(dontEncodeElements: String) {
    def.dontEncodeElements = dontEncodeElements
  }

  /**
   * If set to true (which is the default), resource references containing a version will have the
   * version removed when the resource is encoded. This is generally good behaviour because in most
   * situations, references from one resource to another should be to the resource by ID, not by ID and
   * version. In some cases though, it may be desirable to preserve the version in resource links. In
   * that case, this value should be set to false. This method provides the ability to globally disable
   * reference encoding. If finer-grained control is needed, use
   * setDontStripVersionsFromReferencesAtPaths(List)
   */
  public fun stripVersionsFromReferences(stripVersionsFromReferences: Boolean) {
    def.stripVersionsFromReferences = stripVersionsFromReferences.toString()
  }

  /**
   * If set to true (which is the default), resource references containing a version will have the
   * version removed when the resource is encoded. This is generally good behaviour because in most
   * situations, references from one resource to another should be to the resource by ID, not by ID and
   * version. In some cases though, it may be desirable to preserve the version in resource links. In
   * that case, this value should be set to false. This method provides the ability to globally disable
   * reference encoding. If finer-grained control is needed, use
   * setDontStripVersionsFromReferencesAtPaths(List)
   */
  public fun stripVersionsFromReferences(stripVersionsFromReferences: String) {
    def.stripVersionsFromReferences = stripVersionsFromReferences
  }

  /**
   * If set to true (which is the default), the Bundle.entry.fullUrl will override the
   * Bundle.entry.resource's resource id if the fullUrl is defined. This behavior happens when parsing
   * the source data into a Bundle object. Set this to false if this is not the desired behavior (e.g.
   * the client code wishes to perform additional validation checks between the fullUrl and the
   * resource id).
   */
  public
      fun overrideResourceIdWithBundleEntryFullUrl(overrideResourceIdWithBundleEntryFullUrl: Boolean) {
    def.overrideResourceIdWithBundleEntryFullUrl =
        overrideResourceIdWithBundleEntryFullUrl.toString()
  }

  /**
   * If set to true (which is the default), the Bundle.entry.fullUrl will override the
   * Bundle.entry.resource's resource id if the fullUrl is defined. This behavior happens when parsing
   * the source data into a Bundle object. Set this to false if this is not the desired behavior (e.g.
   * the client code wishes to perform additional validation checks between the fullUrl and the
   * resource id).
   */
  public
      fun overrideResourceIdWithBundleEntryFullUrl(overrideResourceIdWithBundleEntryFullUrl: String) {
    def.overrideResourceIdWithBundleEntryFullUrl = overrideResourceIdWithBundleEntryFullUrl
  }

  /**
   * If set to true (default is false) only elements marked by the FHIR specification as being
   * summary elements will be included.
   */
  public fun summaryMode(summaryMode: Boolean) {
    def.summaryMode = summaryMode.toString()
  }

  /**
   * If set to true (default is false) only elements marked by the FHIR specification as being
   * summary elements will be included.
   */
  public fun summaryMode(summaryMode: String) {
    def.summaryMode = summaryMode
  }

  /**
   * If set to true (default is false), narratives will not be included in the encoded values.
   */
  public fun suppressNarratives(suppressNarratives: Boolean) {
    def.suppressNarratives = suppressNarratives.toString()
  }

  /**
   * If set to true (default is false), narratives will not be included in the encoded values.
   */
  public fun suppressNarratives(suppressNarratives: String) {
    def.suppressNarratives = suppressNarratives
  }

  /**
   * If supplied value(s), any resource references at the specified paths will have their resource
   * versions encoded instead of being automatically stripped during the encoding process. This setting
   * has no effect on the parsing process. Multiple elements can be separated by comma when using
   * String parameter. This method provides a finer-grained level of control than
   * setStripVersionsFromReferences(String) and any paths specified by this method will be encoded even
   * if setStripVersionsFromReferences(String) has been set to true (which is the default)
   */
  public
      fun dontStripVersionsFromReferencesAtPaths(dontStripVersionsFromReferencesAtPaths: String) {
    def.dontStripVersionsFromReferencesAtPaths = dontStripVersionsFromReferencesAtPaths
  }

  /**
   * Whether the data format should set the Content-Type header with the type from the data format.
   * For example application/xml for data formats marshalling to XML, or application/json for data
   * formats marshalling to JSON
   */
  public fun contentTypeHeader(contentTypeHeader: Boolean) {
    def.contentTypeHeader = contentTypeHeader.toString()
  }

  /**
   * Whether the data format should set the Content-Type header with the type from the data format.
   * For example application/xml for data formats marshalling to XML, or application/json for data
   * formats marshalling to JSON
   */
  public fun contentTypeHeader(contentTypeHeader: String) {
    def.contentTypeHeader = contentTypeHeader
  }
}
