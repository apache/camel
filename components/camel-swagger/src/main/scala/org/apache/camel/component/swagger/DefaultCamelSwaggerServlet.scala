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

import java.lang.management.ManagementFactory
import javax.management.{MBeanServer, ObjectName}

import org.apache.camel.model.ModelHelper
import org.apache.camel.model.rest.{RestDefinition, RestsDefinition}
import org.apache.camel.util.CamelVersionHelper

import scala.collection.mutable

// to iterate Java list using for loop
import scala.collection.JavaConverters._

/**
 * Default Camel swagger servlet.
 */
class DefaultCamelSwaggerServlet extends RestSwaggerApiDeclarationServlet {

  override def getRestDefinitions(camelId: String): mutable.Buffer[RestDefinition] = {
    var found: ObjectName = null

    val server: MBeanServer = ManagementFactory.getPlatformMBeanServer
    val names = server.queryNames(new ObjectName("org.apache.camel:type=context,*"), null)
    for (name <- names.asScala) {
      val on = name.asInstanceOf[ObjectName]
      var id: String = on.getKeyProperty("name")
      if (id.startsWith("\"") && id.endsWith("\"")) {
         id = id.substring(1, id.length() - 1)
      }
      if (camelId == null || camelId.equals(id)) {
        // filter out older Camel versions as this requires Camel 2.15 or better
        val version = server.getAttribute(on, "CamelVersion").asInstanceOf[String]
        if (CamelVersionHelper.isGE("2.15.0", version)) {
          found = on
        }
      }
    }

    if (found != null) {
      val result = server.invoke(found, "dumpRestsAsXml", null, null)
      if (result != null) {
        val xml = result.asInstanceOf[String]
        val rests: RestsDefinition = ModelHelper.createModelFromXml(null, xml, classOf[RestsDefinition])
        val answer = new scala.collection.mutable.ListBuffer[RestDefinition]
        for (rest <- rests.getRests.asScala) {
          answer += rest
        }
        return answer
      }
    }

    null
  }

  override def findCamelContexts(): List[String] = {
    var answer = mutable.MutableList[String]()

    val server: MBeanServer = ManagementFactory.getPlatformMBeanServer
    val names = server.queryNames(new ObjectName("*:type=context,*"), null)
    for (name <- names.asScala) {
      val on = name.asInstanceOf[ObjectName]
      var id: String = on.getKeyProperty("name")
      if (id.startsWith("\"") && id.endsWith("\"")) {
        id = id.substring(1, id.length() - 1)
      }

      if (id != null) {
        // filter out older Camel versions as this requires Camel 2.15 or better
        val version = server.getAttribute(on, "CamelVersion").asInstanceOf[String]
        if (CamelVersionHelper.isGE("2.15.0", version)) {
          answer += id
        }
      }
    }
    return answer.toList
  }

}
