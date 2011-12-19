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
package org.apache.camel.example.cafe;

import org.apache.camel.CamelContext;
import org.apache.camel.example.cafe.test.TestDrinkRouter;
import org.apache.camel.example.cafe.test.TestWaiter;
import org.junit.After;
import org.junit.Before;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CafeRouteSpringTest extends CafeRouteBuilderTest {
    private AbstractApplicationContext applicationContext;
   
    
    @Before
    public void setUp() throws Exception {
        applicationContext = new ClassPathXmlApplicationContext("META-INF/camel-routes.xml");
        setUseRouteBuilder(false);
        super.setUp();
        waiter = applicationContext.getBean("waiter", TestWaiter.class);
        driverRouter = applicationContext.getBean("drinkRouter", TestDrinkRouter.class);
    }
    
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        if (applicationContext != null) {
            applicationContext.stop();
        }
    }
    
    protected CamelContext createCamelContext() throws Exception {
        return applicationContext.getBean("camel", CamelContext.class);
    }

}
