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
package org.apache.camel.dataformat.bindy.csv;

import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.javaconfig.SingleRouteCamelConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.springframework.config.java.annotation.Bean;
import org.springframework.config.java.annotation.Configuration;
import org.springframework.config.java.test.JavaConfigContextLoader;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration(locations = "org.apache.camel.dataformat.bindy.csv.BindySimpleCsvUnmarshallTest$ContextConfig", loader = JavaConfigContextLoader.class)
public class BindySimpleCsvUnmarshallTest extends AbstractJUnit4SpringContextTests {
	
	private static final transient Log LOG = LogFactory.getLog(BindySimpleCsvUnmarshallTest.class);
	
	private String record ="01,,Albert,Cartier,ISIN,BE12345678,SELL,,1500,EUR,08-01-2009\r\n" +
                            "02,A1,,Preud'Homme,ISIN,XD12345678,BUY,,2500,USD,08-01-2009\r\n" +
                            "03,A2,Jacques,,,BE12345678,SELL,,1500,EUR,08-01-2009\r\n" +
                            "04,A3,Michel,Dupond,,,BUY,,2500,USD,08-01-2009\r\n" +
                            "05,A4,Annie,Dutronc,ISIN,BE12345678,,,1500,EUR,08-01-2009\r\n" +
                            "06,A5,André,Rieux,ISIN,XD12345678,SELL,Share,,USD,08-01-2009\r\n" +
                            "07,A6,Mylène,Farmer,ISIN,BE12345678,BUY,1500,,,08-01-2009\r\n" +
                            "08,A7,Eva,Longoria,ISIN,XD12345678,SELL,Share,2500,USD,\r\n" +
                            ",,,D,,BE12345678,SELL,,,,08-01-2009\r\n" +
                            ",,,D,ISIN,BE12345678,,,,,08-01-2009\r\n" +
                            ",,,D,ISIN,LU123456789,,,,,\r\n" +
                            "10,A8,Pauline,M,ISIN,XD12345678,SELL,Share,2500,USD,08-01-2009\r\n" +
                            "10,A9,Pauline,M,ISIN,XD12345678,BUY,Share,2500.45,USD,08-01-2009";

    @EndpointInject(uri = "mock:result")
    private MockEndpoint resultEndpoint;
    
    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

    @Test
    public void testUnMarshallMessage() throws Exception {
    	
    	template.sendBody(record);
 
        resultEndpoint.expectedMessageCount(1);        
        resultEndpoint.assertIsSatisfied();
    	
/*    	List<Exchange> exchanges = resultEndpoint.getExchanges(); 
    	
    	for(Exchange exchange : exchanges) {
    		Object body = exchange.getOut().getBody();
    		LOG.debug("Body received : " + body.toString());
    	}*/

    }

    @Configuration
    public static class ContextConfig extends SingleRouteCamelConfiguration {
        BindyCsvDataFormat camelDataFormat = new BindyCsvDataFormat("org.apache.camel.dataformat.bindy.model.simple.oneclass");

        @Override
        @Bean
        public RouteBuilder route() {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    //from("file://src/test/data?move=./target/done").unmarshal(camelDataFormat).to("mock:result");
                	from("direct:start").unmarshal(camelDataFormat).to("mock:result");
                }
            };
        }
    }
}

    