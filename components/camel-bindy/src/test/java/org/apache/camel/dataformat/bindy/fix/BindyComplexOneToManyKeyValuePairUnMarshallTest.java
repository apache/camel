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
package org.apache.camel.dataformat.bindy.fix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.bindy.CommonBindyTest;
import org.apache.camel.dataformat.bindy.kvp.BindyKeyValuePairDataFormat;
import org.apache.camel.dataformat.bindy.model.fix.complex.onetomany.Header;
import org.apache.camel.dataformat.bindy.model.fix.complex.onetomany.Order;
import org.apache.camel.dataformat.bindy.model.fix.complex.onetomany.Security;
import org.apache.camel.dataformat.bindy.model.fix.complex.onetomany.Trailer;
import org.apache.camel.spring.javaconfig.SingleRouteCamelConfiguration;
import org.junit.Test;
import org.springframework.config.java.annotation.Bean;
import org.springframework.config.java.annotation.Configuration;
import org.springframework.config.java.test.JavaConfigContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;


@ContextConfiguration(locations = "org.apache.camel.dataformat.bindy.fix.BindyComplexOneToManyKeyValuePairUnMarshallTest$ContextConfig", loader = JavaConfigContextLoader.class)
public class BindyComplexOneToManyKeyValuePairUnMarshallTest extends CommonBindyTest {

    @Test
    @DirtiesContext
    public void testUnMarshallMessage() throws Exception {
    	
    	String message = "8=FIX 4.19=2034=135=049=INVMGR56=BRKR" +
		"1=BE.CHM.00111=CHM0001-0158=this is a camel - bindy test" +
		"22=448=BE000124567854=1" +
		"22=548=BE000987654354=2" +
		"22=648=BE000999999954=3" +
		"10=220";
    	
        result.expectedBodiesReceived( generateModel().toString() );
        template.sendBody(message);

        result.assertIsSatisfied();
    }

    public List<Map<String, Object>> generateModel() {
    	
    	List<Map<String, Object>> models = new ArrayList<Map<String, Object>>();
        Map<String, Object> model = new HashMap<String, Object>();
        List<Security> securities = new ArrayList<Security>();
         
        Header header = new Header();
        header.setBeginString("FIX 4.1");
        header.setBodyLength(20);
        header.setMsgSeqNum(1);
        header.setMsgType("0");
        header.setSendCompId("INVMGR");
        header.setTargetCompId("BRKR");
        
        Trailer trailer = new Trailer();
        trailer.setCheckSum(220); 
        
        Order order = new Order();
        order.setAccount("BE.CHM.001");
        order.setClOrdId("CHM0001-01");
        order.setText("this is a camel - bindy test");

        // 1st security
        Security security = new Security();
        security.setIdSource("4");
        security.setSecurityCode("BE0001245678");
        security.setSide("1");
        
        securities.add(security);
        
        // 2nd security
        security = new Security();
        security.setIdSource("5");
        security.setSecurityCode("BE0009876543");
        security.setSide("2");
        
        securities.add(security);
        
        // 3rd security
        security = new Security();
        security.setIdSource("6");
        security.setSecurityCode("BE0009999999");
        security.setSide("3");
        
        securities.add(security);
        
        order.setSecurities(securities);
        order.setHeader(header);
        order.setTrailer(trailer);
        
        model.put(order.getClass().getName(), order);
        model.put(header.getClass().getName(), header);
        model.put(trailer.getClass().getName(), trailer);
 
        models.add(model);
        return models;
    }

    @Configuration
    public static class ContextConfig extends SingleRouteCamelConfiguration {
        BindyKeyValuePairDataFormat kvpBindyDataFormat = new BindyKeyValuePairDataFormat("org.apache.camel.dataformat.bindy.model.fix.complex.onetomany");

        @Override
        @Bean
        public RouteBuilder route() {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    from(URI_DIRECT_START).unmarshal(kvpBindyDataFormat).to(URI_MOCK_RESULT);
                }
            };
        }
    }
}
