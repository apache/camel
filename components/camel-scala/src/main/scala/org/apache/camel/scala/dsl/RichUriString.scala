package org.apache.camel.scala.dsl;

import org.apache.camel.model.FilterType
import org.apache.camel.model.ProcessorType

class RichUriString(uri:String, builder:RouteBuilder) {
  
  def to(targets: String*) : ProcessorType[T] forSome {type T} = {
    targets.length match {
      case 1 => builder.from(uri).to(targets(0))
      case _ => {
        val from = builder.from(uri)
        val multicast = from.multicast
        for (target <- targets) multicast.to(target)
        from
      }
    }
  }
  def -->(targets: String*) : ProcessorType[T] forSome {type T} = to(targets:_*)
  
  def ==>(block: => Unit) = {
    builder.build(builder.from(uri), block)
  }
  
  def when(filter: Exchange => Boolean) : FilterType = 
    builder.from(uri).filter(new WhenPredicate(filter))
  
  

}
