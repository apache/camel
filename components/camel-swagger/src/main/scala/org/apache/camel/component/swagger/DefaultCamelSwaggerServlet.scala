/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.swagger

import java.io.StringReader
import java.lang.management.ManagementFactory
import javax.management.{MBeanServer, ObjectName}
import javax.xml.bind.{JAXBContext, Unmarshaller}

import org.apache.camel.model.rest.{RestDefinition, RestsDefinition}

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
    val names = server.queryMBeans(new ObjectName("org.apache.camel,context=*,type=context,name=*"), null)
    for (name <- names.asScala) {
      val on = name.asInstanceOf[ObjectName]
      val id: String = on.getKeyProperty("name")
      if (camelId == null || camelId.equals(id)) {
        found = on
      }
    }

    if (found != null) {
      val result = server.invoke(found, "dumpRestsAsXml", null, null)
      if (result != null) {
        val context: JAXBContext = JAXBContext.newInstance(classOf[RestsDefinition])
        val unmarshaller: Unmarshaller = context.createUnmarshaller
        val xml = result.asInstanceOf[String]
        val rests: RestsDefinition = unmarshaller.unmarshal(new StringReader(xml)).asInstanceOf[RestsDefinition]
        return rests.getRests.asScala
      }
    }

    null
  }

}
