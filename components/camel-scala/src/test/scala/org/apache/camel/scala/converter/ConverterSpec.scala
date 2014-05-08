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

import org.apache.camel.scala.CamelSpec
import org.scalatest.FunSpec
import org.scalatest.MustMatchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

import reflect.{ClassTag, classTag}
import org.w3c.dom.Document
import xml.Elem
import scala.Some
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(classOf[JUnitRunner])
class ConverterSpec extends FunSpec with CamelSpec with MustMatchers {
  describe("types converter") {
    it("must convert symbol to string") {
      to[String]('Test) must equal(Some("Test"))
    }
    it("must convert string to symbol") {
      to[Symbol]("Test") must equal(Some('Test))
    }
  }

  describe("immutable collections converter") {
    import collection.{Iterator, Iterable, Map, Seq, Set}
    import java.lang.{Iterable => JIterable}
    import java.util.{Collection => JCollection, Collections, Iterator => JIterator, Enumeration, List => JList, Map => JMap, Set => JSet}

    it("must convert scala iterators") {
      val it = Iterator.single(1)
      to[JIterator[Int]](it) must be('defined)
      to[Enumeration[Int]](it) must be('defined)
    }
    it("must convert java iterators") {
      val l = Collections.singletonList(1)
      to[Iterator[Int]](l.iterator) must be('defined)
      to[Iterator[Int]](Collections.enumeration(l)) must be('defined)
    }
    it("must convert scala iterable") {
      val it = Iterable(1)
      to[JIterable[Int]](it) must be('defined)
      to[JCollection[Int]](it) must be('defined)
    }
    it("must convert java list -> scala list") {
      to[List[Int]]( Collections.singletonList(2) ) must be('defined)
      to[List[Int]]( Collections.emptyList ) must equal(Some(Nil))
    }
    it("must convert scala list -> java list") {
      to[JList[Int]]( 2 :: Nil) must be('defined)
      to[JList[Int]]( Nil ) must be('defined)
    }
    it("must convert java collection") {
      val it = Collections.singletonList(1)
      to[Iterable[Int]](it) must be('defined)
    }
    it("must convert scala set") {
      val s = Set(1)
      to[JSet[Int]](s) must be('defined)
    }
    it("must convert java set") {
      val s = Collections.singleton(1)
      to[Set[Int]](s) must (be('defined) and equal(Some(Set(1))))
    }
    it("must convert scala seq") {
      val s = Seq(1)
      to[JList[Int]](s) must be('defined)
    }
    it("must convert scala map") {
      val m = Map("a" -> 1)
      to[JMap[String, Int]](m) must be('defined)
    }
    it("must convert java map") {
      val m = Collections.singletonMap("a", 1)
      to[Map[String, Int]](m) must (be('defined) and equal(Some(Map("a" -> 1))))
    }
  }

  describe("mutable collections converter") {
    import collection.mutable.{Buffer, Map, Seq, Set}
    import java.util.{Dictionary => JDictionary, Hashtable => JHashtable, Collections, List => JList, Map => JMap, Set => JSet}
    import java.util.concurrent.{ConcurrentHashMap => JConcurrentHashMap}

    it("must convert scala buffer") {
      val b = Buffer(1)
      to[JList[Int]](b) must be('defined)
    }
    it("must convert java buffer") {
      val b = Collections.singletonList(1)
      to[Buffer[Int]](b) must be('defined)
    }
    it("must convert scala set") {
      val s = Set(1)
      to[JSet[Int]](s) must be('defined)
    }
    it("must convert java set") {
      val s = Collections.singleton(1)
      to[Set[Int]](s) must be('defined)
    }
    it("must convert scala map") {
      val m = Map("a" -> 1)
      to[JDictionary[String,Int]](m) must be('defined)
      to[JMap[String,Int]](m) must be('defined)
    }
    it("must convert java map") {
      val m = Collections.singletonMap("a", 1)
      to[Map[String,Int]](m) must be('defined)
    }
    it("must convert java dictionary") {
      val d = new JHashtable[String,Int](Collections.singletonMap("a", 1))
      to[Map[String,Int]](d) must be('defined)
    }
    it("must convert java concurrent map") {
      val m = new JConcurrentHashMap[String,Int](Collections.singletonMap("a", 1))
      to[scala.collection.concurrent.Map[String,Int]](m) must be('defined)
    }
    it("must convert scala seq") {
      val s = Seq(1)
      to[JList[Int]](s) must be('defined)
    }
  }

  describe("option converter") {
    import collection.{Iterator, Iterable}
    import java.lang.{Iterable => JIterable}
    import java.util.{Collection => JCollection, Iterator => JIterator, List => JList}

    it("must convert option -> list") {
      to[List[Int]]( Some(2) ) must be('defined)
    }
    it("must convert option -> iterable") {
      to[Iterable[Int]]( Some(2) ) must be('defined)
    }
    it("must convert option -> iterator") {
      to[Iterator[Int]]( Some(2) ) must be('defined)
    }
    it("must convert option -> jlist") {
      to[JList[Int]]( Some(2) ) must be('defined)
    }
    it("must convert option -> jcollection") {
      to[JCollection[Int]]( Some(2) ) must be('defined)
    }
    it("must convert option -> jiterator") {
      to[JIterator[Int]]( Some(2) ) must be('defined)
    }
    it("must convert option -> jiterable") {
      to[JIterable[Int]]( Some(2) ) must be('defined)
    }
  }

  describe("scala xml converter") {
    it("must convert to document") {
      val v = <persons/>
      val result = to[Document](v)
      result must be('defined)
      Option(result.get.getElementsByTagName("persons")) must be('defined)
    }

    it("must convert string to document") {
      val result = to[Elem]("<persons/>")
      result.get must equal(<persons/>)
    }

    it("must convert dom to elem") {
      val doc = createDocument
      val element = doc.createElement("persons")
      doc.appendChild(element)

      val result = to[Elem](doc)
      result must be('defined)
      result.get must equal(<persons/>)
    }

    it("must convert dom node to elem") {
      val result = to[Elem](createDocument.createElement("persons"))
      result must be('defined)
      result.get must equal(<persons/>)
    }


    def createDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()


  }

  private def to[T : ClassTag](x: AnyRef): Option[T] = Option( context.getTypeConverter.mandatoryConvertTo(classTag[T].runtimeClass.asInstanceOf[Class[T]], createExchange, x) )
}
