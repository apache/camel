/**
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
package org.apache.camel.component.swagger

import org.apache.camel.model.rest.{VerbDefinition, RestDefinition}
import com.wordnik.swagger.config.SwaggerConfig
import com.wordnik.swagger.model.{ApiDescription, Operation, ApiListing}
import org.slf4j.LoggerFactory
import com.wordnik.swagger.core.util.ModelUtil
import com.wordnik.swagger.core.SwaggerSpec
import scala.collection.mutable.ListBuffer
import java.util.Locale

// to iterate using for loop
import scala.collection.JavaConverters._

class RestSwaggerReader {

  private val LOG = LoggerFactory.getLogger(classOf[RestSwaggerReader])

  def read(rest: RestDefinition, config: SwaggerConfig): Option[ApiListing] = {

    val api = rest.getPath
    if (api != null) {
      val fullPath = {
        if (api.startsWith("/")) api.substring(1)
        else api
      }
      val (resourcePath, subpath) = {
        if (fullPath.indexOf("/") > 0) {
          val pos = fullPath.indexOf("/")
          ("/" + fullPath.substring(0, pos), fullPath.substring(pos))
        }
        else ("/", fullPath)
      }

      LOG.debug("read routes from classes: %s, %s".format(resourcePath, subpath))

      val operations = new ListBuffer[Operation]

      val list = rest.getVerbs.asScala
      for (verb: VerbDefinition <- list) {

        var method = verb.asVerb().toUpperCase(Locale.US)

        var responseType = verb.getOutType match {
          case e: String => e
          case _ => "java.lang.Void"
        }

        var p = verb.getProduces
        if (p == null) {
          p = rest.getProduces
        }
        val produces = p match {
          case e: String if e != "" => e.split(",").map(_.trim).toList
          case _ => List()
        }

        var c = verb.getConsumes
        if (c == null) {
          c = rest.getConsumes
        }
        val consumes = c match {
          case e: String if e != "" => e.split(",").map(_.trim).toList
          case _ => List()
        }

        operations += Operation(
          method,
          "",
          "",
          responseType,
          "",
          0,
          produces,
          consumes,
          List(),
          List(),
          List(),
          List(),
          None)
      }

      if (operations.size > 0) {
        val apis = List(
          ApiDescription(
            "/" + fullPath,
            Some(""),
            operations.toList))
        val models = ModelUtil.modelsFromApis(apis)
        Some(
          ApiListing(
            config.apiVersion,
            SwaggerSpec.version,
            config.basePath,
            resourcePath,
            List(), // produces
            List(), // consumes
            List(), // protocols
            List(), // authorizations
            ModelUtil.stripPackages(apis),
            models)
        )
      }
      else None
    }
    else None
  }

}
