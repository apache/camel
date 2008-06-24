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
package org.apache.camel.spring.debug;

import java.util.List;

import junit.framework.TestCase;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.model.RouteType;
import org.apache.camel.processor.interceptor.DebugInterceptor;
import org.apache.camel.processor.interceptor.Debugger;
import org.apache.camel.spring.Main;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Revision$
 */
public class DebugTest extends TestCase {
    private static final transient Log LOG = LogFactory.getLog(DebugTest.class);

    protected Main main;
    protected Debugger debugger;
    protected Object expectedBody = "<hello id='abc'>world!</hello>";

    public void testDebugger() throws Exception {
        // START SNIPPET: example
        // lets run the camel route in debug mode
        main = new Main();
        main.enableDebug();
        main.setApplicationContextUri("org/apache/camel/spring/debug/applicationContext.xml");
        main.start();

        // now lets test we have a debugger available
        debugger = main.getDebugger();
        // END SNIPPET: example

        assertNotNull("should have a debugger!", debugger);

        DebugInterceptor f1 = assertHasInterceptor("f1");
        DebugInterceptor o1 = assertHasInterceptor("o1");
        DebugInterceptor o2 = assertHasInterceptor("o2");

        // now lets get the routes
        List<RouteType> routes = main.getRouteDefinitions();
        assertEquals("Number of routes", 1, routes.size());

        // now lets send a message
        ProducerTemplate template = main.getCamelTemplate();
        template.sendBody("direct:a", expectedBody);

        List<Exchange> o1Messages = o1.getExchanges();
        assertEquals("Expected messages at o1", 1, o1Messages.size());
        LOG.info("o1 received message: " + o1Messages.get(0));
    }

    @Override
    protected void tearDown() throws Exception {
        if (main != null) {
            main.stop();
        }
    }

    protected DebugInterceptor assertHasInterceptor(String id) {
        DebugInterceptor interceptor = debugger.getInterceptor(id);
        assertNotNull("Should have an interceptor for id: " + id, interceptor);
        return interceptor;
    }
}
