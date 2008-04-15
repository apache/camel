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
package org.apache.camel.scala.dsl;

import org.apache.camel.model.FilterType
import org.apache.camel.model.ProcessorType

class RichUriString(uri:String, builder:RouteBuilder) {

  def to(targets: String*) : ProcessorType[T] forSome {type T} = {
    val from = builder.from(uri)
    targets.length match {
      case 1 => from.to(targets(0))
      case _ => {
        val multicast = from.multicast
        for (target <- targets) multicast.to(target)
        from
      }
    }
  }
  def -->(targets: String*) : ProcessorType[T] forSome {type T} = to(targets:_*)

  def ==>(block: => Unit) = {
    builder.build(builder.from(uri), block)
  }

  def when(filter: Exchange => Boolean) : FilterType =
    builder.from(uri).filter(new WhenPredicate(filter))



}
