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
import com.wordnik.swagger.model._
import com.wordnik.swagger.core.util.ModelUtil
import com.wordnik.swagger.core.SwaggerSpec

import org.apache.camel.model.rest.{RestOperationResponseMsgDefinition, RestOperationParamDefinition, VerbDefinition, RestDefinition}
import org.apache.camel.util.FileUtil
import org.slf4j.LoggerFactory

import scala.collection.mutable.ListBuffer
import com.wordnik.swagger.model.Parameter
import com.wordnik.swagger.model.ApiDescription
import com.wordnik.swagger.model.Operation
import com.wordnik.swagger.model.ApiListing

// to iterate Java list using for loop
import scala.collection.JavaConverters._

/**
 * A Swagger reader that reads the Camel's Rest models and builds a Swagger ApiListing models.
 */
class RestSwaggerReader {

  private val LOG = LoggerFactory.getLogger(classOf[RestSwaggerReader])

  def read(rest: RestDefinition, config: SwaggerConfig): Option[ApiListing] = {

    var resourcePath = rest.getPath
    // resource path must start with slash
    if (resourcePath == null) {
      resourcePath = ""
    }
    if (!resourcePath.startsWith("/")) {
      resourcePath = "/" + resourcePath
    }

    LOG.debug("Reading rest path: " + resourcePath + " -> " + rest)

    // create a list of apis
    val apis = new ListBuffer[ApiDescription]

    // used during gathering of apis
    val operations = new ListBuffer[Operation]
    var path: String = null

    var list = rest.getVerbs.asScala
    // must sort the verbs by uri so we group them together when an uri has multiple operations
    list = list.sorted(VerbOrdering)

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

      // the method must be in upper case
      var method = verb.asVerb().toUpperCase(Locale.US)

      // create an unique nickname using the method and paths
      var nickName = createNickname(verb.asVerb(), buildUrl(resourcePath, path))

      var responseType = verb.getOutType match {
        case e: String if e.endsWith("[]") => "List[" + e.substring(0, e.length - 2) + "]"
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

      var summary = verb.getDescriptionText
      if (summary == null) {
        summary = ""
      }

      LOG.debug("Adding operation " + method + " " + nickName)

      operations += Operation(
        method,
        summary,
        "",
        responseType,
        nickName,
        0,
        produces,
        consumes,
        List(),
        List(),
        createParameters(verb),
        createResponseMessages(verb),
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

      LOG.debug("Adding APIs with {} models", models.size)

      var desc = rest.getDescriptionText
      if (desc == null) {
        desc = ""
      }

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
          models,
          Option(desc))
      )
    }

    else None
  }

  def createResponseMessages(verb: VerbDefinition): List[ResponseMessage] = {
    val responseMsgs = new ListBuffer[ResponseMessage]

    for (param:RestOperationResponseMsgDefinition <- verb.getResponseMsgs.asScala) {
      responseMsgs += ResponseMessage(
        param.getCode.asInstanceOf[Integer],
        param.getMessage,
        Option( param.getResponseModel )
      )
    }

    responseMsgs.toList
  }

  def createParameters(verb: VerbDefinition): List[Parameter] = {
    val parameters = new ListBuffer[Parameter]

    for (param:RestOperationParamDefinition <- verb.getParams.asScala) {
      var allowValues=AnyAllowableValues

      if(!param.getAllowableValues.isEmpty){
        AllowableListValues(param.getAllowableValues.asScala.toList)
      }

      parameters += Parameter(
        param.getName,
        Some( param.getDescription ),
        Some( param.getDefaultValue),
        if (param.getRequired != null) param.getRequired.booleanValue() else false,
        false,
        param.getDataType,
        allowValues,
        param.getType.toString,
        Some(param.getAccess)
      )
    }

    parameters.toList
  }

  def createNickname(method: String, absPath : String): String = {
    val s = method + "/" + absPath
    val arr = s.split("\\/")
    val r = arr.foldLeft("") {
      (a, b) => a + toTitleCase(sanitizeNickname(b))
    }
    // first char should be lower
    r.charAt(0).toLower + r.substring(1)
  }

  def toTitleCase(s: String): String = {
    if (s.size > 0) {
      s.charAt(0).toUpper + s.substring(1)
    } else {
      s
    }
  }

  def sanitizeNickname(s: String): String = {
    // nick name must only be alpha chars
    s.replaceAll("\\W", "")
  }

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

  /**
   * To sort the rest operations
   */
  object VerbOrdering extends Ordering[VerbDefinition] {
    def compare(a:VerbDefinition, b:VerbDefinition) = {
      var u1 = ""
      if (a.getUri != null) {
        // replace { with _ which comes before a when soring by char
        u1 = a.getUri.replace("{", "_")
      }
      var u2 = ""
      if (b.getUri != null) {
        // replace { with _ which comes before a when soring by char
        u2 = b.getUri.replace("{", "_")
      }

      var num = u1.compareTo(u2)
      if (num == 0) {
        // same uri, so use http method as sorting
        num = a.asVerb().compareTo(b.asVerb())
      }
      num
    }
  }

}
