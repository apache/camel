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
package org.apache.camel.component.aws.sns.integration;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws.sns.SnsConstants;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Must be manually tested. Provide your own accessKey and secretKey!")
public class SnsComponentIntegrationTest extends CamelTestSupport {
    
    @Test
    public void sendInOnly() throws Exception {
        Exchange exchange = template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(SnsConstants.SUBJECT, "This is my subject");
                exchange.getIn().setBody("This is my message text.");
            }
        });
        
        assertNotNull(exchange.getIn().getHeader(SnsConstants.MESSAGE_ID));
    }
    
    @Test
    public void sendInOut() throws Exception {
        Exchange exchange = template.send("direct:start", ExchangePattern.InOut, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(SnsConstants.SUBJECT, "This is my subject");
                exchange.getIn().setBody("This is my message text.");
            }
        });
        
        assertNotNull(exchange.getMessage().getHeader(SnsConstants.MESSAGE_ID));
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("aws-sns://MyNewTopic?accessKey=xxx&secretKey=yyy&policy=%7B%22Version%22%3A%222008-10-17%22,%22Statement%22%3A%5B%7B%22Sid%22%3A%221%22,%22Effect%22%3A%22Allow%22,"
                            + "%22Principal%22%3A%7B%22AWS%22%3A%5B%22*%22%5D%7D,%22Action%22%3A%5B%22sns%3ASubscribe%22%5D%7D%5D%7D&subject=The+subject+message");
            }
        };
    }
}
