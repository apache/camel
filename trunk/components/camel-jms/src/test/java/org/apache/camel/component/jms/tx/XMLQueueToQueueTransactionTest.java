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
package org.apache.camel.component.jms.tx;

import org.apache.xbean.spring.context.ClassPathXmlApplicationContext;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;


/**
 * Test case derived from:
 * http://camel.apache.org/transactional-client.html and Martin
 * Krasser's sample:
 * http://www.nabble.com/JMS-Transactions---How-To-td15168958s22882.html#a15198803
 * NOTE: had to split into separate test classes as I was unable to fully tear
 * down and isolate the test cases, I'm not sure why, but as soon as we know the
 * Transaction classes can be joined into one.
 */
public class XMLQueueToQueueTransactionTest extends AbstractTransactionTest {

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/jms/tx/XMLQueueToQueueTransactionTest.xml");
    }
    
    @Test
    public void testRollbackUsingXmlQueueToQueue() throws Exception {
        // routes should have been configured via xml and added to the camel context
        assertResult();
    }
}
