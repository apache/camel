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
package org.apache.camel.component.timer;

import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.TestCase;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.jndi.JndiContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Revision: 520220 $
 */
public class TimerRouteTest extends TestCase {
    private static final transient Log LOG = LogFactory.getLog(TimerRouteTest.class);

    public void testPojoRoutes() throws Exception {
        final AtomicInteger hitCount = new AtomicInteger();

        JndiContext context = new JndiContext();
        context.bind("bar", new Runnable() {
            public void run() {
                LOG.debug("hit");
                hitCount.incrementAndGet();
            }
        });

        CamelContext camelContext = new DefaultCamelContext(context);

        // lets add some routes
        camelContext.addRoutes(new RouteBuilder() {
            public void configure() {
                from("timer://foo?fixedRate=true&delay=0&period=500").to("pojo:bar");
            }
        });

        camelContext.start();

        // now lets wait for the timer to fire a few times.
        Thread.sleep(1000 * 2);
        assertTrue("", hitCount.get() >= 3);

        camelContext.stop();
    }
}
