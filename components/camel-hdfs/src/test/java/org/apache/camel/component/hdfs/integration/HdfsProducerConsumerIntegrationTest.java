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
package org.apache.camel.component.hdfs.integration;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Must run manual")
public class HdfsProducerConsumerIntegrationTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testSimpleSplitWriteRead() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("hdfs://localhost:9000/tmp/test/test-camel-simple-write-file?fileSystemType=HDFS&splitStrategy=BYTES:5,IDLE:1000");
                from("hdfs://localhost:9000/tmp/test/test-camel-simple-write-file?pattern=seg*&initialDelay=2000&fileSystemType=HDFS&chunkSize=5").to("mock:result");
            }
        });
        context.start();

        for (int i = 0; i < 10; ++i) {
            template.sendBody("direct:start", "CIAO" + i);
        }

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);

        resultEndpoint.expectedMessageCount(10);
        resultEndpoint.assertIsSatisfied();

        int i = 0;
        List<Exchange> exchanges = resultEndpoint.getExchanges();
        for (Exchange exchange : exchanges) {
            assertEquals("CIAO" + i++, exchange.getIn().getBody(String.class));
        }
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();

        Thread.sleep(250);
        Configuration conf = new Configuration();
        Path dir = new Path("hdfs://localhost:9000/tmp/test");
        FileSystem fs = FileSystem.get(dir.toUri(), conf);
        fs.delete(dir, true);
    }

}