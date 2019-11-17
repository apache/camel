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
package org.apache.camel.processor.jpa;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.examples.SendEmail;
import org.apache.camel.spring.SpringRouteBuilder;
import org.apache.camel.util.ObjectHelper;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.orm.jpa.LocalEntityManagerFactoryBean;

import static org.hamcrest.CoreMatchers.equalTo;

public class JpaRouteSharedEntityManagerTest extends AbstractJpaTest {
    protected static final String SELECT_ALL_STRING = "select x from " + SendEmail.class.getName() + " x";
    private CountDownLatch latch = new CountDownLatch(1);

    @Override
    @Before
    public void setUp() throws Exception {
        // Don't run on Hibernate
        Assume.assumeTrue(ObjectHelper.loadClass("org.hibernate.Hibernate") == null);
        super.setUp();
    }

    @Test
    public void testRouteJpaShared() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        int countStart = getBrokerCount();
        assertThat("brokerCount", countStart, equalTo(1));

        template.sendBody("direct:startShared", new SendEmail("one@somewhere.org"));
        // start route
        context.getRouteController().startRoute("jpaShared");

        // not the cleanest way to check the number of open connections
        int countEnd = getBrokerCount();
        assertThat("brokerCount", countEnd, equalTo(1));
        
        latch.countDown();

        assertMockEndpointsSatisfied();
    }

    private int getBrokerCount() {
        LocalEntityManagerFactoryBean entityManagerFactory = applicationContext.getBean("&entityManagerFactory", LocalEntityManagerFactoryBean.class);

        //uses Spring EL so we don't need to reference the classes
        StandardEvaluationContext context = new StandardEvaluationContext(entityManagerFactory);
        context.setBeanResolver(new BeanFactoryResolver(applicationContext));
        SpelExpressionParser parser = new SpelExpressionParser();
        Expression expression = parser.parseExpression("nativeEntityManagerFactory.brokerFactory.openBrokers"); 
        List<?> brokers = expression.getValue(context, List.class);

        return brokers.size();
    }
    
    @Test
    public void testRouteJpaNotShared() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:startNotshared", new SendEmail("one@somewhere.org"));

        int countStart = getBrokerCount();
        assertThat("brokerCount", countStart, equalTo(1));

        // start route
        context.getRouteController().startRoute("jpaOwn");

        // not the cleanest way to check the number of open connections
        int countEnd = getBrokerCount();
        assertThat("brokerCount", countEnd, equalTo(2));
        
        latch.countDown();

        assertMockEndpointsSatisfied();
    }    

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new SpringRouteBuilder() {
            public void configure() {
                from("direct:startNotshared").to("jpa://" + SendEmail.class.getName() + "?");
                from("direct:startShared").to("jpa://" + SendEmail.class.getName() + "?sharedEntityManager=true&joinTransaction=false");
                from("jpa://" + SendEmail.class.getName() + "?sharedEntityManager=true&joinTransaction=false").routeId("jpaShared").autoStartup(false).process(new LatchProcessor()).to("mock:result");
                from("jpa://" + SendEmail.class.getName() + "?sharedEntityManager=false").routeId("jpaOwn").autoStartup(false).process(new LatchProcessor()).to("mock:result");
            }
        };
    }

    @Override
    protected String routeXml() {
        return "org/apache/camel/processor/jpa/springJpaRouteTest.xml";
    }

    @Override
    protected String selectAllString() {
        return SELECT_ALL_STRING;
    }
    
    private class LatchProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            latch.await(2, TimeUnit.SECONDS);
        }
    }
}

