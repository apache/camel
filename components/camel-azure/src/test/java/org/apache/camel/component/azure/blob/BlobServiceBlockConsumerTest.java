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
package org.apache.camel.component.azure.blob;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;

import com.microsoft.azure.storage.StorageCredentialsAccountAndKey;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.Ignore;
import org.junit.Test;

public class BlobServiceBlockConsumerTest extends CamelTestSupport {
    @EndpointInject("direct:start")
    ProducerTemplate templateStart;
    
    @Test
    @Ignore
    public void testGetBlockBlob() throws Exception {
        templateStart.send("direct:start", ExchangePattern.InOnly, exchange -> exchange.getIn().setBody("Block Blob"));
        
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        
        assertMockEndpointsSatisfied();
        File f = mock.getExchanges().get(0).getIn().getBody(File.class);
        assertNotNull("File must be set", f);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(); 
            IOHelper.copy(new FileInputStream(f), bos);
            assertEquals("Block Blob", bos.toString("UTF-8"));
        } finally {
            if (f != null) {
                f.delete();
            }
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("creds",
            new StorageCredentialsAccountAndKey("camelazure",
                "base64EncodedValue"));
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("azure-blob://camelazure/container1/blobBlock?credentials=#creds&operation=updateBlockBlob");
                
                from("azure-blob://camelazure/container1/blobBlock?credentials=#creds&fileDir="
                    + System.getProperty("java.io.tmpdir")).to("mock:result");
                
                //from("azure-blob://camelazure/container1/blobBlock?credentials=#creds")
                //    .to("file://" + System.getProperty("java.io.tmpdir"));  
            }
        };
    }
}
