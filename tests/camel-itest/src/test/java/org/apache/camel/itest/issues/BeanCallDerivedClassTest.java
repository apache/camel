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
package org.apache.camel.itest.issues;

import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.apache.xbean.spring.context.ClassPathXmlApplicationContext;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeanCallDerivedClassTest extends CamelSpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/itest/issues/BeanCallDerivedClassTest-context.xml");
    }

    @Test
    void testCallBean() {
        DerivedClass derived = context.getRegistry().lookupByNameAndType("derived", DerivedClass.class);

        template.sendBody("direct:start", "Hello World");
        assertEquals("Hello World", derived.getBody());
    }

}
