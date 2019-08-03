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

import java.io.File;
import java.util.List;

import javax.persistence.Query;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.idempotent.jpa.MessageProcessed;
import org.junit.Before;
import org.junit.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;

/**
 * Unit test using jpa idempotent repository for the file consumer.
 */
public class FileConsumerJpaIdempotentTest extends AbstractJpaTest {

    protected static final String SELECT_ALL_STRING = "select x from " + MessageProcessed.class.getName() + " x where x.processorName = ?1";
    protected static final String PROCESSOR_NAME = "FileConsumer";

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/idempotent");
        super.setUp();
        template.sendBodyAndHeader("file://target/idempotent/", "Hello World", Exchange.FILE_NAME, "report.txt");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("file://target/idempotent/?idempotent=true&idempotentRepository=#jpaStore&move=done/${file:name}").routeId("foo").autoStartup(false)
                    .to("mock:result");
            }
        };
    }

    @Override
    protected void cleanupRepository() {
        transactionTemplate.execute(new TransactionCallback<Object>() {
            public Object doInTransaction(TransactionStatus arg0) {
                entityManager.joinTransaction();
                Query query = entityManager.createQuery(SELECT_ALL_STRING);
                query.setParameter(1, PROCESSOR_NAME);
                List<?> list = query.getResultList();
                for (Object item : list) {
                    entityManager.remove(item);
                }
                entityManager.flush();
                return Boolean.TRUE;
            }
        });
    }

    @Test
    public void testFileConsumerJpaIdempotent() throws Exception {
        // consume the file the first time
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedMessageCount(1);

        context.getRouteController().startRoute("foo");

        assertMockEndpointsSatisfied();

        Thread.sleep(1000);

        // reset mock and set new expectations
        mock.reset();
        mock.expectedMessageCount(0);

        // move file back
        File file = new File("target/idempotent/done/report.txt");
        File renamed = new File("target/idempotent/report.txt");
        file.renameTo(renamed);

        // should NOT consume the file again, let 2 secs pass to let the consumer try to consume it but it should not
        Thread.sleep(2000);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected String routeXml() {
        return "org/apache/camel/processor/jpa/fileConsumerJpaIdempotentTest-config.xml";
    }

    @Override
    protected String selectAllString() {
        return SELECT_ALL_STRING;
    }

}
