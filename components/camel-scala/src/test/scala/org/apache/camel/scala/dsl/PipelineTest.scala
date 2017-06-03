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
package org.apache.camel.scala.dsl

import builder.RouteBuilder
import org.apache.camel.Exchange
import javax.activation.{FileDataSource, DataHandler}
import org.junit.Test
import org.junit.Assert.assertNotNull

/**
 * Unit tests for the explicit 'pipeline' DSL keyword 
 */
class PipelineTest extends ScalaTestSupport {

  val increment = (exchange: Exchange) => {
    val integer = exchange.getIn.getBody(classOf[Int])
    exchange.getOut.setBody(integer + 1)
  }

  val setHeaderAndAttachment = (exchange: Exchange) => {
    val out = exchange.getOut
    out.copyFrom(exchange.getIn)

    out.setHeader("test", "testValue")
    out.addAttachment("test1.xml", new DataHandler(new FileDataSource("pom.xml")))
    out.getAttachmentObject("test1.xml").addHeader("attachmentHeader", "testvalue")
  }

  val removeHeaderAndAttachment = (exchange: Exchange) => {
    val out = exchange.getOut
    out.copyFrom(exchange.getIn)

    assertNotNull("The test attachment should not be null", out.getAttachment("test1.xml"))
    assertNotNull("The test attachement header should not be null", out.getAttachmentObject("test1.xml").getHeader("attachmentHeader"))
    assertNotNull("The test header should not be null", out.getHeader("test"))
    out.removeAttachment("test1.xml")
    out.removeHeader("test") : Unit
  }  

  @Test
  def testIncrementSimple() {
    "mock:result" expect { _.received(new java.lang.Integer(4))}
    test {
      "direct:a" ! 1
    }
  }

  @Test
  def testIncrementBlock() {
    "mock:result" expect { _.received(new java.lang.Integer(4))}
    test {
      "direct:b" ! 1
    }
  }

  @Test
  def testExplicitPipeline() {
    test {
      "direct:start" ! "Hello world"
    }
  }

  val builder = new RouteBuilder {
    "direct:a" pipeline to("direct:x", "direct:y", "direct:z", "mock:result")
    "direct:b" ==> {
      pipeline {
        to("direct:x")
        to("direct:y")
        to("direct:z")
        to("mock:result")
      }
    }

    "direct:x" process(increment)
    "direct:y" process(increment)
    "direct:z" process(increment)

    "direct:start" ==> {
      pipeline {
        process(setHeaderAndAttachment)
        process(removeHeaderAndAttachment)
      }
    }
  }
}
