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
package org.apache.camel.commands.jolokia;

import org.apache.camel.commands.ContextInfoCommand;
import org.apache.camel.commands.ContextListCommand;
import org.apache.camel.commands.EipExplainCommand;
import org.apache.camel.commands.EndpointExplainCommand;
import org.apache.camel.commands.EndpointListCommand;
import org.apache.camel.commands.RouteInfoCommand;
import org.apache.camel.commands.RouteListCommand;
import org.apache.camel.commands.RouteShowCommand;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("used for manual testing")
public class JolokiaCommandsTest {

    private String url = "http://localhost:8080/jolokia";

    private JolokiaCamelController controller;

    @Test
    public void testContextList() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        if (!controller.ping()) {
            throw new IllegalArgumentException("Error connecting to " + url);
        }

        ContextListCommand cmd = new ContextListCommand();
        cmd.execute(controller, System.out, System.err);
    }

    @Test
    public void testContextInfo() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        if (!controller.ping()) {
            throw new IllegalArgumentException("Error connecting to " + url);
        }

        ContextInfoCommand cmd = new ContextInfoCommand("camel-1", true);
        cmd.setStringEscape(new NoopStringEscape());
        cmd.execute(controller, System.out, System.err);
    }

    @Test
    public void testEndpointList() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        if (!controller.ping()) {
            throw new IllegalArgumentException("Error connecting to " + url);
        }

        EndpointListCommand cmd = new EndpointListCommand("camel-1", true, true, true);
        cmd.execute(controller, System.out, System.err);
    }

    @Test
    public void testEndpointExplain() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        if (!controller.ping()) {
            throw new IllegalArgumentException("Error connecting to " + url);
        }

        EndpointExplainCommand cmd = new EndpointExplainCommand("camel-1", true, "rest*");
        cmd.execute(controller, System.out, System.err);
    }

    @Test
    public void testEipExplain() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        if (!controller.ping()) {
            throw new IllegalArgumentException("Error connecting to " + url);
        }

        EipExplainCommand cmd = new EipExplainCommand("camel-1", "transform", true);
        cmd.execute(controller, System.out, System.err);
    }

    @Test
    public void testRouteList() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        if (!controller.ping()) {
            throw new IllegalArgumentException("Error connecting to " + url);
        }

        RouteListCommand cmd = new RouteListCommand("camel-1");
        cmd.execute(controller, System.out, System.err);
    }

    @Test
    public void testRouteInfo() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        if (!controller.ping()) {
            throw new IllegalArgumentException("Error connecting to " + url);
        }

        RouteInfoCommand cmd = new RouteInfoCommand("route1", "camel-1");
        cmd.setStringEscape(new NoopStringEscape());
        cmd.execute(controller, System.out, System.err);
    }

    @Test
    public void testRouteShow() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        if (!controller.ping()) {
            throw new IllegalArgumentException("Error connecting to " + url);
        }

        RouteShowCommand cmd = new RouteShowCommand("route1", "camel-1");
        cmd.execute(controller, System.out, System.err);
    }

}
