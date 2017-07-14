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
class ScalaImmutableCollections {
  import collection.{Iterator, Iterable, Map, Seq, Set}
  import java.lang.{Iterable => JIterable}
  import java.util.{Collection => JCollection, Enumeration => JEnumeration, Iterator => JIterator, List => JList, Map => JMap, Set => JSet}

  @Converter
  def toJavaIterator[T](iterator: Iterator[T]): JIterator[T] = iterator

  @Converter
  def toEnumeration[T](iterator: Iterator[T]): JEnumeration[T] = iterator

  @Converter
  def toScalaIterator[T](iterator: JIterator[T]): Iterator[T] = iterator

  @Converter
  def toScalaIterator[T](enumeration: JEnumeration[T]): Iterator[T] = enumeration

  @Converter
  def toJavaIterable[T](iterable: Iterable[T]): JIterable[T] = asJavaIterable(iterable)

  @Converter
  def toScalaIterable[T](iterable: JIterable[T]): Iterable[T] = iterable

  @Converter
  def toJavaCollection[T](iterable: Iterable[T]): JCollection[T] = iterable

  @Converter
  def toJavaCollection[T](list: List[T]): JCollection[T] = list

  @Converter
  def toScalaIterable[T](collection: JCollection[T]): Iterable[T] = collection

  @Converter
  def toScalaList[T](collection: JCollection[T]): List[T] = (collection:Iterable[T]).toList

  @Converter
  def toJavaSet[T](set: Set[T]): JSet[T] = set

  @Converter
  def toJavaMap[A,B](map: Map[A,B]): JMap[A,B] = map
  
  @Converter
  def toJavaList[T](list: List[T]): JList[T] = list

  @Converter
  def toScalaList[T](list: JList[T]): List[T] = (list:Iterable[T]).toList

  @Converter
  def toJavaList[T](seq: Seq[T]): JList[T] = seq
}

@Converter
class ScalaMutableCollections {
  import collection.mutable.{Buffer, Map, Seq, Set}
  import java.util.{Dictionary => JDictionary, List => JList, Map => JMap, Set => JSet}
  import java.util.concurrent.{ConcurrentMap => JConcurrentMap}

  @Converter
  def toJavaList[T](buffer: Buffer[T]): JList[T] = buffer

  @Converter
  def toScalaBuffer[T](list: JList[T]): Buffer[T] = list

  @Converter
  def toJavaSet[T](set: Set[T]): JSet[T] = set

  @Converter
  def toScalaSet[T](set: JSet[T]): Set[T] = set

  @Converter
  def toJavaDictionary[A,B](map: Map[A,B]): JDictionary[A,B] = map

  @Converter
  def toScalaMap[A,B](dictionary: JDictionary[A,B]): Map[A,B] = dictionary

  @Converter
  def toJavaMap[A,B](map: Map[A,B]): JMap[A,B] = map

  @Converter
  def toScalaMap[A,B](map: JMap[A,B]): Map[A,B] = map

  @Converter
  def toJavaConcurrentMap[A,B](map: scala.collection.concurrent.Map[A,B]): JConcurrentMap[A,B] = map

  @Converter
  def toScalaConcurrentMap[A,B](map: JConcurrentMap[A,B]): scala.collection.concurrent.Map[A,B] = map

  @Converter
  def toJavaList[T](seq: Seq[T]): JList[T] = seq
}
