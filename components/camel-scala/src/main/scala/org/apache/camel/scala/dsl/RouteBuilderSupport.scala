package org.apache.camel.scala.dsl;

trait RouteBuilderSupport {

  implicit def scalaToJavaBuilder(scalaBuilder: org.apache.camel.scala.dsl.RouteBuilder) = scalaBuilder.builder
  
}
