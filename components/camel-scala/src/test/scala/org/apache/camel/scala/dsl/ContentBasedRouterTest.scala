package org.apache.camel.scala.dsl;

class ContentBasedRouterTest extends ScalaTestSupport {
  
  def testSimpleContentBasedRouter = {
    "mock:a" expect {_.expectedMessageCount(3)}
    "mock:b" expect {_.received("<hello/>")}
    "mock:c" expect {_.received("<hallo/>")}
    "mock:d" expect {_.received("<hellos/>")}
    "direct:a" ! ("<hello/>", "<hallo/>", "<hellos/>")
    "mock:a" assert()
    "mock:b" assert()
    "mock:c" assert()
    "mock:d" assert()
  }
  
  override protected def createRouteBuilder() = 
    new RouteBuilder {
      "direct:a" ==> {
        to ("mock:a")
        choice {
          when (_.in == "<hello/>") to ("mock:b")
          when (_.in == "<hallo/>") to ("mock:c")
          otherwise to ("mock:d")
        }
      }
    }.print

}
