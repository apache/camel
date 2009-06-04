package org.apache.camel.scala.dsl

import model.{WhenDefinition,OnCompletionDefinition}
import org.apache.camel.scala.dsl.builder.RouteBuilder;

case class SOnCompletionDefinition(override val target : OnCompletionDefinition)(implicit val builder : RouteBuilder) extends SAbstractDefinition[OnCompletionDefinition] {

  import org.apache.camel.scala.dsl.SOnCompletionDefinition.{Strategy,FailureOnly}

  override def when(predicate : Exchange => Boolean) : SOnCompletionDefinition = 
    wrap(target.setOnWhen(new WhenDefinition(new ScalaPredicate(predicate))))

  def strategy(strategy : Strategy) : SOnCompletionDefinition = {
    strategy.applyTo(target)
    this
  }

  def onFailureOnly = wrap(target.onFailureOnly)
  def onCompleteOnly = wrap(target.onCompleteOnly)

  override def wrap(block: => Unit) = super.wrap(block).asInstanceOf[SOnCompletionDefinition]

}

object SOnCompletionDefinition {
  
  abstract class Strategy {
    def applyTo(target: OnCompletionDefinition)
  }
  case class FailureOnly extends Strategy {
    def applyTo(target: OnCompletionDefinition) : Unit = target.onFailureOnly
  }
  case class CompleteOnly extends Strategy {
    def applyTo(target: OnCompletionDefinition) : Unit = target.onCompleteOnly
  }

}
