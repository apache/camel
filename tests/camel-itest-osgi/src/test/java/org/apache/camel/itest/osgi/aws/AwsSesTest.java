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
package org.apache.camel.itest.osgi.aws;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.aws.ses.SesConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.springframework.osgi.context.support.OsgiBundleXmlApplicationContext;

@RunWith(PaxExam.class)
public class AwsSesTest extends AwsTestSupport {
    
    @Override
    protected OsgiBundleXmlApplicationContext createApplicationContext() {
        return new OsgiBundleXmlApplicationContext(new String[]{"org/apache/camel/itest/osgi/aws/CamelContext.xml"});
    }
    
    @Test
    public void sendInOnly() throws Exception {
        Exchange exchange = template.send("direct:start-ses", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("This is my message text.");
            }
        });
        
        assertEquals("1", exchange.getIn().getHeader(SesConstants.MESSAGE_ID));
    }
    
    @Test
    public void sendInOut() throws Exception {
        Exchange exchange = template.request("direct:start-ses", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("This is my message text.");
            }
        });
        
        assertEquals("1", exchange.getOut().getHeader(SesConstants.MESSAGE_ID));
    }
}