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
package org.apache.camel.component.jpa;

import org.apache.camel.Exchange;
import org.apache.camel.examples.MultiSteps;

/**
 * @version 
 */
public class JpaWithNativeQueryWithResultClassTest extends JpaWithNamedQueryTest {
    
    /**
     * We receive a MultiSteps object, because we call entityManager.createNativeQuery(nativeQuery, MultiSteps.class)
     */
    @Override
    protected void assertReceivedResult(Exchange exchange) {
        assertNotNull(exchange);
        MultiSteps result = (MultiSteps) exchange.getIn().getBody();
        assertNotNull("Received an object array", result);
        assertEquals("address property", "foo@bar.com", result.getAddress());
    }
    
    /**
     * Is still 1, because we receive an object array which has no @Consumed annotation
     * as the MultiSteps class has.
     */
    @Override
    protected int getUpdatedStepValue() {
        return 2;
    }
    
    @Override
    protected void assertURIQueryOption(JpaConsumer jpaConsumer) {
        assertEquals("select * from MultiSteps where step = 1", jpaConsumer.getNativeQuery());
    }

    @Override
    protected String getEndpointUri() {
        return "jpa://" + MultiSteps.class.getName() + "?consumer.resultClass=org.apache.camel.examples.MultiSteps&consumer.nativeQuery=select * from MultiSteps where step = 1";
    }
}