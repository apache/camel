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
package org.apache.camel.example.bam;

import javax.persistence.EntityManagerFactory;

import org.apache.camel.bam.ActivityBuilder;
import org.apache.camel.bam.ProcessBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import static org.apache.camel.util.Time.seconds;

/**
 * @version 
 */
// START SNIPPET: demo
public class MyActivities extends ProcessBuilder {

    public MyActivities() {
    }

    public MyActivities(EntityManagerFactory entityManagerFactory, TransactionTemplate transactionTemplate) {
        super(entityManagerFactory, transactionTemplate);
    }

    public void configure() throws Exception {

        // let's define some activities, correlating on an XPath on the message bodies
        ActivityBuilder purchaseOrder = activity("file:src/data/purchaseOrders?noop=true")
                .correlate(xpath("/purchaseOrder/@id").stringResult());

        ActivityBuilder invoice = activity("file:src/data/invoices?noop=true&consumer.initialDelay=5000")
                .correlate(xpath("/invoice/@purchaseOrderId").stringResult());

        // now let's add some BAM rules
        invoice.starts().after(purchaseOrder.completes())
                .expectWithin(seconds(10))
                .errorIfOver(seconds(20)).to("log:org.apache.camel.example.bam.BamFailures?level=error");
    }
}
// END SNIPPET: demo
