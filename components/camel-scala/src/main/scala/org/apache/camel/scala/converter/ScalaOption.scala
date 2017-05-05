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
package org.apache.camel.scala.converter

import org.apache.camel.Converter
import collection.JavaConversions._

@Converter
class ScalaOption {
  import collection.{Iterator, Iterable, Seq}
  import java.lang.{Iterable => JIterable}
  import java.util.{Collection => JCollection, Iterator => JIterator, List => JList}

  @Converter
  def toList[T](opt: Option[T]): List[T] = opt.toList

  @Converter
  def toIterator[T](opt: Option[T]): Iterator[T] = opt.iterator

  @Converter
  def toIterable[T](opt: Option[T]): Iterable[T] = opt.toList

  @Converter
  def toSeq[T](opt: Option[T]): Seq[T] = opt.toList

  @Converter
  def toJavaIterator[T](opt: Option[T]): JIterator[T] = opt.iterator

  @Converter
  def toJavaIterable[T](opt: Option[T]): JIterable[T] = opt.toList

  @Converter
  def toJavaList[T](opt: Option[T]): JList[T] = opt.toList

  @Converter
  def toJavaCollection[T](opt: Option[T]): JCollection[T] = opt.toList
}
