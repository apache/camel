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
package org.apache.camel.component.velocity;

import java.io.FileInputStream;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class VelocityOverridesPropertiesTest extends CamelTestSupport {
    
    @Test
    public void testOverridingProperties() throws Exception {
        Logger logger = Logger.getLogger("org.apache.camel.component.velocity");
        FileHandler fh = new FileHandler("target/camel-velocity-jdk-test.log");
        logger.addHandler(fh);
        logger.setLevel(Level.ALL);
        
        Exchange exchange = template.request("direct:a", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Monday");
                exchange.getIn().setHeader("name", "Christian");
                exchange.setProperty("item", "7");
            }
        });

        assertEquals("Dear Christian. You ordered item 7 on Monday.", exchange.getOut().getBody());
        assertEquals("Christian", exchange.getOut().getHeader("name"));
        
        String logContent = IOUtils.toString(new FileInputStream("target/camel-velocity-jdk-test.log"), "UTF-8");
        assertTrue(logContent.contains("JdkLogChute will use logger 'org.apache.camel.component.velocity.VelocityEndpoint' at level 'FINEST'"));
        assertTrue(logContent.contains("Using logger class org.apache.velocity.runtime.log.JdkLogChute"));
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:a")
                    .to("velocity:org/apache/camel/component/velocity/example.vm?propertiesFile=org/apache/camel/component/velocity/velocity-logging.properties");
            }
        };
    }
}