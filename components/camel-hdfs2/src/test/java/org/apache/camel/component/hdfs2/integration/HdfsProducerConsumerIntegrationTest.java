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
package org.apache.camel.component.hdfs2.integration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;


import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;

@Ignore("Must run manual")
public class HdfsProducerConsumerIntegrationTest extends CamelTestSupport {
    private static final int ITERATIONS = 400;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testSimpleSplitWriteRead() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("hdfs2://localhost:9000/tmp/test/test-camel-simple-write-file?fileSystemType=HDFS&splitStrategy=BYTES:5,IDLE:1000");
                from("hdfs2://localhost:9000/tmp/test/test-camel-simple-write-file?pattern=*&initialDelay=2000&fileSystemType=HDFS&chunkSize=5").to("mock:result");
            }
        });
        context.start();

        Set<String> sent = new HashSet<String>();

        for (int i = 0; i < 10; ++i) {
            String text = "CIAO" + i;
            sent.add(text);
            template.sendBody("direct:start", text);
        }

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);

        resultEndpoint.expectedMessageCount(10);
        resultEndpoint.assertIsSatisfied();

        List<Exchange> exchanges = resultEndpoint.getExchanges();
        for (Exchange exchange : exchanges) {
            String text = exchange.getIn().getBody(String.class);
            sent.remove(text);
        }
        assertThat(sent.isEmpty(), is(true));
    }

    @Test
    // see https://issues.apache.org/jira/browse/CAMEL-7318
    public void testMultipleConsumers() throws Exception {

        Path p = new Path("hdfs://localhost:9000/tmp/test/multiple-consumers");
        FileSystem fs = FileSystem.get(p.toUri(), new Configuration());
        fs.mkdirs(p);
        for (int i = 1; i <= ITERATIONS; i++) {
            FSDataOutputStream os = fs.create(new Path(p, String.format("file-%03d.txt", i)));
            os.write(String.format("hello (%03d)\n", i).getBytes());
            os.close();
        }

        final Set<String> fileNames = new HashSet<String>();
        final CountDownLatch latch = new CountDownLatch(ITERATIONS);
        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                fileNames.add(exchange.getIn().getHeader(Exchange.FILE_NAME, String.class));
                latch.countDown();
            }
        });

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // difference in chunkSize only to allow multiple consumers
                from("hdfs2://localhost:9000/tmp/test/multiple-consumers?pattern=*.txt&fileSystemType=HDFS&chunkSize=128").to("mock:result");
                from("hdfs2://localhost:9000/tmp/test/multiple-consumers?pattern=*.txt&fileSystemType=HDFS&chunkSize=256").to("mock:result");
                from("hdfs2://localhost:9000/tmp/test/multiple-consumers?pattern=*.txt&fileSystemType=HDFS&chunkSize=512").to("mock:result");
                from("hdfs2://localhost:9000/tmp/test/multiple-consumers?pattern=*.txt&fileSystemType=HDFS&chunkSize=1024").to("mock:result");
            }
        });
        context.start();

        resultEndpoint.expectedMessageCount(ITERATIONS);

        latch.await(30, TimeUnit.SECONDS);

        resultEndpoint.assertIsSatisfied();
        assertThat(fileNames.size(), equalTo(ITERATIONS));
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
        fs.delete(new Path("hdfs://localhost:9000/tmp/test/multiple-consumers"), true);
    }

}