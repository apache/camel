package org.apache.camel.scala.dsl;

class PipelineAndMulticastTest extends ScalaTestSupport {
  
  def testArrowRoute = testRoute("direct:a", "mock:c", "mock:a", "mock:b")
  def testToRoute = testRoute("direct:d", "mock:f", "mock:d", "mock:e")
  def testArrowBlockRoute = testRoute("direct:g", "mock:i", "mock:g", "mock:h")
  def testToBlockRoute = testRoute("direct:j", "mock:l", "mock:j", "mock:k")
  
  def testRoute(from: String, end: String, multis: String*) = {
    multis.foreach ( _.expect { _.received("<hello/>")})
    end expect { _.received("<olleh/>")}
    
    val exchange = in("<hello/>")
    exchange.out = "<olleh/>"
    getTemplate().send(from, exchange)
    
    multis.foreach( _.assert())
    end assert()
  }
  
  
  override protected def createRouteBuilder() = 
    new RouteBuilder {
      "direct:a" --> ("mock:a", "mock:b") --> "mock:c"
      "direct:d" to ("mock:d", "mock:e") to "mock:f"
      "direct:g" ==> {
        --> ("mock:g", "mock:h")
        --> ("mock:i")
      }
      "direct:j" ==> {
        to ("mock:j", "mock:k")
        to ("mock:l")
      }  
    }.print

}
