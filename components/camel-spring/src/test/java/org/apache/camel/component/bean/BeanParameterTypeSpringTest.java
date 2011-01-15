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
package org.apache.camel.component.bean;

import java.io.ByteArrayInputStream;
import java.io.StringReader;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version $Revision: 736324 $
 */
public class BeanParameterTypeSpringTest extends SpringTestSupport {
    
    public void testBeanClass() throws Exception {
        beanWithParameterType("direct:beanClass", "mock:beanClassResult");
    }
    
    public void testBeanInstance() throws Exception {
        beanWithParameterType("direct:beanInstance", "mock:beanInstanceResult");
    }
    
    public void testFilterWithBeanClass() throws Exception {
        filterWithParameterType("direct:filterClass", "mock:filterClassResult");
    }
    
    public void testFilterWithBeanInstance() throws Exception {
        filterWithParameterType("direct:filterInstance", "mock:filterInstanceResult");
    }
    
    protected void beanWithParameterType(String startEndpoint, String mockEndpoint) throws Exception {
        MockEndpoint result = resolveMandatoryEndpoint(mockEndpoint, MockEndpoint.class);
        result.expectedBodiesReceived("String", "String", "String", "String");

        template.sendBody(startEndpoint, (Object) "Camel");
        template.sendBody(startEndpoint, new ByteArrayInputStream("Camel".getBytes()));
        template.sendBody(startEndpoint, new StringReader("Camel"));
        template.sendBody(startEndpoint, "Camel");

        result.assertIsSatisfied();
    }
    
    protected void filterWithParameterType(String startEndpoint, String mockEndpoint) throws Exception {
        MockEndpoint result = resolveMandatoryEndpoint(mockEndpoint, MockEndpoint.class);
        result.expectedMessageCount(4);
        
        template.sendBody(startEndpoint, (Object) "Camel");
        template.sendBody(startEndpoint, new ByteArrayInputStream("Camel".getBytes()));
        template.sendBody(startEndpoint, new StringReader("Camel"));
        template.sendBody(startEndpoint, "Camel");

        result.assertIsSatisfied();
    }
    
    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/bean/BeanParameterTypeSpringTest-context.xml");
    }
}