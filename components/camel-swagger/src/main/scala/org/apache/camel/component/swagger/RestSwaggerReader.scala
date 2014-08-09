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

import java.util.Locale

import com.wordnik.swagger.config.SwaggerConfig
import com.wordnik.swagger.model.{ApiDescription, Operation, ApiListing}
import com.wordnik.swagger.core.util.ModelUtil
import com.wordnik.swagger.core.SwaggerSpec

import org.apache.camel.model.rest.{VerbDefinition, RestDefinition}
import org.apache.camel.util.FileUtil
 import org.slf4j.LoggerFactory

import scala.collection.mutable.ListBuffer

// to iterate Java list using for loop
import scala.collection.JavaConverters._

class RestSwaggerReader {

  private val LOG = LoggerFactory.getLogger(classOf[RestSwaggerReader])

  def buildUrl(path1: String, path2: String): String = {
    val s1 = FileUtil.stripTrailingSeparator(path1)
    val s2 = FileUtil.stripLeadingSeparator(path2)
    if (s1 != null && s2 != null) {
      s1 + "/" + s2
    } else if (path1 != null) {
      path1
    } else {
      path2
    }
  }

  // TOOD: register classloader

  def read(rest: RestDefinition, config: SwaggerConfig): Option[ApiListing] = {

    val resourcePath = rest.getPath

    // create a list of apis
    val apis = new ListBuffer[ApiDescription]

    // used during gathering of apis
    val operations = new ListBuffer[Operation]
    var path: String = null

    // must sort the verbs by uri so we group them together when an uri has multiple operations
    // TODO: we want to sort /{xx} first, so we may need some regexp matching to trigger sorting them before non {}
    // TODO: and then 2nd sort by http method
    var list = rest.getVerbs.asScala
    list = list.sortBy(v => v.getUri match {
      case v: Any => v
      case _ => ""
    })

    for (verb: VerbDefinition <- list) {

      if (verb.getUri != path && operations.size > 0) {
        // restart
        apis += ApiDescription(
          buildUrl(resourcePath, path),
          Some(""),
          operations.toList)
        operations.clear()
      }

      path = verb.getUri
      var method = verb.asVerb().toUpperCase(Locale.US)

      var responseType = verb.getOutType match {
        case e: String => e
        case _ => "java.lang.Void"
      }

      val produces = verb.getProduces match {
        case e: String if e != "" => e.split(",").map(_.trim).toList
        case _ => List()
      }

      val consumes = verb.getConsumes match {
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

    // add remainder
    if (operations.size > 0) {
      apis += ApiDescription(
        buildUrl(resourcePath, path),
        Some(""),
        operations.toList)
    }

    if (apis.size > 0) {

      val produces = rest.getProduces match {
        case e: String if e != "" => e.split(",").map(_.trim).toList
        case _ => List()
      }

      val consumes = rest.getConsumes match {
        case e: String if e != "" => e.split(",").map(_.trim).toList
        case _ => List()
      }

      val models = ModelUtil.modelsFromApis(apis.toList)
      Some(
        ApiListing(
          config.apiVersion,
          SwaggerSpec.version,
          config.basePath,
          resourcePath,
          produces,
          consumes,
          List(), // protocols
          List(), // authorizations
          ModelUtil.stripPackages(apis.toList),
          models)
      )
    }

    else None
  }

}
