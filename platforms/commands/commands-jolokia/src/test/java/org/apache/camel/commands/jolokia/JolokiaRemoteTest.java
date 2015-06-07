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

import java.util.List;
import java.util.Map;

import org.junit.Ignore;
import org.junit.Test;

@Ignore("used for manual testing")
public class JolokiaRemoteTest {

    private String url = "http://localhost:8080/jolokia";

    private JolokiaCamelController controller;

    @Test
    public void testPing() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        boolean pong = controller.ping();
        System.out.println("Ping responsed: " + pong);
    }

    @Test
    public void testRemoteCamelContexts() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        List<Map<String, String>> data = controller.getCamelContexts();
        System.out.println(data);
    }

    @Test
    public void testRemoteCamelContextInformation() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        Map<String, Object> data = controller.getCamelContextInformation("myCamel");
        System.out.println(data);
    }

    @Test
    public void testRemoteCamelContextStatsAsXml() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        String data = controller.getCamelContextStatsAsXml("myCamel", true, false);
        System.out.println(data);
    }

    @Test
    public void testRemoteCamelContextControl() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        controller.suspendContext("myCamel");
        Map<String, Object> data = controller.getCamelContextInformation("myCamel");
        System.out.println(data);

        Thread.sleep(500);

        controller.resumeContext("myCamel");
        data = controller.getCamelContextInformation("myCamel");
        System.out.println(data);
    }

    @Test
    public void testRemoteGetAllRoutes() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        List<Map<String, String>> data = controller.getRoutes(null);
        System.out.println(data);
    }

    @Test
    public void testRemoteGetRoutes() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        List<Map<String, String>> data = controller.getRoutes("myCamel");
        System.out.println(data);
    }

    @Test
    public void testRemoteGetRoutesFilter() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        List<Map<String, String>> data = controller.getRoutes(null, "route2");
        System.out.println(data);
    }

    @Test
    public void testRemoteResetRouteStats() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        controller.resetRouteStats("myCamel");
    }

    @Test
    public void testRemoteRouteControl() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        controller.suspendRoute("myCamel", "route2");
        List<Map<String, String>> data = controller.getRoutes("myCamel", "route2");
        System.out.println(data);

        Thread.sleep(500);

        controller.resumeRoute("myCamel", "route2");
        data = controller.getRoutes("myCamel", "route2");
        System.out.println(data);
    }

    @Test
    public void testRouteModel() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        String data = controller.getRouteModelAsXml("myCamel", "route2");
        System.out.println(data);
    }

    @Test
    public void testRouteStats() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        String data = controller.getRouteStatsAsXml("myCamel", "route2", true, true);
        System.out.println(data);
    }

    @Test
    public void testRestsModel() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        String data = controller.getRestModelAsXml("myCamel");
        System.out.println(data);
    }

    @Test
    public void testGetEndpoints() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        List<Map<String, String>> data = controller.getEndpoints("myCamel");
        System.out.println(data);
    }

    @Test
    public void testGetRestServices() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        List<Map<String, String>> data = controller.getRestServices("myCamel");
        System.out.println(data);
    }

    @Test
    public void testExplainEndpointJson() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        String data = controller.explainEndpointAsJSon("myCamel", "log:foo", true);
        System.out.println(data);
    }

    @Test
    public void testExplainEipJson() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        String data = controller.explainEipAsJSon("myCamel", "transform", true);
        System.out.println(data);
    }

    @Test
    public void testListComponents() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        List<Map<String, String>> data = controller.listComponents("myCamel");
        System.out.println(data);
    }

    @Test
    public void testInflight() throws Exception {
        controller = new DefaultJolokiaCamelController();
        controller.connect(url, null, null);

        List<Map<String, Object>> data = controller.browseInflightExchanges("myCamel", 500, false);
        System.out.println(data);
    }

}
