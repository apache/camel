package org.apache.camel.scala;

import org.apache.camel.ContextTestSupport
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.scala.dsl._

abstract class ScalaTestSupport extends ContextTestSupport with RouteBuilderSupport {

  implicit def stringToUri(uri:String) = new RichTestUri(uri, this)
  implicit def mockWrapper(endpoint: MockEndpoint) = new RichMockEndpoint(endpoint)
  implicit def exchangeWrapper(exchange: Exchange[T] forSome {type T}) = new RichExchange(exchange)
  
  def assert(uri: String) = getMockEndpoint(uri).assertIsSatisfied
  
  protected[scala] def getTemplate() = template
  
  protected[scala] def mock(uri: String) = getMockEndpoint(uri)
  
  def in(message: Any) : Exchange = {
    val exchange = createExchangeWithBody(message)
    println(exchange)
    exchange
  }
  
}
