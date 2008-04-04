package org.apache.camel.scala.dsl;

class PipelineRouteBuilderTest extends ScalaTestSupport {
  
  def testPipelineWithArrows() = testPipeline("direct:a", "mock:a", "mock:b")
  def testPipelineWithTos() = testPipeline("direct:c", "mock:c", "mock:d")
  def testPipelineBlockWithArrows() = testPipeline("direct:e", "mock:e", "mock:f")
  def testPipelineBlockWithTos() = testPipeline("direct:g", "mock:g", "mock:h")
  
  def testPipeline(from: String, to: String*) = {
    to.foreach {
      _.expect { _.expectedMessageCount(1) }
    }
    from ! "<hello/>"
    to.foreach {
      _.assert()
    }
  }
  
  override protected def createRouteBuilder() = 
    new RouteBuilder {
       "direct:a" --> "mock:a" --> "mock:b"
       "direct:c" to "mock:c" to "mock:d"
       
       "direct:e" ==> {
         --> ("mock:e")
         --> ("mock:f")
       }
       
       "direct:g" ==> {
         to ("mock:g")
         to ("mock:h")
       }
    }.print

}
