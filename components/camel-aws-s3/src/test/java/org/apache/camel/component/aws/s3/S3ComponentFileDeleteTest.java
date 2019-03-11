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
package org.apache.camel.component.aws.s3;

import com.amazonaws.services.s3.model.PutObjectRequest;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.junit.Test;

public class S3ComponentFileDeleteTest extends S3ComponentFileTest {

    @Override
    String getCamelBucket() {
        return "myothercamelbucket";
    }

    @Test
    @Override
    public void sendFile() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = templateDelete.send("direct:startDelete", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(S3Constants.KEY, "CamelUnitTest");
                exchange.getIn().setBody(testFile);
            }
        });

        assertMockEndpointsSatisfied();

        assertResultExchange(result.getExchanges().get(0), true);

        PutObjectRequest putObjectRequest = client.putObjectRequests.get(0);
        assertEquals(getCamelBucket(), putObjectRequest.getBucketName());

        assertResponseMessage(exchange.getIn());

        assertFileNotExists(testFile.getAbsolutePath());
    }

}
