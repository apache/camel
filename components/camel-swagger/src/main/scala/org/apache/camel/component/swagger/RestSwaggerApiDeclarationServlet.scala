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

import java.net.URL
import javax.servlet.http.{HttpServlet, HttpServletResponse, HttpServletRequest}
import javax.servlet.ServletConfig

import com.wordnik.swagger.core.filter.SpecFilter
import com.wordnik.swagger.core.util.JsonSerializer
import com.wordnik.swagger.config.{SwaggerConfig, ConfigFactory, FilterFactory}
import com.wordnik.swagger.model.{ApiInfo, ResourceListing, ApiListingReference}

import org.apache.camel.model.rest.RestDefinition
import org.slf4j.LoggerFactory

import scala.collection.mutable

/**
 * A Http Servlet to expose the REST services as Swagger APIs.
 */
abstract class RestSwaggerApiDeclarationServlet extends HttpServlet {

  private val LOG = LoggerFactory.getLogger(classOf[RestSwaggerApiDeclarationServlet])

  val reader = new RestSwaggerReader()
  var camelId: String = null
  val swaggerConfig: SwaggerConfig = ConfigFactory.config
  var cors: Boolean = false
  var initDone: Boolean = false

  override def init(config: ServletConfig): Unit = {
    super.init(config)

    // configure swagger options
    var s = config.getInitParameter("api.version")
    if (s != null) {
      swaggerConfig.setApiVersion(s)
    }
    s = config.getInitParameter("swagger.version")
    if (s != null) {
      swaggerConfig.setSwaggerVersion(s)
    }
    s = config.getInitParameter("base.path")
    if (s != null) {
      swaggerConfig.setBasePath(s)
    }
    s = config.getInitParameter("api.path")
    if (s != null) {
      swaggerConfig.setApiPath(s)
    }
    s = config.getInitParameter("cors")
    if (s != null) {
      cors = "true".equalsIgnoreCase(s)
    }
    s = config.getInitParameter("camelId")
    if (s != null) {
      camelId = s
    }

    val title = config.getInitParameter("api.title")
    val description = config.getInitParameter("api.description")
    val termsOfServiceUrl = config.getInitParameter("api.termsOfServiceUrl")
    val contact = config.getInitParameter("api.contact")
    val license = config.getInitParameter("api.license")
    val licenseUrl = config.getInitParameter("api.licenseUrl")

    val apiInfo = new ApiInfo(title, description, termsOfServiceUrl, contact, license, licenseUrl)
    swaggerConfig.setApiInfo(apiInfo)
  }

  def getRestDefinitions(camelId: String) : mutable.Buffer[RestDefinition]

  override protected def doGet(request: HttpServletRequest, response: HttpServletResponse) = {
    if (!initDone) {
      initBaseAndApiPaths(request)
    }

    val route = request.getPathInfo
    // render overview if the route is empty or is the root path
    if (route != null && route != "" && route != "/") {
      renderApiDeclaration(request, response)
    } else {
      renderResourceListing(request, response)
    }
  }

  def initBaseAndApiPaths(request: HttpServletRequest) = {
    var base = swaggerConfig.getBasePath
    if (base == null || !base.startsWith("http")) {
      // base path is configured using relative, so lets calculate the absolute url now we have the http request
      val url = new URL(request.getRequestURL.toString)
      if (base == null) {
        base = ""
      }
      val path = translateContextPath(request)
      if (url.getPort != 80 && url.getPort != -1) {
        base = url.getProtocol + "://" + url.getHost + ":" + url.getPort + path + "/" + base
      } else {
        base = url.getProtocol + "://" + url.getHost + request.getContextPath + "/" + base
      }
      swaggerConfig.setBasePath(base)
    }
    base = swaggerConfig.getApiPath
    if (base == null || !base.startsWith("http")) {
      // api path is configured using relative, so lets calculate the absolute url now we have the http request
      val url = new URL(request.getRequestURL.toString)
      if (base == null) {
        base = ""
      }
      val path = translateContextPath(request)
      if (url.getPort != 80 && url.getPort != -1) {
        base = url.getProtocol + "://" + url.getHost + ":" + url.getPort + path + "/" + base
      } else {
        base = url.getProtocol + "://" + url.getHost + request.getContextPath + "/" + base
      }
      swaggerConfig.setApiPath(base)
    }
    initDone = true
  }

  /**
   * We do only want the base context-path and not sub paths
   */
  def translateContextPath(request: HttpServletRequest): String = {
    var path = request.getContextPath
    if (path.isEmpty || path.equals("/")) {
      return ""
    } else {
      val idx = path.lastIndexOf("/")
      if (idx > 0) {
        return path.substring(0, idx)
      }
    }
    path
  }

  /**
   * Renders the resource listing which is the overview of all the apis
   */
  def renderResourceListing(request: HttpServletRequest, response: HttpServletResponse) = {
    LOG.trace("renderResourceListing")

    val queryParams = Map[String, List[String]]()
    val cookies = Map[String, String]()
    val headers = Map[String, List[String]]()

    if (cors) {
      response.setHeader("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers")
      response.setHeader("Access-Control-Allow-Methods", "GET, HEAD, POST, PUT, DELETE, TRACE, OPTIONS, CONNECT, PATCH")
      response.setHeader("Access-Control-Allow-Origin", "*")
    }

    val rests = getRestDefinitions(camelId)
    if (rests != null) {
      val f = new SpecFilter
      val listings = RestApiListingCache.listing(rests, swaggerConfig).map(specs => {
        (for (spec <- specs.values)
        yield f.filter(spec, FilterFactory.filter, queryParams, cookies, headers)
          ).filter(m => m.apis.size > 0)
      })
      val references = (for (listing <- listings.getOrElse(List())) yield {
        ApiListingReference(listing.resourcePath, listing.description)
      }).toList
      val resourceListing = ResourceListing(
        swaggerConfig.apiVersion,
        swaggerConfig.swaggerVersion,
        references,
        List(),
        swaggerConfig.info
      )
      LOG.debug("renderResourceListing write response -> {}", resourceListing)
      response.getOutputStream.write(JsonSerializer.asJson(resourceListing).getBytes("utf-8"))
    } else {
      response.setStatus(204)
    }
  }

  /**
   * Renders the api listing of a single resource
   */
  def renderApiDeclaration(request: HttpServletRequest, response: HttpServletResponse) = {
    LOG.trace("renderApiDeclaration")

    val route = request.getPathInfo
    val docRoot = request.getPathInfo
    val f = new SpecFilter
    val queryParams = Map[String, List[String]]()
    val cookies = Map[String, String]()
    val headers = Map[String, List[String]]()
    val pathPart = docRoot

    if (cors) {
      response.setHeader("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers")
      response.setHeader("Access-Control-Allow-Methods", "GET, HEAD, POST, PUT, DELETE, TRACE, OPTIONS, CONNECT, PATCH")
      response.setHeader("Access-Control-Allow-Origin", "*")
    }

    val rests = getRestDefinitions(camelId)
    if (rests != null) {
      val listings = RestApiListingCache.listing(rests, swaggerConfig).map(specs => {
          (for (spec <- specs.values) yield {
          f.filter(spec, FilterFactory.filter, queryParams, cookies, headers)
        }).filter(m => m.resourcePath == pathPart)
      }).toList.flatten
      listings.size match {
        case 1 => {
          LOG.debug("renderResourceListing write response -> {}", listings.head)
          response.getOutputStream.write(JsonSerializer.asJson(listings.head).getBytes("utf-8"))
        }
        case _ => response.setStatus(404)
      }
    } else {
      // no data
      response.setStatus(204)
    }
  }

}
