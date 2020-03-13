/*
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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.spring.SpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class ProcessorAsEndpointTest extends SpringTestSupport {
    protected Object body = "<hello>world!</hello>";

    @Test
    public void testSendingToProcessorEndpoint() throws Exception {
        ProcessorStub processor = getMandatoryBean(ProcessorStub.class, "myProcessor");

        template.sendBody("bean:myProcessor", body);

        List<Exchange> list = processor.getExchanges();
        assertEquals("Received exchange list: " + list, 1, list.size());

        log.debug("Found exchanges: " + list);
    }

    @Test
    public void testSendingToNonExistentEndpoint() throws Exception {
        String uri = "unknownEndpoint";
        try {
            template.sendBody(uri, body);
            fail("We should have failed as this is a bad endpoint URI");
        } catch (NoSuchEndpointException e) {
            log.debug("Caught expected exception: " + e, e);
        }
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/bind/processorAsEndpoint.xml");
    }

}
