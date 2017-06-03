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
package org.apache.camel
package scala

import reflect.{ClassTag, classTag}
import org.apache.camel.spi.{UnitOfWork, Synchronization}

/**
 * Rich wrapper for Camel's Exchange implementations
 */
class RichExchange(val exchange : Exchange) extends Exchange {

  def in_=(message: Any) { exchange.getIn.setBody(message) }

  /**
   * Retrieves given header from the input message. Primarily intended to be used together with the named parameter
   * header, for example:
   *
   * {{{
   * val header = exchange.in(header = "headerKey")
   * }}}
   *
   * @param header name of the header to retrieve
   * @return header value
   */
  def in(header:String) : Any = exchange.getIn.getHeader(header)

  def in = exchange.getIn.getBody
  def in[T : ClassTag] : T = exchange.getIn.getBody(classTag[T].runtimeClass.asInstanceOf[Class[T]])

  def out = exchange.getOut.getBody

  def out(header:String) : Any = exchange.getOut.getHeader(header)

  def out_=(message:Any) { exchange.getOut.setBody(message) }


  // Delegation methods
  //-------------------------------------------------------------------------

  def setUnitOfWork(unitOfWork: UnitOfWork) { exchange.setUnitOfWork(unitOfWork) }

  def setProperty(name: String, value: Any) { exchange.setProperty(name, value) }

  def setPattern(pattern: ExchangePattern) { exchange.setPattern(pattern) }

  def setOut(out: Message) { exchange.setOut(out) }

  def setIn(in: Message) { exchange.setIn(in) }

  def setFromEndpoint(fromEndpoint: Endpoint) { exchange.setFromEndpoint(fromEndpoint) }

  def setFromRouteId(routeId: String) { exchange.setFromRouteId(routeId) }

  def setExchangeId(id: String) { exchange.setExchangeId(id) }

  def setException(t: Throwable) { exchange.setException(t) }

  def removeProperty(name: String) = exchange.removeProperty(name)

  def removeProperties(pattern: String) = exchange.removeProperties(pattern)

  def removeProperties(pattern: String, excludePatterns: String*) = exchange.removeProperties(pattern, excludePatterns: _*)

  def isTransacted = exchange.isTransacted

  def isExternalRedelivered = exchange.isExternalRedelivered

  def isRollbackOnly = exchange.isRollbackOnly

  def isFailed = exchange.isFailed

  def hasProperties = exchange.hasProperties

  def hasOut = exchange.hasOut

  def getUnitOfWork = exchange.getUnitOfWork

  def getProperty[T](name: String, propertyType : Class[T]) = exchange.getProperty(name, propertyType)

  def getProperty[T](name: String, defaultValue: Any, propertyType : Class[T]) = exchange.getProperty(name, defaultValue, propertyType)

  def getProperty(name: String, defaultValue: Any) = exchange.getProperty(name, defaultValue)

  def getProperty(name: String) = exchange.getProperty(name)

  def getProperties = exchange.getProperties

  def getPattern = exchange.getPattern

  def getOut[T](outType : Class[T]) = exchange.getOut(outType)

  def getOut = exchange.getOut

  def getIn[T](inType : Class[T]) = exchange.getIn(inType)

  def getIn = exchange.getIn

  def getFromEndpoint = exchange.getFromEndpoint

  def getFromRouteId = exchange.getFromRouteId

  def getExchangeId = exchange.getExchangeId

  def getException[T](exceptionType : Class[T]) = exchange.getException(exceptionType)

  def getException = exchange.getException

  def getContext = exchange.getContext

  def copy = new RichExchange(exchange.copy)

  def copy(safeCopy: Boolean) = new RichExchange(exchange.copy(safeCopy))

  def addOnCompletion(onCompletion: Synchronization) { exchange.addOnCompletion(onCompletion) }
  
  def containsOnCompletion(onCompletion: Synchronization) = exchange.containsOnCompletion(onCompletion)

  def handoverCompletions(exchange : Exchange) { exchange.handoverCompletions(exchange) }

  def handoverCompletions = exchange.handoverCompletions

  def getCreated = exchange.getCreated
}
