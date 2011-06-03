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
package org.apache.camel.component.hdfs;

import java.io.File;
import java.util.List;

import junit.framework.Assert;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.Test;

public class HdfsProducerConsumerTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testSimpleSplitWriteRead() throws Exception {
        final Path file = new Path(new File("target/test/test-camel-simple-write-file").getAbsolutePath());

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("hdfs:///localhost/" + file.toUri() + "?fileSystemType=LOCAL&splitStrategy=BYTES:5,IDLE:1000");
                from("hdfs:///localhost/" + file.toUri() + "?pattern=seg*&initialDelay=2000&fileSystemType=LOCAL&chunkSize=5").to("mock:result");
            }
        });
        context.start();

        for (int i = 0; i < 10; ++i) {
            template.sendBody("direct:start", "CIAO" + i);
        }

        MockEndpoint resultEndpoint = (MockEndpoint) context.getEndpoint("mock:result");

        resultEndpoint.expectedMessageCount(10);
        resultEndpoint.assertIsSatisfied();

        int i = 0;
        List<Exchange> exchanges = resultEndpoint.getExchanges();
        for (Exchange exchange : exchanges) {
            Assert.assertEquals("CIAO" + i++, exchange.getIn().getBody(String.class));
        }
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        Thread.sleep(100);
        Configuration conf = new Configuration();
        Path dir = new Path("target/test");
        FileSystem fs = FileSystem.get(dir.toUri(), conf);
        fs.delete(dir, true);
    }
}