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

import org.junit.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @version 
 */
public class MultipleProcessesTest extends BamRouteTest {
    
    @Override
    @Test
    public void testBam() throws Exception {
        // TODO fixme
        //overdueEndpoint.expectedMessageCount(1);
        overdueEndpoint.expectedMinimumMessageCount(1);
        //overdueEndpoint.message(0).predicate().el("${in.body.correlationKey == '124'}");

        sendAMessages();
        sendBMessages();

        overdueEndpoint.assertIsSatisfied();
    }

    protected void sendAMessages() {
        TransactionTemplate transaction = getMandatoryBean(TransactionTemplate.class, "transactionTemplate");
        transaction.execute(new TransactionCallbackWithoutResult() {
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                template.sendBody("seda:a", "<hello id='123'>A</hello>");
                template.sendBody("seda:a", "<hello id='124'>B</hello>");
                template.sendBody("seda:a", "<hello id='125'>C</hello>");
            }
        });
    }

    protected void sendBMessages() {
        TransactionTemplate transaction = getMandatoryBean(TransactionTemplate.class, "transactionTemplate");
        transaction.execute(new TransactionCallbackWithoutResult() {
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                template.sendBody("seda:b", "<hello id='123'>A</hello>");
                template.sendBody("seda:b", "<hello id='125'>C</hello>");
            }
        });
    }
}
