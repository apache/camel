/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel
package scala.dsl.languages

/**
 * Trait to support the different expression languages available in Camel 
 */
trait Languages {
  
  /**
   * Implicitly make a method for every language available on the Camel Exchange
   */
  implicit def addLanguageMethodsToExchange(exchange: Exchange) = new {
    def el(expression: String) =         Languages.this.el(expression)(exchange)
    def groovy(expression: String) =     Languages.this.groovy(expression)(exchange)
    def header(headerName: String) =     Languages.this.header(headerName)(exchange)
    def javascript(expression: String) = Languages.this.javascript(expression)(exchange)
    def jxpath(expression: String) =     Languages.this.jxpath(expression)(exchange)
    def mvel(expression: String) =       Languages.this.mvel(expression)(exchange)
    def ognl(expression: String) =       Languages.this.ognl(expression)(exchange)
    def php(expression: String) =        Languages.this.php(expression)(exchange)
    def property(propertyName: String) = Languages.this.property(propertyName)(exchange)
    def python(expression: String) =     Languages.this.python(expression)(exchange)
    def ruby(expression: String) =       Languages.this.ruby(expression)(exchange)
    def simple(expression: String) =     Languages.this.simple(expression)(exchange)
    def spel(expression: String) =       Languages.this.spel(expression)(exchange)
    def sql(expression: String) =        Languages.this.sql(expression)(exchange)
    def xpath(expression: String) =      Languages.this.xpath(expression)(exchange)
    def xquery(expression: String) =     Languages.this.xquery(expression)(exchange)
  }
  
  // a set of methods to allow direct use of the language as an expression
  def el(expression: String)(exchange: Exchange) =         Languages.evaluate(expression)(exchange)("el")
  def groovy(expression: String)(exchange: Exchange) =     Languages.evaluate(expression)(exchange)("groovy")
  def header(headerName: String)(exchange: Exchange) =     Languages.evaluate(headerName)(exchange)("header")
  def javascript(expression: String)(exchange: Exchange) = Languages.evaluate(expression)(exchange)("javascript")
  def jxpath(expression: String)(exchange: Exchange) =     Languages.evaluate(expression)(exchange)("jxpath")
  def mvel(expression: String)(exchange: Exchange) =       Languages.evaluate(expression)(exchange)("mvel")
  def ognl(expression: String)(exchange: Exchange) =       Languages.evaluate(expression)(exchange)("ognl")
  def php(expression: String)(exchange: Exchange) =        Languages.evaluate(expression)(exchange)("php")
  def property(propertyName: String)(exchange: Exchange) = Languages.evaluate(propertyName)(exchange)("property")
  def python(expression: String)(exchange: Exchange) =     Languages.evaluate(expression)(exchange)("python")
  def ruby(expression: String)(exchange: Exchange) =       Languages.evaluate(expression)(exchange)("ruby")
  def simple(expression: String)(exchange: Exchange) =     Languages.evaluate(expression)(exchange)("simple")
  def spel(expression: String)(exchange: Exchange) =       Languages.evaluate(expression)(exchange)("spel")
  def sql(expression: String)(exchange: Exchange) =        Languages.evaluate(expression)(exchange)("sql")
  def xpath(expression: String)(exchange: Exchange) =      Languages.evaluate(expression)(exchange)("xpath")
  def xquery(expression: String)(exchange: Exchange) =     Languages.evaluate(expression)(exchange)("xquery")
  
}

/**
 * Companion object with the static template method for language support 
 */
object Languages {

  def evaluate(expression: String)(exchange: Exchange)(lang: String) : Any = {
    val language = exchange.getContext().resolveLanguage(lang)
    // return a language function as the language should support being
    // evaluated as a predicate or expression depending on its usage
    new LanguageFunction(language, expression)
  }
}
