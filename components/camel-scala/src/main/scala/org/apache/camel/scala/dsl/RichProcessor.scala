package org.apache.camel.scala.dsl;

import org.apache.camel.model.ProcessorType

class RichProcessor(processor : ProcessorType[T] forSome {type T}) {
   
  def -->(uri: String) = processor.to(uri)
  
}
