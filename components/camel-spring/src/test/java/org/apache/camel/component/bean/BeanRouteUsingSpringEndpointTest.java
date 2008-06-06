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

import javax.annotation.Resource;

import org.apache.camel.Endpoint;
import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit38.AbstractJUnit38SpringContextTests;

/**
 * @version $Revision$
 */
@ContextConfiguration
public class BeanRouteUsingSpringEndpointTest extends AbstractJUnit38SpringContextTests {
    @Autowired
    protected ProducerTemplate template;
    @Resource
    protected Endpoint helloEndpoint;
    @Resource
    protected Endpoint goodbyeEndpoint;

    protected String body = "James";

    public void testSayHello() throws Exception {
        assertNotNull(helloEndpoint);
        assertNotNull(goodbyeEndpoint);

        Object value = template.sendBody(helloEndpoint, body);

        assertEquals("Returned value", "Hello James!", value);
    }

    public void testSayGoodbye() throws Exception {
        Object value = template.sendBody(goodbyeEndpoint, body);

        assertEquals("Returned value", "Bye James!", value);
    }

}
