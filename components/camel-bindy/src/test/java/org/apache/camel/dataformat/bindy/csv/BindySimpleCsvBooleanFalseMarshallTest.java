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
package org.apache.camel.dataformat.bindy.csv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.model.simple.bool.BooleanExample;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration
public class BindySimpleCsvBooleanFalseMarshallTest extends AbstractJUnit4SpringContextTests {

    private List<Map<String, Object>> models = new ArrayList<>();
    private String result = "andrew,false\r\n";

    @Produce("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint resultEndpoint;

    @Test
    public void testMarshallMessage() throws Exception {
        resultEndpoint.expectedBodiesReceived(result);

        template.sendBody(generateModel());

        resultEndpoint.assertIsSatisfied();
    }


    public List<Map<String, Object>> generateModel() {
        Map<String, Object> modelObjects = new HashMap<>();
        
        BooleanExample example = new BooleanExample();
        
        example.setName("andrew");
        example.setExist(Boolean.FALSE);

        modelObjects.put(example.getClass().getName(), example);

        models.add(modelObjects);

        return models;
    }

    public static class ContextConfig extends RouteBuilder {

        @Override
        public void configure() {
            BindyCsvDataFormat camelDataFormat = new BindyCsvDataFormat(
                org.apache.camel.dataformat.bindy.model.simple.bool.BooleanExample.class);
            
            camelDataFormat.setLocale("en");

            from("direct:start").marshal(camelDataFormat).to("mock:result");
        }
        
    }

}
