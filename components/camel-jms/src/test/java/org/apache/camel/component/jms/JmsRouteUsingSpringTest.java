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
package org.apache.camel.component.jms;

import org.apache.camel.CamelContext;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.util.IOHelper;
import org.junit.After;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class JmsRouteUsingSpringTest extends JmsRouteTest {
    private ClassPathXmlApplicationContext applicationContext;
    
    @Override
    protected CamelContext createCamelContext() throws Exception {
        applicationContext = createApplicationContext();
        return SpringCamelContext.springCamelContext(applicationContext);
    }
    
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/jms/jmsRouteUsingSpring.xml");
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        IOHelper.close(applicationContext);
    }
}
