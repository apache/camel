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
package org.apache.camel.spring;


import org.apache.camel.CamelContext;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.IOHelper;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CircularComponentCreationTest {
    @Test
    public void testSimple() {
        try {
            doTest("org/apache/camel/spring/CircularComponentCreationSimpleTest.xml");

            Assert.fail("Exception should have been thrown");
        } catch (RuntimeCamelException e) {
            Assert.assertTrue(e.getCause() instanceof FailedToCreateRouteException);
        }
    }

    @Test
    public void testComplex() {
        doTest("org/apache/camel/spring/CircularComponentCreationComplexTest.xml");
    }

    // *******************************
    // Test implementation
    // *******************************

    private void doTest(String path) {
        AbstractXmlApplicationContext applicationContext = null;
        CamelContext camelContext = null;
        try {
            applicationContext = new ClassPathXmlApplicationContext(path);
            camelContext = new SpringCamelContext(applicationContext);
        } finally {
            IOHelper.close(applicationContext);
        }
    }
}
