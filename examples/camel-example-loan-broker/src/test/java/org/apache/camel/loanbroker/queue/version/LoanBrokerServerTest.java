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
package org.apache.camel.loanbroker.queue.version;

import org.apache.camel.CamelContext;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class LoanBrokerServerTest extends Assert {
    
    @Test
    public void startLoanBroker() throws Exception {
        AbstractApplicationContext applicationContext = 
            new ClassPathXmlApplicationContext(new String[]{"/META-INF/spring/queueCamelContext.xml"});
        CamelContext camelContext = (CamelContext)applicationContext.getBean("myCamel");
        assertNotNull("The camel context should not be null", camelContext);
        Thread.sleep(2000);        
        camelContext.stop();
        applicationContext.stop();        
    }

}
