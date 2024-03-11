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

import java.lang.Class
import kotlin.Boolean
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.DataFormatDsl
import org.apache.camel.model.dataformat.AvroDataFormat

/**
 * Marshal POJOs to Avro and back using Jackson.
 */
public fun DataFormatDsl.avroJackson(i: AvroJacksonDataFormatDsl.() -> Unit) {
  def = AvroJacksonDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class AvroJacksonDataFormatDsl {
  public val def: AvroDataFormat

  init {
    def = AvroDataFormat()}

  /**
   * The id of this node
   */
  public fun id(id: String) {
    def.id = id
  }

  /**
   * Lookup and use the existing ObjectMapper with the given id when using Jackson.
   */
  public fun objectMapper(objectMapper: String) {
    def.objectMapper = objectMapper
  }

  /**
   * Whether to lookup and use default Jackson ObjectMapper from the registry.
   */
  public fun useDefaultObjectMapper(useDefaultObjectMapper: Boolean) {
    def.useDefaultObjectMapper = useDefaultObjectMapper.toString()
  }

  /**
   * Whether to lookup and use default Jackson ObjectMapper from the registry.
   */
  public fun useDefaultObjectMapper(useDefaultObjectMapper: String) {
    def.useDefaultObjectMapper = useDefaultObjectMapper
  }

  /**
   * Class name of the java type to use when unmarshalling
   */
  public fun unmarshalType(unmarshalType: Class<*>) {
    def.unmarshalType = unmarshalType
  }

  /**
   * When marshalling a POJO to JSON you might want to exclude certain fields from the JSON output.
   * With Jackson you can use JSON views to accomplish this. This option is to refer to the class which
   * has JsonView annotations
   */
  public fun jsonView(jsonView: Class<*>) {
    def.jsonView = jsonView
  }

  /**
   * If you want to marshal a pojo to JSON, and the pojo has some fields with null values. And you
   * want to skip these null values, you can set this option to NON_NULL
   */
  public fun include(include: String) {
    def.include = include
  }

  /**
   * Used for JMS users to allow the JMSType header from the JMS spec to specify a FQN classname to
   * use to unmarshal to.
   */
  public fun allowJmsType(allowJmsType: Boolean) {
    def.allowJmsType = allowJmsType.toString()
  }

  /**
   * Used for JMS users to allow the JMSType header from the JMS spec to specify a FQN classname to
   * use to unmarshal to.
   */
  public fun allowJmsType(allowJmsType: String) {
    def.allowJmsType = allowJmsType
  }

  /**
   * Refers to a custom collection type to lookup in the registry to use. This option should rarely
   * be used, but allows to use different collection types than java.util.Collection based as default.
   */
  public fun collectionType(collectionType: Class<*>) {
    def.collectionType = collectionType
  }

  /**
   * To unmarshal to a List of Map or a List of Pojo.
   */
  public fun useList(useList: Boolean) {
    def.useList = useList.toString()
  }

  /**
   * To unmarshal to a List of Map or a List of Pojo.
   */
  public fun useList(useList: String) {
    def.useList = useList
  }

  /**
   * To use custom Jackson modules com.fasterxml.jackson.databind.Module specified as a String with
   * FQN class names. Multiple classes can be separated by comma.
   */
  public fun moduleClassNames(moduleClassNames: String) {
    def.moduleClassNames = moduleClassNames
  }

  /**
   * To use custom Jackson modules referred from the Camel registry. Multiple modules can be
   * separated by comma.
   */
  public fun moduleRefs(moduleRefs: String) {
    def.moduleRefs = moduleRefs
  }

  /**
   * Set of features to enable on the Jackson com.fasterxml.jackson.databind.ObjectMapper. The
   * features should be a name that matches a enum from
   * com.fasterxml.jackson.databind.SerializationFeature,
   * com.fasterxml.jackson.databind.DeserializationFeature, or
   * com.fasterxml.jackson.databind.MapperFeature Multiple features can be separated by comma
   */
  public fun enableFeatures(enableFeatures: String) {
    def.enableFeatures = enableFeatures
  }

  /**
   * Set of features to disable on the Jackson com.fasterxml.jackson.databind.ObjectMapper. The
   * features should be a name that matches a enum from
   * com.fasterxml.jackson.databind.SerializationFeature,
   * com.fasterxml.jackson.databind.DeserializationFeature, or
   * com.fasterxml.jackson.databind.MapperFeature Multiple features can be separated by comma
   */
  public fun disableFeatures(disableFeatures: String) {
    def.disableFeatures = disableFeatures
  }

  /**
   * If enabled then Jackson is allowed to attempt to use the CamelJacksonUnmarshalType header
   * during the unmarshalling. This should only be enabled when desired to be used.
   */
  public fun allowUnmarshallType(allowUnmarshallType: Boolean) {
    def.allowUnmarshallType = allowUnmarshallType.toString()
  }

  /**
   * If enabled then Jackson is allowed to attempt to use the CamelJacksonUnmarshalType header
   * during the unmarshalling. This should only be enabled when desired to be used.
   */
  public fun allowUnmarshallType(allowUnmarshallType: String) {
    def.allowUnmarshallType = allowUnmarshallType
  }

  /**
   * If set then Jackson will use the Timezone when marshalling/unmarshalling.
   */
  public fun timezone(timezone: String) {
    def.timezone = timezone
  }

  /**
   * If set to true then Jackson will lookup for an objectMapper into the registry
   */
  public fun autoDiscoverObjectMapper(autoDiscoverObjectMapper: Boolean) {
    def.autoDiscoverObjectMapper = autoDiscoverObjectMapper.toString()
  }

  /**
   * If set to true then Jackson will lookup for an objectMapper into the registry
   */
  public fun autoDiscoverObjectMapper(autoDiscoverObjectMapper: String) {
    def.autoDiscoverObjectMapper = autoDiscoverObjectMapper
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

  /**
   * Optional schema resolver used to lookup schemas for the data in transit.
   */
  public fun schemaResolver(schemaResolver: String) {
    def.schemaResolver = schemaResolver
  }

  /**
   * When not disabled, the SchemaResolver will be looked up into the registry
   */
  public fun autoDiscoverSchemaResolver(autoDiscoverSchemaResolver: Boolean) {
    def.autoDiscoverSchemaResolver = autoDiscoverSchemaResolver.toString()
  }

  /**
   * When not disabled, the SchemaResolver will be looked up into the registry
   */
  public fun autoDiscoverSchemaResolver(autoDiscoverSchemaResolver: String) {
    def.autoDiscoverSchemaResolver = autoDiscoverSchemaResolver
  }
}
