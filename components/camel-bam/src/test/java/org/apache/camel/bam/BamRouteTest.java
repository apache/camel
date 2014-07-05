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
package org.apache.camel.bam;

import javax.persistence.EntityManagerFactory;

import org.apache.camel.bam.model.ActivityState;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Before;
import org.junit.Test;

import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

import static org.apache.camel.util.Time.seconds;

/**
 * @version 
 */
public class BamRouteTest extends CamelSpringTestSupport {
    protected MockEndpoint overdueEndpoint;
    protected int errorTimeout = 2;

    @Test
    public void testBam() throws Exception {
        overdueEndpoint.expectedMessageCount(1);

        template.sendBody("seda:a", "<hello id='123'>world!</hello>");

        overdueEndpoint.assertIsSatisfied();

        // it was b that was the problem and thus send to the overdue endpoint
        ActivityState state = overdueEndpoint.getExchanges().get(0).getIn().getBody(ActivityState.class);
        assertNotNull(state);
        assertEquals("123", state.getCorrelationKey());
        assertEquals("b", state.getActivityDefinition().getName());
    }

    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("META-INF/spring/spring.xml");
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();

        overdueEndpoint = getMockEndpoint("mock:overdue");
        overdueEndpoint.setResultWaitTime(20000);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        EntityManagerFactory entityManagerFactory = getMandatoryBean(EntityManagerFactory.class, "entityManagerFactory");
        TransactionTemplate transactionTemplate = getMandatoryBean(TransactionTemplate.class, "transactionTemplate");

        // START SNIPPET: example
        return new ProcessBuilder(entityManagerFactory, transactionTemplate) {
            public void configure() throws Exception {

                // let's define some activities, correlating on an XPath on the message bodies
                ActivityBuilder a = activity("seda:a").name("a")
                        .correlate(xpath("/hello/@id"));

                ActivityBuilder b = activity("seda:b").name("b")
                        .correlate(xpath("/hello/@id"));

                // now let's add some rules
                b.starts().after(a.completes())
                        .expectWithin(seconds(1))
                        .errorIfOver(seconds(errorTimeout)).to("mock:overdue");
            }
        };
        // END SNIPPET: example
    }

}
