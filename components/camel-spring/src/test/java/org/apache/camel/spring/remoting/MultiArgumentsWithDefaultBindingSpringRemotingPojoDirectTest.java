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
package org.apache.camel.spring.remoting;

import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version
 */
public class MultiArgumentsWithDefaultBindingSpringRemotingPojoDirectTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/remoting/multi-arguments-with-default-binding-pojo-direct.xml");
    }

    public void testMultiArgumentPojo() throws Exception {
        try {
            // use the pojo directly to call the injected endpoint and have the
            // original runtime exception thrown
            MultiArgumentsWithDefaultBinding myMultArgumentPojo = applicationContext.getBean("multiArgumentsPojoDirect", MultiArgumentsWithDefaultBinding.class);
            myMultArgumentPojo.doSomethingMultiple();
        } catch (RuntimeException e) {
            fail(""
                    + "\nShould not have failed with multiple arguments on POJO @Produce @Consume."
                    + "\nValues are incorrect in the consume for doSomething(String arg1, String arg2, Date arg3)"
                    + "\nProduce called with doSomething(\"Hello World 1\", \"Hello World 2\", new Date())."
                    + "\nConsume got something else."
                    + "\n" + e.getMessage()
                    + "\n");
        }
    }

}
