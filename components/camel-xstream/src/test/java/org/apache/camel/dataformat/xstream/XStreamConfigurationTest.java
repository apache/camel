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
package org.apache.camel.dataformat.xstream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.json.JsonHierarchicalStreamDriver;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.dataformat.XStreamDataFormat;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

/**
 * Marshal tests with domain objects.
 */
public class XStreamConfigurationTest extends CamelTestSupport {

    private static volatile boolean constructorInjected;
    private static volatile boolean methodInjected;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        constructorInjected = false;
        methodInjected = false;
    }

    @Test
    public void testCustomMarshalDomainObject() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);

        PurchaseOrder order = new PurchaseOrder();
        order.setName("Tiger");
        order.setAmount(1);
        order.setPrice(99.95);
        
        String ordereString = "<?xml version='1.0' encoding='UTF-8'?>" + "<purchase-order name=\"Tiger\" price=\"99.95\" amount=\"1.0\"/>";
        mock.expectedBodiesReceived(new Object[] {ordereString, order});

        template.sendBody("direct:marshal", order);
        template.sendBody("direct:unmarshal", ordereString);

        mock.assertIsSatisfied();
    }

    @Test
    public void testCustomMarshalDomainObjectWithImplicit() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);

        PurchaseHistory history = new PurchaseHistory();
        List<Double> list = new ArrayList<>();
        list.add(11.5);
        list.add(97.5);
        history.setHistory(list);

        String ordereString = "<?xml version='1.0' encoding='UTF-8'?>" + "<org.apache.camel.dataformat.xstream.PurchaseHistory>"
                + "<double>11.5</double><double>97.5</double>" + "</org.apache.camel.dataformat.xstream.PurchaseHistory>";
        mock.expectedBodiesReceived(new Object[] {ordereString, history});

        template.sendBody("direct:marshal", history);
        template.sendBody("direct:unmarshal", ordereString);

        mock.assertIsSatisfied();
    }

    @Test
    public void testCustomMarshalDomainObjectJson() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);

        PurchaseOrder order = new PurchaseOrder();
        order.setName("Tiger");
        order.setAmount(1);
        order.setPrice(99.95);
        
        String ordereString = "{\"purchase-order\":{\"@name\":\"Tiger\",\"@price\":99.95,\"@amount\":1}}";
        mock.expectedBodiesReceived(new Object[] {ordereString, order});

        template.sendBody("direct:marshal-json", order);
        template.sendBody("direct:unmarshal-json", ordereString);

        mock.assertIsSatisfied();
    }
    
    @Test
    public void testCustomXStreamDriverMarshal() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        PurchaseOrder order = new PurchaseOrder();
        order.setName("Tiger");
        order.setAmount(1);
        order.setPrice(99.95);
                
        template.sendBody("direct:myDriver", order);
        mock.assertIsSatisfied();
        String result = mock.getExchanges().get(0).getIn().getBody(String.class);
        // make sure the result is start with "{"
        assertTrue("Should get a json result", result.startsWith("{"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                XStreamDataFormat xstreamDefinition = new XStreamDataFormat();
                Map<String, String> aliases = new HashMap<>();
                aliases.put("purchase-order", PurchaseOrder.class.getName());
                xstreamDefinition.setAliases(aliases);
                xstreamDefinition.setPermissions(PurchaseOrder.class, PurchaseHistory.class);

                Map<String, String> converters = new HashMap<>();
                converters.put("1", PurchaseOrderConverter.class.getName());
                converters.put("2", CheckMethodInjection.class.getName());
                converters.put("3", CheckConstructorInjection.class.getName());

                xstreamDefinition.setConverters(converters);

                Map<String, String> implicits = new HashMap<>();
                implicits.put(PurchaseHistory.class.getName(), "history");
                xstreamDefinition.setImplicitCollections(implicits);

                from("direct:marshal").marshal(xstreamDefinition).to("mock:result");
                from("direct:unmarshal").unmarshal(xstreamDefinition).to("mock:result");

                xstreamDefinition = new XStreamDataFormat();
                xstreamDefinition.setDriver("json");
                aliases = new HashMap<>();
                aliases.put("purchase-order", PurchaseOrder.class.getName());
                xstreamDefinition.setAliases(aliases);
                xstreamDefinition.setPermissions(PurchaseOrder.class, PurchaseHistory.class);

                converters = new HashMap<>();
                converters.put("1", PurchaseOrderConverter.class.getName());

                xstreamDefinition.setConverters(converters);
                from("direct:marshal-json").marshal(xstreamDefinition).to("mock:result");
                from("direct:unmarshal-json").unmarshal(xstreamDefinition).to("mock:result");
                
                org.apache.camel.dataformat.xstream.XStreamDataFormat xStreamDataFormat 
                    = new org.apache.camel.dataformat.xstream.XStreamDataFormat();
                xStreamDataFormat.setXstreamDriver(new JsonHierarchicalStreamDriver());
                xStreamDataFormat.setPermissions("+6org.apache.camel.dataformat.xstream.*");

                from("direct:myDriver").marshal(xStreamDataFormat).to("mock:result");
            }
        };
    }

    public static class PurchaseOrderConverter implements Converter {

        @Override
        @SuppressWarnings("rawtypes")
        public boolean canConvert(Class type) {
            return PurchaseOrder.class.isAssignableFrom(type);
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            PurchaseOrder order = new PurchaseOrder();
            order.setName(reader.getAttribute("name"));
            order.setPrice(Double.parseDouble(reader.getAttribute("price")));
            order.setAmount(Double.parseDouble(reader.getAttribute("amount")));
            return order;
        }

        @Override
        public void marshal(Object object, HierarchicalStreamWriter writer, MarshallingContext context) {

            writer.addAttribute("name", ((PurchaseOrder) object).getName());
            writer.addAttribute("price", Double.toString(((PurchaseOrder) object).getPrice()));
            writer.addAttribute("amount", Double.toString(((PurchaseOrder) object).getAmount()));
        }
    }

    public static class CheckConstructorInjection implements Converter {
        public CheckConstructorInjection(XStream xstream) {
            if (xstream != null) {
                constructorInjected = true;
            } else {
                throw new RuntimeException("XStream should not be null");
            }
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            return null;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public boolean canConvert(Class type) {
            return false;
        }
    }

    public static class CheckMethodInjection implements Converter {
        public CheckMethodInjection() {

        }

        public void setXStream(XStream xstream) {
            if (xstream != null) {
                methodInjected = true;
            } else {
                throw new RuntimeException("XStream should not be null");
            }
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            return null;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public boolean canConvert(Class type) {
            return false;
        }
    }
}
