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
package org.apache.camel.language.datasonnet

import com.datasonnet.document.{DefaultDocument, Document, MediaTypes}
import com.datasonnet.spi.{DataFormatService, Library, PluginException}
import org.apache.camel.Exchange
import sjsonnet.Std.builtin
import sjsonnet.{Materializer, Val}

object CML extends Library {
  val exchange: ThreadLocal[Exchange] = new ThreadLocal[Exchange]

  override def namespace(): String = "cml"

  override def libsonnets(): Set[String] = Set.empty

  override def functions(dataformats: DataFormatService): Map[String, Val.Func] = Map(
    // See: org.apache.camel.language.xpath.XPathBuilder.createPropertiesFunction
    builtin("properties", "key")((_, _, key: Val) => {
      key match {
        case Val.Str(value) =>
          // use the property placeholder resolver to lookup the property for us
          exchange.get.getContext.resolvePropertyPlaceholders("{{" + value + "}}")
        case _ => throw new IllegalArgumentException("Expected String got: " + key.prettyName)
      }
    }),

    builtin("header", "key")((_, _, key: Val) => {
      key match {
        case Val.Str(value) => valFrom(exchange.get.getMessage.getHeader(value), dataformats)
        case _ => throw new IllegalArgumentException("Expected String got: " + key.prettyName)
      }
    }),

    builtin("exchangeProperty", "key")((_, _, key: Val) => {
      key match {
        case Val.Str(value) => valFrom(exchange.get.getProperty(value), dataformats)
        case _ => throw new IllegalArgumentException("Expected String got: " + key.prettyName)
      }
    })
  )

  // TODO: write to map null objs to Val.Null instead NPE
  private def valFrom(obj: AnyRef, dataformats: DataFormatService): Val = {
    val doc: Document[_] = obj match {
      case doc: Document[_] => doc
      case _ => new DefaultDocument(obj, MediaTypes.APPLICATION_JAVA)
    }

    try Materializer.reverse(dataformats.thatAccepts(doc)
      .orElseThrow(() => new IllegalArgumentException("todo"))
      .read(doc, dataformats))
    catch {
      case e: PluginException => throw new IllegalStateException(e)
    }
  }

  override def modules(dataformats: DataFormatService): Map[String, Val.Obj] = Map.empty
}
