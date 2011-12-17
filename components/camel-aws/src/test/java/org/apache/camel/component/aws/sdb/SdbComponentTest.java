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
package org.apache.camel.component.aws.sdb;

import java.util.List;

import com.amazonaws.services.simpledb.model.NoSuchDomainException;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class SdbComponentTest extends CamelTestSupport {
    private AmazonSDBClientMock amazonSdbClient;

    @Test
    public void domainCreatedOnStart() throws Exception {
        assertEquals("TestDomain", amazonSdbClient.getDomainNameToCreate());
    }

    @Test
    public void putItemFromMessageHeaders() throws Exception {
        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(SdbConstants.OPERATION, SdbConstants.OPERATION_PUT);
                exchange.getIn().setHeader(SdbConstants.ITEM_KEY, "ItemOne");
                exchange.getIn().setHeader(SdbConstants.ATTRIBUTE_PREFIX + "AttributeOne", "Value One");
                exchange.getIn().setHeader(SdbConstants.ATTRIBUTE_PREFIX + "AttributeTwo", "Value Two");
            }
        });

        assertEquals("TestDomain", domainNameToBeCreated());
        assertEquals("ItemOne", itemNameToBeCreated());
        assertEquals("Value One", getAttributeValueFor("AttributeOne"));
        assertEquals("Value Two", getAttributeValueFor("AttributeTwo"));
        assertEquals(2, getAttributesSize());
    }

    @Test
    public void deleteItemByKey() throws Exception {
        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(SdbConstants.OPERATION, SdbConstants.OPERATION_DELETE);
                exchange.getIn().setHeader(SdbConstants.ITEM_KEY, "ItemOne");
            }
        });

        assertEquals("ItemOne", amazonSdbClient.getItemNameToDelete());
    }

    @Test
    public void getItemByKey() throws Exception {
        Exchange exchange = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(SdbConstants.OPERATION, SdbConstants.OPERATION_GET);
                exchange.getIn().setHeader(SdbConstants.ITEM_KEY, "ItemOne");
            }
        });

        assertEquals("Value One", exchange.getIn().getHeader("AttributeOne"));
        assertEquals("Value Two", exchange.getIn().getHeader("AttributeTwo"));
    }

    @Test
    public void deletingItemOnNonExistingDomainCauseException() throws Exception {
        Exchange exchange = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(SdbConstants.DOMAIN_NAME, "MissingDomain");
                exchange.getIn().setHeader(SdbConstants.OPERATION, SdbConstants.OPERATION_DELETE);
                exchange.getIn().setHeader(SdbConstants.ITEM_KEY, "ItemOne");
            }
        });

        Exception exception = exchange.getException();
        assertNotNull("NoSuchDomainException is missing", exception);
        assertTrue(exception instanceof NoSuchDomainException);
    }


    @Test
    public void itemKeyHeaderIsAlwaysRequired() throws Exception {
        Exchange exchange = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(SdbConstants.OPERATION, SdbConstants.OPERATION_PUT);
                exchange.getIn().setHeader(SdbConstants.ATTRIBUTE_PREFIX + "AttributeOne", "Value One");
            }
        });

        Exception exception = exchange.getException();
        assertNotNull("IllegalArgumentException is missing", exception);
        assertTrue(exception instanceof IllegalArgumentException);
    }

    private int getAttributesSize() {
        return amazonSdbClient.getPutAttributesRequest().getAttributes().size();
    }

    private String getAttributeValueFor(String attributeName) {
        List<ReplaceableAttribute> attributes = amazonSdbClient.getPutAttributesRequest().getAttributes();
        for (ReplaceableAttribute attribute : attributes) {
            if (attribute.getName().equals(attributeName)) {
                return attribute.getValue();
            }
        }
        return "Attribute Not Found" + attributeName;
    }

    private String itemNameToBeCreated() {
        return amazonSdbClient.getPutAttributesRequest().getItemName();
    }

    private String domainNameToBeCreated() {
        return amazonSdbClient.getPutAttributesRequest().getDomainName();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        amazonSdbClient = new AmazonSDBClientMock();
        registry.bind("amazonSdbClient", amazonSdbClient);
        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("aws-sdb://TestDomain?amazonSdbClient=#amazonSdbClient&operation=CamelAwsSdbGet");
            }
        };
    }
}
