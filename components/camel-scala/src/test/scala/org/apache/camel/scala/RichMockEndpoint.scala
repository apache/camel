package org.apache.camel.scala;

import org.apache.camel.component.mock.MockEndpoint

class RichMockEndpoint(endpoint: MockEndpoint) {
  
  def received(messages: AnyRef*) {
    val list = new java.util.ArrayList[AnyRef](messages.length)
    messages.foreach(list.add(_))
    endpoint.expectedBodiesReceived(list)
  }

}
 
