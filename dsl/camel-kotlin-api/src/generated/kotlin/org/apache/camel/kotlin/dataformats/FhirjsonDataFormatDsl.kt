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
import org.apache.camel.model.dataformat.FhirJsonDataFormat

public fun DataFormatDsl.fhirJson(i: FhirjsonDataFormatDsl.() -> Unit) {
  def = FhirjsonDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class FhirjsonDataFormatDsl {
  public val def: FhirJsonDataFormat

  init {
    def = FhirJsonDataFormat()}

  public fun id(id: String) {
    def.id = id
  }

  public fun fhirVersion(fhirVersion: String) {
    def.fhirVersion = fhirVersion
  }

  public fun fhirContext(fhirContext: String) {
    def.fhirContext = fhirContext
  }

  public fun prettyPrint(prettyPrint: Boolean) {
    def.prettyPrint = prettyPrint.toString()
  }

  public fun prettyPrint(prettyPrint: String) {
    def.prettyPrint = prettyPrint
  }

  public fun parserErrorHandler(parserErrorHandler: String) {
    def.parserErrorHandler = parserErrorHandler
  }

  public fun parserOptions(parserOptions: String) {
    def.parserOptions = parserOptions
  }

  public fun preferTypes(preferTypes: String) {
    def.preferTypes = preferTypes
  }

  public fun forceResourceId(forceResourceId: String) {
    def.forceResourceId = forceResourceId
  }

  public fun serverBaseUrl(serverBaseUrl: String) {
    def.serverBaseUrl = serverBaseUrl
  }

  public fun omitResourceId(omitResourceId: Boolean) {
    def.omitResourceId = omitResourceId.toString()
  }

  public fun omitResourceId(omitResourceId: String) {
    def.omitResourceId = omitResourceId
  }

  public
      fun encodeElementsAppliesToChildResourcesOnly(encodeElementsAppliesToChildResourcesOnly: Boolean) {
    def.encodeElementsAppliesToChildResourcesOnly =
        encodeElementsAppliesToChildResourcesOnly.toString()
  }

  public
      fun encodeElementsAppliesToChildResourcesOnly(encodeElementsAppliesToChildResourcesOnly: String) {
    def.encodeElementsAppliesToChildResourcesOnly = encodeElementsAppliesToChildResourcesOnly
  }

  public fun encodeElements(encodeElements: String) {
    def.encodeElements = encodeElements
  }

  public fun dontEncodeElements(dontEncodeElements: String) {
    def.dontEncodeElements = dontEncodeElements
  }

  public fun stripVersionsFromReferences(stripVersionsFromReferences: Boolean) {
    def.stripVersionsFromReferences = stripVersionsFromReferences.toString()
  }

  public fun stripVersionsFromReferences(stripVersionsFromReferences: String) {
    def.stripVersionsFromReferences = stripVersionsFromReferences
  }

  public
      fun overrideResourceIdWithBundleEntryFullUrl(overrideResourceIdWithBundleEntryFullUrl: Boolean) {
    def.overrideResourceIdWithBundleEntryFullUrl =
        overrideResourceIdWithBundleEntryFullUrl.toString()
  }

  public
      fun overrideResourceIdWithBundleEntryFullUrl(overrideResourceIdWithBundleEntryFullUrl: String) {
    def.overrideResourceIdWithBundleEntryFullUrl = overrideResourceIdWithBundleEntryFullUrl
  }

  public fun summaryMode(summaryMode: Boolean) {
    def.summaryMode = summaryMode.toString()
  }

  public fun summaryMode(summaryMode: String) {
    def.summaryMode = summaryMode
  }

  public fun suppressNarratives(suppressNarratives: Boolean) {
    def.suppressNarratives = suppressNarratives.toString()
  }

  public fun suppressNarratives(suppressNarratives: String) {
    def.suppressNarratives = suppressNarratives
  }

  public
      fun dontStripVersionsFromReferencesAtPaths(dontStripVersionsFromReferencesAtPaths: String) {
    def.dontStripVersionsFromReferencesAtPaths = dontStripVersionsFromReferencesAtPaths
  }

  public fun contentTypeHeader(contentTypeHeader: Boolean) {
    def.contentTypeHeader = contentTypeHeader.toString()
  }

  public fun contentTypeHeader(contentTypeHeader: String) {
    def.contentTypeHeader = contentTypeHeader
  }
}
