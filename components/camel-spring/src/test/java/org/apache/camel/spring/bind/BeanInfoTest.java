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
package org.apache.camel.spring.bind;

import junit.framework.TestCase;

import org.apache.camel.Exchange;
import org.apache.camel.component.bean.BeanInfo;
import org.apache.camel.component.bean.BeanProcessor;
import org.apache.camel.component.bean.DefaultParameterMappingStrategy;
import org.apache.camel.component.bean.MethodInvocation;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Revision$
 */
public class BeanInfoTest extends TestCase {
    private static final Log LOG = LogFactory.getLog(BeanInfoTest.class);
    protected DefaultCamelContext camelContext = new DefaultCamelContext();
    protected Exchange exchange = new DefaultExchange(camelContext);
    protected DefaultParameterMappingStrategy strategy = new DefaultParameterMappingStrategy();
    protected ExampleBean bean = new ExampleBean();
    protected BeanInfo info;



    public void testFindsSingleMethodMatchingBody() throws Throwable {
        MethodInvocation invocation = info.createInvocation(bean, exchange);
        assertNotNull("Should have found a method invocation!", invocation);

        Object value = invocation.proceed();

        LOG.info("Value: " + value);
    }

    public void testBeanProcessor() throws Exception {
        BeanProcessor processor = new BeanProcessor(bean, info);
        processor.process(exchange);

        LOG.info("Exchange is: " + exchange);
    }

    protected void setUp() throws Exception {
        super.setUp();
        exchange.getIn().setBody("James");
        info = new BeanInfo(camelContext, bean.getClass(), strategy);
    }
}
