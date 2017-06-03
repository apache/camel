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

import java.util.Map;

import org.apache.camel.impl.DefaultConsumerTemplate;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class CamelContextAwareTest extends SpringTestSupport {
    protected CamelContextAwareBean bean1;


    public void testInjectionPoints() throws Exception {
        assertNotNull("No CamelContext injected!", bean1.getCamelContext());
        Map<String, String> globalOptions  = bean1.getCamelContext().getGlobalOptions();
        assertNotNull("The global options reference should not be null", globalOptions);
        assertEquals("No global options injected", globalOptions.size(), 1);
        assertEquals("Should get the value of org.apache.camel.test", globalOptions.get("org.apache.camel.test"), "this is a test first");
    }
    
    public void testCamelTemplates() throws Exception {
        DefaultProducerTemplate producer1 = getMandatoryBean(DefaultProducerTemplate.class, "producer1");
        assertEquals("Inject a wrong camel context", producer1.getCamelContext().getName(), "camel1");
        
        DefaultProducerTemplate producer2 = getMandatoryBean(DefaultProducerTemplate.class, "producer2");
        assertEquals("Inject a wrong camel context", producer2.getCamelContext().getName(), "camel2");
        
        DefaultConsumerTemplate consumer = getMandatoryBean(DefaultConsumerTemplate.class, "consumer");
        assertEquals("Inject a wrong camel context", consumer.getCamelContext().getName(), "camel2");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        bean1 = getMandatoryBean(CamelContextAwareBean.class, "bean1");
    }
       

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/camelContextAwareBean.xml");
    }

}
