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
package org.apache.camel.example.bigxml;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.BeforeClass;
import org.junit.Test;

public class XmlTokenizerTest extends CamelTestSupport {

    @BeforeClass
    public static void beforeClass() throws Exception {
        TestUtils.buildTestXml();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext ctx = super.createCamelContext();
        ctx.disableJMX();
        return ctx;
    }

    @Override
    protected int getShutdownTimeout() {
        return 300;
    }

    @Test
    public void test() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(TestUtils.getNumOfRecords()).create();
        boolean matches = notify.matches(TestUtils.getMaxWaitTime(), TimeUnit.MILLISECONDS);
        log.info("Processed XML file with {} records", TestUtils.getNumOfRecords());
        assertTrue("Test completed", matches);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:" + TestUtils.getBasePath() + "?readLock=changed&noop=true")
                    .split(body().tokenizeXML("record", "records")).streaming().stopOnException()
                        .log(LoggingLevel.TRACE, "org.apache.camel.example.bigxml", "${body}")
                        .to("log:org.apache.camel.example.bigxml?level=DEBUG&groupInterval=100&groupDelay=100&groupActiveOnly=false")
                    .end();
            }
        };
    }

}
