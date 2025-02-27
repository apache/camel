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
package org.apache.camel.spring.primary;

import java.util.Set;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.NoSuchBeanTypeException;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class FindSingleByTypeTest extends ContextTestSupport {

    private AbstractApplicationContext applicationContext;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        if (context != null) {
            context.stop();
        }
        applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/spring/primary/findBySingle.xml");
        context = applicationContext.getBean("myCamel", ModelCamelContext.class);
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        // we're done so let's properly close the application context
        IOHelper.close(applicationContext);

        super.tearDown();
    }

    @Test
    public void testFindSingle() {
        // should find primary
        Customer c = context.getRegistry().findSingleByType(Customer.class);

        Assertions.assertNotNull(c);
        Assertions.assertEquals("Donald", c.name());

        // should not find anything
        Object o = context.getRegistry().findSingleByType(UuidGenerator.class);
        Assertions.assertNull(o);
    }

    @Test
    public void testFindByType() {
        // should find primary
        Set<Customer> set = context.getRegistry().findByType(Customer.class);

        // should find both beans
        Assertions.assertEquals(2, set.size());
    }

    @Test
    public void testFindSingleMandatory() {
        // should find primary
        Customer c = context.getRegistry().mandatoryFindSingleByType(Customer.class);
        Assertions.assertEquals("Donald", c.name());

        // should not find anything
        Assertions.assertThrows(NoSuchBeanTypeException.class,
                () -> context.getRegistry().mandatoryFindSingleByType(UuidGenerator.class));
    }

}
