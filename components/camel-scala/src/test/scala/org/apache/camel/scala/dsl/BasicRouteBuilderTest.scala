package org.apache.camel.scala.dsl;

import org.apache.camel.scala.ScalaTestSupport

class BasicRouteBuilderTest extends ScalaTestSupport {
  
  def testBasicRouteArrowSyntax() = assertBasicRoute("direct:a", "mock:a")
  def testBasicRouteTextSyntax() = assertBasicRoute("direct:b", "mock:b")
    
  def assertBasicRoute(from: String, to: String) = {
    to expect {
      _.expectedMessageCount(1)
    }
    from ! "<hello/>" 
    to assert
  }
    
  override protected def createRouteBuilder() = 
    new RouteBuilder {
       "direct:a" --> "mock:a"
       "direct:b" to "mock:b"
    }.print

}
