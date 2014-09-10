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
package org.apache.camel.component.log;

import java.lang.reflect.Field;

import org.apache.camel.Endpoint;
import org.apache.camel.processor.CamelLogProcessor;
import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class CustomExchangeFormatterTest extends SpringTestSupport {

    public void testExchangeFormattersConfiguredProperly() throws Exception {
        // the exchangeFormatter field is not a property, therefore we have to inspect it via reflection
        Field f = CamelLogProcessor.class.getDeclaredField("formatter");
        f.setAccessible(true);
        
        TestExchangeFormatter aaa = null;
        TestExchangeFormatter bbb = null;
        for (Endpoint ep : context.getEndpoints()) {
            if (!(ep instanceof LogEndpoint)) {
                continue;
            }
            
            LogEndpoint log = (LogEndpoint) ep;
            
            aaa = log.getEndpointUri().contains("aaa") ? 
                    (TestExchangeFormatter) f.get(((CamelLogProcessor) log.getLogger())) : aaa;
            bbb = log.getEndpointUri().contains("bbb") ? 
                    (TestExchangeFormatter) f.get(((CamelLogProcessor) log.getLogger())) : bbb;
        }
        
        assertNotNull(aaa);
        assertNotNull(bbb);
        assertNotSame(aaa, bbb);
        assertEquals("aaa", aaa.getTestProperty());
        assertEquals("bbb", bbb.getTestProperty());
    }

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/log/custom-exchange-formatter-context.xml");
    }
}