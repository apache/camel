package org.apache.camel.scala.dsl;

class FilterRouteBuilderTest extends ScalaTestSupport {

  def testSimpleFilter() = {
    "mock:a" expect {_.expectedMessageCount(1)}
    "direct:a" ! ("<hello/>", "<hellos/>")
    "mock:a" assert
  }
  
  def testFilterWithAlternatives() = {
    "mock:b" expect {_.expectedMessageCount(1)}
    "mock:b" expect {_.expectedMessageCount(1)}
    "mock:d" expect {_.expectedMessageCount(2)}
    "mock:e" expect {_.expectedMessageCount(0)}
    "mock:f" expect {_.expectedMessageCount(2)}
    "direct:b" ! ("<hello/>", "<hellos/>")
    "mock:b" assert()
    "mock:c" assert()
    "mock:d" assert()
    "mock:e" assert()
    "mock:f" assert()
  }
  
  override protected def createRouteBuilder() = 
    new RouteBuilder {
       "direct:a" when(_.in == "<hello/>") to "mock:a"
         
       "direct:b" ==> {
         when(_.in == "<hello/>") then {
           to ("mock:b")
           --> ("mock:c")
         }
         when(_.in == "<hallo/>") {
           to ("mock:e")
         } otherwise {
           to ("mock:f")
         }
         to ("mock:d")  
       }
    }.print

}
