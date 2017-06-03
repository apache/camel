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
package org.apache.camel.component.rest;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringFromRestDuplicateTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        System.setProperty("CamelSedaPollTimeout", "10");
        try {
            new ClassPathXmlApplicationContext("org/apache/camel/component/rest/SpringFromRestDuplicateTest.xml");
            fail("Should throw exception");
        } catch (RuntimeCamelException e) {
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Duplicate verb detected in rest-dsl: get:{id}", iae.getMessage());
        }
        return null;
    }

    @Override
    protected void setUp() throws Exception {
        // must override as there is no valid spring xml file
        createApplicationContext();
    }

    public void testDuplicate() throws Exception {
        // noop
    }
}
