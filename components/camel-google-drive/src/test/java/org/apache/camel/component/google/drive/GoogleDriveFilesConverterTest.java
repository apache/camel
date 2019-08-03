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
package org.apache.camel.component.google.drive;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class GoogleDriveFilesConverterTest extends CamelTestSupport {
    
    @Override
    protected void doPreSetup() throws Exception {
        deleteDirectory("target/file-test");
    }
        
    @Test
    public void converterTest() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        
        template.sendBodyAndHeader("file://target/file-test/", "Hello World", Exchange.FILE_NAME, "hello.txt");
        
        assertMockEndpointsSatisfied();
        
        Message result = mock.getExchanges().get(0).getIn();
        assertTrue("We should get google file instance here", result.getBody() instanceof com.google.api.services.drive.model.File);
        
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                
                from("file://target/file-test?initialDelay=2000").convertBodyTo(com.google.api.services.drive.model.File.class).to("mock:result");
            }
        };
    }


}
