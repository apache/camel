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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.examples.VersionedItem;
import org.apache.camel.spring.SpringRouteBuilder;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @version
 */
@Ignore("Need the fix of OPENJPA-2461")
public class JpaRouteSkipLockedEntityTest extends AbstractJpaTest {
    protected static final String SELECT_ALL_STRING = "select x from " + VersionedItem.class.getName() + " x";
    
    private int count;
    private final ReentrantLock lock = new ReentrantLock();
    private Condition cond1 = lock.newCondition();
    
    @Test
    public void testRouteJpa() throws Exception {
        MockEndpoint mock1 = getMockEndpoint("mock:result1");
        mock1.expectedMessageCount(2);
        MockEndpoint mock2 = getMockEndpoint("mock:result2");
        mock2.expectedMessageCount(2);

        template.sendBody("jpa://" + VersionedItem.class.getName(), new VersionedItem("one"));
        template.sendBody("jpa://" + VersionedItem.class.getName(), new VersionedItem("two"));
        template.sendBody("jpa://" + VersionedItem.class.getName(), new VersionedItem("three"));
        template.sendBody("jpa://" + VersionedItem.class.getName(), new VersionedItem("four"));
        
        this.context.startRoute("second");
        this.context.startRoute("first");

        assertMockEndpointsSatisfied();
       
        //force test to wait till finished
        this.context.stopRoute("first");
        this.context.stopRoute("second");

        setLockTimeout(60);
        List<?> list = entityManager.createQuery(selectAllString()).getResultList();
        assertEquals(0, list.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new SpringRouteBuilder() {
            @Override
            public void configure() {
                String options = "?consumer.skipLockedEntity=true"; //&consumer.lockModeType=PESSIMISTIC_FORCE_INCREMENT";
                from("jpa://" + VersionedItem.class.getName() + options).routeId("first").autoStartup(false).bean(new WaitLatch()).log("route1: ${body}").to("mock:result1");
                from("jpa2://select" + options + "&consumer.query=select s from VersionedItem s").routeId("second").autoStartup(false).bean(new WaitLatch()).log("route2: ${body}").to("mock:result2");
            }
        };
    }

    @Override
    protected String routeXml() {
        return "org/apache/camel/processor/jpa/springJpaRouteSkipLockedTest.xml";
    }

    @Override
    protected String selectAllString() {
        return SELECT_ALL_STRING;
    }

    public class WaitLatch {
        public void onMessage(VersionedItem body) throws Exception {
            lock.lock();
            try {

                count++;
                // if (count != 1) {
                cond1.signal();
                // }

                // if not last
                if (count != 4) {
                    cond1.await();
                }
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        setLockTimeout(0);
    }

    public void setLockTimeout(int timeout) throws SQLException {
        entityManager.getTransaction().begin();
        Connection connection = entityManager.unwrap(java.sql.Connection.class);
        connection.createStatement().execute("CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY('derby.locks.waitTimeout', '" + timeout + "')");
        entityManager.getTransaction().commit();
    }

}
