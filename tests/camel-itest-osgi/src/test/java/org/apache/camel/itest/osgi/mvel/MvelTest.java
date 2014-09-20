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
package org.apache.camel.itest.osgi.mvel;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.apache.camel.itest.osgi.core.bean.MyFooBean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;

import static org.ops4j.pax.exam.OptionUtils.combine;


@RunWith(PaxExam.class)
public class MvelTest extends OSGiIntegrationTestSupport {
    
    @Test
    public void testMvelLanguage() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodyReceived();
        
        template.sendBody("direct:testmvel", new MyFooBean());
       
    }
        
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:testmvel").choice().when().mvel("request.body instanceof org.apache.camel.itest.osgi.core.bean.MyFooBean").to("mock:result");           
            }
        };
    }
    
    @Configuration
    public static Option[] configure() {
        Option[] options = combine(
            getDefaultCamelKarafOptions(),
            loadCamelFeatures("camel-mvel"));
        
        return options;
    }

}
