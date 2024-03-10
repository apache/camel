/*
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
package org.apache.camel.kotlin.components

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

/**
 * Transforms XML payload using an XSLT template.
 */
public fun UriDsl.xslt(i: XsltUriDsl.() -> Unit) {
  XsltUriDsl(this).apply(i)
}

@CamelDslMarker
public class XsltUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("xslt")
  }

  private var resourceUri: String = ""

  /**
   * Path to the template. The following is supported by the default URIResolver. You can prefix
   * with: classpath, file, http, ref, or bean. classpath, file and http loads the resource using these
   * protocols (classpath is default). ref will lookup the resource in the registry. bean will call a
   * method on a bean to be used as the resource. For bean you can specify the method name after dot,
   * eg bean:myBean.myMethod
   */
  public fun resourceUri(resourceUri: String) {
    this.resourceUri = resourceUri
    it.url("$resourceUri")
  }

  /**
   * Whether to allow to use resource template from header or not (default false). Enabling this
   * allows to specify dynamic templates via message header. However this can be seen as a potential
   * security vulnerability if the header is coming from a malicious user, so use this with care.
   */
  public fun allowTemplateFromHeader(allowTemplateFromHeader: String) {
    it.property("allowTemplateFromHeader", allowTemplateFromHeader)
  }

  /**
   * Whether to allow to use resource template from header or not (default false). Enabling this
   * allows to specify dynamic templates via message header. However this can be seen as a potential
   * security vulnerability if the header is coming from a malicious user, so use this with care.
   */
  public fun allowTemplateFromHeader(allowTemplateFromHeader: Boolean) {
    it.property("allowTemplateFromHeader", allowTemplateFromHeader.toString())
  }

  /**
   * Cache for the resource content (the stylesheet file) when it is loaded on startup. If set to
   * false Camel will reload the stylesheet file on each message processing. This is good for
   * development. A cached stylesheet can be forced to reload at runtime via JMX using the
   * clearCachedStylesheet operation.
   */
  public fun contentCache(contentCache: String) {
    it.property("contentCache", contentCache)
  }

  /**
   * Cache for the resource content (the stylesheet file) when it is loaded on startup. If set to
   * false Camel will reload the stylesheet file on each message processing. This is good for
   * development. A cached stylesheet can be forced to reload at runtime via JMX using the
   * clearCachedStylesheet operation.
   */
  public fun contentCache(contentCache: Boolean) {
    it.property("contentCache", contentCache.toString())
  }

  /**
   * If you have output=file then this option dictates whether or not the output file should be
   * deleted when the Exchange is done processing. For example suppose the output file is a temporary
   * file, then it can be a good idea to delete it after use.
   */
  public fun deleteOutputFile(deleteOutputFile: String) {
    it.property("deleteOutputFile", deleteOutputFile)
  }

  /**
   * If you have output=file then this option dictates whether or not the output file should be
   * deleted when the Exchange is done processing. For example suppose the output file is a temporary
   * file, then it can be a good idea to delete it after use.
   */
  public fun deleteOutputFile(deleteOutputFile: Boolean) {
    it.property("deleteOutputFile", deleteOutputFile.toString())
  }

  /**
   * Whether or not to throw an exception if the input body is null.
   */
  public fun failOnNullBody(failOnNullBody: String) {
    it.property("failOnNullBody", failOnNullBody)
  }

  /**
   * Whether or not to throw an exception if the input body is null.
   */
  public fun failOnNullBody(failOnNullBody: Boolean) {
    it.property("failOnNullBody", failOnNullBody.toString())
  }

  /**
   * Option to specify which output type to use. Possible values are: string, bytes, DOM, file. The
   * first three options are all in memory based, where as file is streamed directly to a java.io.File.
   * For file you must specify the filename in the IN header with the key XsltConstants.XSLT_FILE_NAME
   * which is also CamelXsltFileName. Also any paths leading to the filename must be created
   * beforehand, otherwise an exception is thrown at runtime.
   */
  public fun output(output: String) {
    it.property("output", output)
  }

  /**
   * The number of javax.xml.transform.Transformer object that are cached for reuse to avoid calls
   * to Template.newTransformer().
   */
  public fun transformerCacheSize(transformerCacheSize: String) {
    it.property("transformerCacheSize", transformerCacheSize)
  }

  /**
   * The number of javax.xml.transform.Transformer object that are cached for reuse to avoid calls
   * to Template.newTransformer().
   */
  public fun transformerCacheSize(transformerCacheSize: Int) {
    it.property("transformerCacheSize", transformerCacheSize.toString())
  }

  /**
   * Whether the producer should be started lazy (on the first message). By starting lazy you can
   * use this to allow CamelContext and routes to startup in situations where a producer may otherwise
   * fail during starting and cause the route to fail being started. By deferring this startup to be
   * lazy then the startup failure can be handled during routing messages via Camel's routing error
   * handlers. Beware that when the first message is processed then creating and starting the producer
   * may take a little time and prolong the total processing time of the processing.
   */
  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  /**
   * Whether the producer should be started lazy (on the first message). By starting lazy you can
   * use this to allow CamelContext and routes to startup in situations where a producer may otherwise
   * fail during starting and cause the route to fail being started. By deferring this startup to be
   * lazy then the startup failure can be handled during routing messages via Camel's routing error
   * handlers. Beware that when the first message is processed then creating and starting the producer
   * may take a little time and prolong the total processing time of the processing.
   */
  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  /**
   * To use a custom org.xml.sax.EntityResolver with javax.xml.transform.sax.SAXSource.
   */
  public fun entityResolver(entityResolver: String) {
    it.property("entityResolver", entityResolver)
  }

  /**
   * Allows to configure to use a custom javax.xml.transform.ErrorListener. Beware when doing this
   * then the default error listener which captures any errors or fatal errors and store information on
   * the Exchange as properties is not in use. So only use this option for special use-cases.
   */
  public fun errorListener(errorListener: String) {
    it.property("errorListener", errorListener)
  }

  /**
   * Allows you to use a custom org.apache.camel.builder.xml.ResultHandlerFactory which is capable
   * of using custom org.apache.camel.builder.xml.ResultHandler types.
   */
  public fun resultHandlerFactory(resultHandlerFactory: String) {
    it.property("resultHandlerFactory", resultHandlerFactory)
  }

  /**
   * To use a custom XSLT transformer factory
   */
  public fun transformerFactory(transformerFactory: String) {
    it.property("transformerFactory", transformerFactory)
  }

  /**
   * To use a custom XSLT transformer factory, specified as a FQN class name
   */
  public fun transformerFactoryClass(transformerFactoryClass: String) {
    it.property("transformerFactoryClass", transformerFactoryClass)
  }

  /**
   * A configuration strategy to apply on freshly created instances of TransformerFactory.
   */
  public
      fun transformerFactoryConfigurationStrategy(transformerFactoryConfigurationStrategy: String) {
    it.property("transformerFactoryConfigurationStrategy", transformerFactoryConfigurationStrategy)
  }

  /**
   * To use a custom javax.xml.transform.URIResolver
   */
  public fun uriResolver(uriResolver: String) {
    it.property("uriResolver", uriResolver)
  }

  /**
   * A consumer to messages generated during XSLT transformations.
   */
  public fun xsltMessageLogger(xsltMessageLogger: String) {
    it.property("xsltMessageLogger", xsltMessageLogger)
  }
}
