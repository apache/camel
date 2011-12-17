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
package org.apache.camel.component.aws.sdb.integration;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws.sdb.SdbConstants;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Must be manually tested. Provide your own accessKey and secretKey!")
public class SdbComponentIntegrationTest extends CamelTestSupport {

    @Test
    public void putItemFromMessageHeaders() throws Exception {
        Exchange exchange = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(SdbConstants.OPERATION, SdbConstants.OPERATION_PUT);
                exchange.getIn().setHeader(SdbConstants.ITEM_KEY, "ItemOne");
                exchange.getIn().setHeader(SdbConstants.ATTRIBUTE_PREFIX + "AttributeOne", "Value One");
                exchange.getIn().setHeader(SdbConstants.ATTRIBUTE_PREFIX + "AttributeTwo", "Value Two");
            }
        });

        assertNull("No exceptions during PUT operation", exchange.getException());

        exchange = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(SdbConstants.OPERATION, SdbConstants.OPERATION_GET);
                exchange.getIn().setHeader(SdbConstants.ITEM_KEY, "ItemOne");
            }
        });

        assertNull("No exceptions during GET operation", exchange.getException());
        assertEquals("Value One", exchange.getIn().getHeader("AttributeOne"));
        assertEquals("Value Two", exchange.getIn().getHeader("AttributeTwo"));

        exchange = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(SdbConstants.OPERATION, SdbConstants.OPERATION_DELETE);
                exchange.getIn().setHeader(SdbConstants.ITEM_KEY, "ItemOne");
            }
        });

        assertNull("No exceptions during DELETE operation", exchange.getException());
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("aws-sdb://TestDomain?accessKey=xxx&secretKey=yyy");
            }
        };
    }
}
