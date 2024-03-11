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
import org.apache.camel.model.dataformat.CBORDataFormat

/**
 * Unmarshal a CBOR payload to POJO and back.
 */
public fun DataFormatDsl.cbor(i: CborDataFormatDsl.() -> Unit) {
  def = CborDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class CborDataFormatDsl {
  public val def: CBORDataFormat

  init {
    def = CBORDataFormat()}

  /**
   * The id of this node
   */
  public fun id(id: String) {
    def.id = id
  }

  /**
   * Lookup and use the existing CBOR ObjectMapper with the given id when using Jackson.
   */
  public fun objectMapper(objectMapper: String) {
    def.objectMapper = objectMapper
  }

  /**
   * Whether to lookup and use default Jackson CBOR ObjectMapper from the registry.
   */
  public fun useDefaultObjectMapper(useDefaultObjectMapper: Boolean) {
    def.useDefaultObjectMapper = useDefaultObjectMapper.toString()
  }

  /**
   * Whether to lookup and use default Jackson CBOR ObjectMapper from the registry.
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
   * If enabled then Jackson CBOR is allowed to attempt to use the CamelCBORUnmarshalType header
   * during the unmarshalling. This should only be enabled when desired to be used.
   */
  public fun allowUnmarshallType(allowUnmarshallType: Boolean) {
    def.allowUnmarshallType = allowUnmarshallType.toString()
  }

  /**
   * If enabled then Jackson CBOR is allowed to attempt to use the CamelCBORUnmarshalType header
   * during the unmarshalling. This should only be enabled when desired to be used.
   */
  public fun allowUnmarshallType(allowUnmarshallType: String) {
    def.allowUnmarshallType = allowUnmarshallType
  }

  /**
   * To enable pretty printing output nicely formatted. Is by default false.
   */
  public fun prettyPrint(prettyPrint: Boolean) {
    def.prettyPrint = prettyPrint.toString()
  }

  /**
   * To enable pretty printing output nicely formatted. Is by default false.
   */
  public fun prettyPrint(prettyPrint: String) {
    def.prettyPrint = prettyPrint
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
}
