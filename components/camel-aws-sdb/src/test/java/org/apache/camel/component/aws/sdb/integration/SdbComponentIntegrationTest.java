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
package org.apache.camel.component.aws.sdb.integration;

import java.util.Arrays;
import java.util.List;

import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.DeletableItem;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import com.amazonaws.services.simpledb.model.ReplaceableItem;
import com.amazonaws.services.simpledb.model.UpdateCondition;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws.sdb.SdbConstants;
import org.apache.camel.component.aws.sdb.SdbOperations;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Must be manually tested. Provide your own accessKey and secretKey!")
public class SdbComponentIntegrationTest extends CamelTestSupport {
    
    @Test
    public void batchDeleteAttributes() {
        final List<DeletableItem> deletableItems = Arrays.asList(new DeletableItem[] {
            new DeletableItem("ITEM1", null),
            new DeletableItem("ITEM2", null)});
        
        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(SdbConstants.OPERATION, SdbOperations.BatchDeleteAttributes);
                exchange.getIn().setHeader(SdbConstants.DELETABLE_ITEMS, deletableItems);
            }
        });
    }
    
    @Test
    public void batchPutAttributes() {
        final List<ReplaceableItem> replaceableItems = Arrays.asList(new ReplaceableItem[] {
            new ReplaceableItem("ITEM1")});
        
        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(SdbConstants.OPERATION, SdbOperations.BatchPutAttributes);
                exchange.getIn().setHeader(SdbConstants.REPLACEABLE_ITEMS, replaceableItems);
            }
        });
    }
    
    @Test
    public void deleteAttributes() {
        final List<Attribute> attributes = Arrays.asList(new Attribute[] {
            new Attribute("NAME1", "VALUE1")});
        final UpdateCondition condition = new UpdateCondition("Key1", "Value1", true);
        
        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(SdbConstants.OPERATION, SdbOperations.DeleteAttributes);
                exchange.getIn().setHeader(SdbConstants.ATTRIBUTES, attributes);
                exchange.getIn().setHeader(SdbConstants.ITEM_NAME, "ITEM1");
                exchange.getIn().setHeader(SdbConstants.UPDATE_CONDITION, condition);
            }
        });
    }
    
    @Test
    public void deleteDomain() {
        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(SdbConstants.OPERATION, SdbOperations.DeleteDomain);
            }
        });
    }
    
    @Test
    public void domainMetadata() {
        Exchange exchange = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(SdbConstants.OPERATION, SdbOperations.DomainMetadata);
            }
        });
        
        assertNotNull(exchange.getIn().getHeader(SdbConstants.TIMESTAMP));
        assertNotNull(exchange.getIn().getHeader(SdbConstants.ITEM_COUNT));
        assertNotNull(exchange.getIn().getHeader(SdbConstants.ATTRIBUTE_NAME_COUNT));
        assertNotNull(exchange.getIn().getHeader(SdbConstants.ATTRIBUTE_VALUE_COUNT));
        assertNotNull(exchange.getIn().getHeader(SdbConstants.ATTRIBUTE_NAME_SIZE));
        assertNotNull(exchange.getIn().getHeader(SdbConstants.ATTRIBUTE_VALUE_SIZE));
        assertNotNull(exchange.getIn().getHeader(SdbConstants.ITEM_NAME_SIZE));
    }
    
    @Test
    public void getAttributes() {
        final List<String> attributeNames = Arrays.asList(new String[] {"ATTRIBUTE1"});
        
        Exchange exchange = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(SdbConstants.OPERATION, SdbOperations.GetAttributes);
                exchange.getIn().setHeader(SdbConstants.ITEM_NAME, "ITEM1");
                exchange.getIn().setHeader(SdbConstants.CONSISTENT_READ, Boolean.TRUE);
                exchange.getIn().setHeader(SdbConstants.ATTRIBUTE_NAMES, attributeNames);
            }
        });
        
        assertNotNull(exchange.getIn().getHeader(SdbConstants.ATTRIBUTES, List.class));
    }
    
    @Test
    public void listDomains() {
        Exchange exchange = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(SdbConstants.OPERATION, SdbOperations.ListDomains);
                exchange.getIn().setHeader(SdbConstants.MAX_NUMBER_OF_DOMAINS, new Integer(5));
                exchange.getIn().setHeader(SdbConstants.NEXT_TOKEN, "TOKEN1");
            }
        });
        
        assertNotNull(exchange.getIn().getHeader(SdbConstants.DOMAIN_NAMES, List.class));
    }
    
    @Test
    public void putAttributes() {
        final List<ReplaceableAttribute> replaceableAttributes = Arrays.asList(new ReplaceableAttribute[] {
            new ReplaceableAttribute("NAME1", "VALUE1", true)});
        final UpdateCondition updateCondition = new UpdateCondition("NAME1", "VALUE1", true);
        
        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(SdbConstants.OPERATION, SdbOperations.PutAttributes);
                exchange.getIn().setHeader(SdbConstants.ITEM_NAME, "ITEM1");
                exchange.getIn().setHeader(SdbConstants.UPDATE_CONDITION, updateCondition);
                exchange.getIn().setHeader(SdbConstants.REPLACEABLE_ATTRIBUTES, replaceableAttributes);
            }
        });
    }
    
    @Test
    public void select() {
        Exchange exchange = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(SdbConstants.OPERATION, SdbOperations.Select);
                exchange.getIn().setHeader(SdbConstants.NEXT_TOKEN, "TOKEN1");
                exchange.getIn().setHeader(SdbConstants.CONSISTENT_READ, Boolean.TRUE);
                exchange.getIn().setHeader(SdbConstants.SELECT_EXPRESSION, "SELECT NAME1 FROM DOMAIN1 WHERE NAME1 LIKE 'VALUE1'");
            }
        });
        
        assertNotNull(exchange.getIn().getHeader(SdbConstants.ITEMS, List.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("aws-sdb://TestDomain?accessKey=xxx&secretKey=yyy&operation=GetAttributes");
            }
        };
    }
}
