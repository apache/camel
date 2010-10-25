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
package org.apache.camel.scala;

import junit.framework.TestCase
import junit.framework.Assert._

import org.apache.camel.impl.DefaultClassResolver
import org.apache.camel.impl.DefaultFactoryFinderResolver
import org.apache.camel.impl.DefaultPackageScanClassResolver
import org.apache.camel.impl.converter.DefaultTypeConverter
import org.apache.camel.util.ReflectionInjector
import org.apache.camel.util.ServiceHelper

import org.w3c.dom.Document

/**
 * Test case for ScalaTypeConverter
 */
class ScalaTypeConverterTest extends TestCase {
  
  val converter = new DefaultTypeConverter(new DefaultPackageScanClassResolver(),
      new ReflectionInjector(), new DefaultFactoryFinderResolver().resolveDefaultFactoryFinder(new DefaultClassResolver()))

  def testDummy = {
    // noop
  }

  // TODO: Fixme, fails on Linux
  def xxxtestDocumentConverter = {
    ServiceHelper.startService(converter)
    val result = converter.convertTo(classOf[Document], <persons/>)
    assertNotNull(result)
    assertNotNull(result.getElementsByTagName("persons"))
  }

}
