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

import com.wordnik.swagger.servlet.listing.{ApiListingCache, ApiDeclarationServlet}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import com.wordnik.swagger.core.filter.SpecFilter
import com.wordnik.swagger.config.{ConfigFactory, FilterFactory}
import com.wordnik.swagger.model.{ResourceListing, ApiListingReference}
import com.wordnik.swagger.core.util.JsonSerializer
import javax.servlet.ServletConfig
import org.springframework.web.context.support.WebApplicationContextUtils
import org.springframework.web.context.WebApplicationContext
import org.apache.camel.CamelContext
import org.slf4j.LoggerFactory

class RestSwaggerApiDeclarationServlet extends ApiDeclarationServlet {

  private val LOG = LoggerFactory.getLogger(classOf[RestSwaggerApiDeclarationServlet])

  var spring: WebApplicationContext = null
  val reader = new RestSwaggerReader()

  override def init(config: ServletConfig): Unit = {
    super.init(config)
    LOG.info("init")
    spring = WebApplicationContextUtils.getRequiredWebApplicationContext(config.getServletContext)
    LOG.info("init found spring {}", spring)
  }

  override def renderResourceListing(request: HttpServletRequest, response: HttpServletResponse) = {
    LOG.info("renderResourceListing")

    val docRoot = ""
    val queryParams = Map[String, List[String]]()
    val cookies = Map[String, String]()
    val headers = Map[String, List[String]]()

    val camel = spring.getBean(classOf[CamelContext])
    LOG.info("renderResourceListing camel -> {}", camel)
    if (camel != null) {

      val config = ConfigFactory.config
      val rests = camel.getRestDefinitions
      LOG.info("renderResourceListing rests -> {}", rests)
      val list = reader.read(rests.get(0), config)
      LOG.info("renderResourceListing reader -> {}", list)
      val cache = Some(list.map(m => (m.resourcePath, m)).toMap)
      LOG.info("renderResourceListing reader -> {}", cache)

      val f = new SpecFilter
      // val listings = ApiListingCache.listing(docRoot).map(specs => {
      val listings = cache.map(specs => {
        (for (spec <- specs.values)
        yield f.filter(spec, FilterFactory.filter, queryParams, cookies, headers)
          ).filter(m => m.apis.size > 0)
      })
      val references = (for (listing <- listings.getOrElse(List())) yield {
        ApiListingReference(listing.resourcePath, listing.description)
      }).toList
      val resourceListing = ResourceListing(config.apiVersion,
        config.swaggerVersion,
        references
      )
      LOG.info("renderResourceListing write response -> {}", resourceListing)
      response.getOutputStream.write(JsonSerializer.asJson(resourceListing).getBytes("utf-8"))
    }
  }

  override def renderApiDeclaration(request: HttpServletRequest, response: HttpServletResponse) = {
    val route = request.getPathInfo
    val docRoot = request.getPathInfo
    val f = new SpecFilter
    val queryParams = Map[String, List[String]]()
    val cookies = Map[String, String]()
    val headers = Map[String, List[String]]()
    val pathPart = docRoot

    val camel = spring.getBean(classOf[CamelContext])
    LOG.info("renderApiDeclaration camel -> {}", camel)
    if (camel != null) {

      val config = ConfigFactory.config
      val rests = camel.getRestDefinitions
      LOG.info("renderApiDeclaration rests -> {}", rests)
      val list = reader.read(rests.get(0), config)
      LOG.info("renderApiDeclaration reader -> {}", list)
      val cache = Some(list.map(m => (m.resourcePath, m)).toMap)
      LOG.info("renderApiDeclaration reader -> {}", cache)

      // val listings = ApiListingCache.listing(docRoot).map(specs => {
      val listings = cache.map(specs => {
          (for (spec <- specs.values) yield {
          f.filter(spec, FilterFactory.filter, queryParams, cookies, headers)
        }).filter(m => m.resourcePath == pathPart)
      }).toList.flatten
      listings.size match {
        case 1 => {
          LOG.info("renderResourceListing write response -> {}", listings.head)
          response.getOutputStream.write(JsonSerializer.asJson(listings.head).getBytes("utf-8"))
        }
        case _ => response.setStatus(404)
      }
    }
    response.setStatus(404)
  }

}
