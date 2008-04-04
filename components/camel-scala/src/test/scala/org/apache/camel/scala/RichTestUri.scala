package org.apache.camel.scala;

import org.apache.camel.component.mock.MockEndpoint

class RichTestUri(uri: String, support: ScalaTestSupport) {
  
  def !(messages: Any*) = {
    for (message <- messages) {
      support.getTemplate().sendBody(uri, message)
    }
  }
  
  def expect(block: MockEndpoint => Unit) = {
    val mock = support.mock(uri)
    block(mock) 
  }
  
  def assert() = support.mock(uri).assertIsSatisfied()

}
