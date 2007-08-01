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
package org.apache.camel.example.bam;

import org.apache.camel.bam.ActivityBuilder;
import org.apache.camel.bam.ProcessBuilder;
import static org.apache.camel.builder.xml.XPathBuilder.xpath;
import static org.apache.camel.util.Time.seconds;
import org.apache.camel.Processor;
import org.apache.camel.Exchange;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;
import org.springframework.orm.jpa.JpaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @version $Revision: $
 */
// START SNIPPET: example
public class MyActivities extends ProcessBuilder {
    private static final Log log = LogFactory.getLog(MyActivities.class);

    protected MyActivities(JpaTemplate jpaTemplate, TransactionTemplate transactionTemplate) {
        super(jpaTemplate, transactionTemplate);
    }

    public void configure() throws Exception {

        // lets define some activities, correlating on an XPath on the message bodies
        ActivityBuilder purchaseOrder = activity("file:src/data/purchaseOrders?noop=true")
                .correlate(xpath("/purchaseOrder/@id"));

        ActivityBuilder invoice = activity("file:src/data/invoices?noop=true")
                .correlate(xpath("/invoice/@purchaseOrderId"));

        // now lets add some rules
        invoice.starts().after(purchaseOrder.completes())
                .expectWithin(seconds(1))
                .errorIfOver(seconds(2)).to("log:org.apache.camel.example.bam.BamFailures?level=error");

        from("seda:failures").process(new Processor() {
            public void process(Exchange exchange) throws Exception {
                log.info("Failed process!: " + exchange + " with body: " + exchange.getIn().getBody());
            }
        });
    }
};
// END SNIPPET: example