package org.apache.camel.scala.dsl;

import org.apache.camel.model.ChoiceType

class RichChoiceType(val choice: ChoiceType, val builder:RouteBuilder) {
  
  def then(block: => Unit) : ChoiceType = {
    builder.build(choice, block)
    choice
  }
  
  def when(test: Exchange => Boolean)(block: => Unit) : ChoiceType = {
    choice
  }
  
  def apply(block: => Unit) : RichChoiceType = {
    builder.build(choice, block)
    this
  }
  
  def to(uri: String) = {
    choice.to(uri)
  }

  def otherwise(block: => Unit) = {
    choice.otherwise
    builder.build(choice, block)
  }
}
