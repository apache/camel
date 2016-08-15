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
package org.apache.camel.processor.jpa;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jpa.JpaEndpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.examples.SendEmail;
import org.apache.camel.spring.SpringRouteBuilder;
import org.junit.Test;

/**
 * @version 
 */
public class JpaProducerRemoveTest extends AbstractJpaTest {
    protected static final String SELECT_ALL_STRING = "select x from " + SendEmail.class.getName() + " x";

    @Test
    public void testRouteJpa() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        context.startRoute("foo");
        context.startRoute("foo1");
        JpaEndpoint jpa = context.getEndpoint("jpa://" + SendEmail.class.getName(), JpaEndpoint.class);
        EntityManagerFactory emf = jpa.getEntityManagerFactory();

        EntityManager entityManager = emf.createEntityManager();
        template.sendBody("direct:start", new SendEmail("foo@beer.org"));
        Exchange exchange = mock.getReceivedExchanges().get(0);
        SendEmail persistedEntity = exchange.getIn().getBody(SendEmail.class);
        SendEmail emfindEntity = entityManager.find(SendEmail.class, persistedEntity.getId());
        assertNotSame(emfindEntity, persistedEntity);
        entityManager.close();
        mock.reset();

        entityManager = emf.createEntityManager();
        template.sendBody("direct:remove", persistedEntity);
        exchange = mock.getReceivedExchanges().get(0);
        persistedEntity = exchange.getIn().getBody(SendEmail.class);
        emfindEntity = entityManager.find(SendEmail.class, persistedEntity.getId());
        assertNull(emfindEntity);
        entityManager.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new SpringRouteBuilder() {
            public void configure() {
                from("direct:start")
                    .id("foo")
                    .to("jpa://" + SendEmail.class.getName() + "?usePassedInEntityManager=true")
                    .to("mock:result");
                from("direct:remove")
                    .id("foo1")
                    .to("jpa://" + SendEmail.class.getName() + "?remove=true&usePassedInEntityManager=true")
                    .to("mock:result");
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

}
