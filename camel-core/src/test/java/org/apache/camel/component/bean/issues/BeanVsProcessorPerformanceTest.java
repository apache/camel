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
package org.apache.camel.component.bean.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.util.StopWatch;

/**
 *
 */
public class BeanVsProcessorPerformanceTest extends ContextTestSupport {

    private final int size = 100000;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myLittleBean", new MyLittleBean());
        return jndi;
    }

    public void testProcessor() throws Exception {
        StopWatch watch = new StopWatch();

        for (int i = 0; i < size; i++) {
            Object out = template.requestBody("direct:a", "" + i);
            assertEquals("Bye " + i, out);
        }

        log.info("Processor took {} ms ", watch.taken());
    }

    public void testBean() throws Exception {
        StopWatch watch = new StopWatch();

        for (int i = 0; i < size; i++) {
            Object out = template.requestBody("direct:b", "" + i);
            assertEquals("Bye " + i, out);
        }

        log.info("Bean took {} ms ", watch.taken());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a")
                        .process(new MyLittleProcessor());

                from("direct:b")
                    .bean("myLittleBean", "bye");
            }
        };
    }
}
