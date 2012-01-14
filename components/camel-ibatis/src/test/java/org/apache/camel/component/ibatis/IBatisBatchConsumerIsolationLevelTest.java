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
package org.apache.camel.component.ibatis;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ibatis.strategy.IBatisProcessingStrategy;
import org.apache.camel.component.ibatis.strategy.TransactionIsolationLevel;
import org.easymock.EasyMock;
import org.junit.Test;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

public class IBatisBatchConsumerIsolationLevelTest extends IBatisTestSupport {

    IBatisProcessingStrategy strategyMock = createMock(IBatisProcessingStrategy.class);

    protected boolean createTestData() {
        return false;
    }

    protected String getStatement() {
        return "create table ACCOUNT ( ACC_ID INTEGER , ACC_FIRST_NAME VARCHAR(255), ACC_LAST_NAME VARCHAR(255), ACC_EMAIL VARCHAR(255), PROCESSED BOOLEAN DEFAULT false)";
    }

    @Test
    public void testConsumeWithIsolation() throws Exception {
        Account account1 = new Account();
        account1.setId(1);
        account1.setFirstName("Bob");
        account1.setLastName("Denver");
        account1.setEmailAddress("TryGuessingGilligan@gmail.com");

        Account account2 = new Account();
        account2.setId(2);
        account2.setFirstName("Alan");
        account2.setLastName("Hale");
        account2.setEmailAddress("TryGuessingSkipper@gmail.com");

        List<Object> accounts = new ArrayList<Object>();
        accounts.add(account1);
        accounts.add(account2);

        strategyMock.setIsolation(TransactionIsolationLevel.TRANSACTION_READ_COMMITTED.getValue());
        expectLastCall().once();

        expect(strategyMock.poll(EasyMock.<IBatisConsumer>anyObject(), EasyMock.<IBatisEndpoint>anyObject())).andReturn(accounts).atLeastOnce();

        strategyMock.commit(EasyMock.<IBatisEndpoint>anyObject(), EasyMock.<Exchange>anyObject(), anyObject(), EasyMock.<String>anyObject());
        expectLastCall().atLeastOnce();
        replay(strategyMock);

        IBatisEndpoint iBatisEndpoint = resolveMandatoryEndpoint("ibatis:selectUnprocessedAccounts?consumer.onConsume=consumeAccount", IBatisEndpoint.class);
        iBatisEndpoint.setStrategy(strategyMock);
        iBatisEndpoint.setIsolation("TRANSACTION_READ_COMMITTED");


        template.sendBody("direct:start", account1);
        template.sendBody("direct:start", account2);
        //We need to wait for the batch process to complete.
        Thread.sleep(1000);
        verify(strategyMock);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("ibatis:selectUnprocessedAccounts?consumer.onConsume=consumeAccount").to("mock:results");
                // END SNIPPET: e1

                from("direct:start").to("ibatis:insertAccount?statementType=Insert");
            }
        };
    }
}