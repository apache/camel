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
package org.apache.camel.spring.config;

import org.apache.camel.util.IOHelper;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class AnotherCamelProxyTest extends Assert {

    @Test
    public void testAnotherCamelProxy() throws Exception {
        // START SNIPPET: e1
        AbstractApplicationContext ac = new ClassPathXmlApplicationContext("org/apache/camel/spring/config/AnotherCamelProxyTest.xml");

        MyProxySender sender = ac.getBean("myProxySender", MyProxySender.class);
        String reply = sender.hello("Camel");

        assertEquals("Bye Camel", reply);

        // we're done so let's properly close the application context
        IOHelper.close(ac);
        // END SNIPPET: e1
    }

}