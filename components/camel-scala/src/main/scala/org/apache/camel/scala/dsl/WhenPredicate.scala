package org.apache.camel.scala.dsl;

import org.apache.camel.Predicate

class WhenPredicate(function: Exchange => Boolean) extends Predicate[Exchange]{
  
  override def matches(exchange: Exchange) = {
    function(exchange)
  }
  
  override def assertMatches(text: String, exchange: Exchange) = {
    if (!matches(exchange)) throw new AssertionError(text + " : " + exchange + " doesn't match Scala function")
  }

}
