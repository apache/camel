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
package org.apache.camel.itest.jaxb;

import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxb.FallbackTypeConverter;
import org.apache.camel.itest.jaxb.example.Bar;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JaxbFallbackTypeConverterTest extends CamelTestSupport {
    
    @Test
    public void testJaxbFallbackTypeConverter() {
        Bar bar = new Bar();
        bar.setName("camel");
        bar.setValue("cool");
        String result = template.requestBody("direct:start", bar, String.class);
        assertNotNull(result);
        assertTrue("Get a wrong xml string", result.indexOf("<bar name=\"camel\" value=\"cool\"/>") > 0);
        assertTrue("The pretty print setting is not working",  result.indexOf("><bar") > 0);
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // setup the camel property for the PrettyPrint
                context.getGlobalOptions().put(FallbackTypeConverter.PRETTY_PRINT, "false");

                from("direct:start").process(new Processor() {

                    @Override
                    public void process(Exchange exchange) throws Exception {
                        Message in = exchange.getIn();
                        InputStream is = in.getMandatoryBody(InputStream.class);
                        // make sure we can get the InputStream rightly.
                        exchange.getOut().setBody(is);
                    }
                    
                });
            }
        };
    }

}
