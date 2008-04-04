package org.apache.camel.scala.dsl

import org.apache.camel.Exchange

class RichExchange(val exchange : Exchange) {
  
  def in : Any = exchange.getIn().getBody()

  def in(header:String) : Any = exchange.getIn().getHeader(header)
  
  def in(target:Class[Any]) : Any = exchange.getIn().getBody(target)  
  
  def out : Any = exchange.getOut().getBody()
  
  def out(header:String) : Any = exchange.getOut().getHeader(header) 
  
  def out_=(message:Any) = exchange.getOut().setBody(message)
  
}
