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
package org.apache.camel.maven.htmlxlsx;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.apache.camel.maven.htmlxlsx.model.CamelContextRouteCoverage;
import org.apache.camel.maven.htmlxlsx.model.ChildEip;
import org.apache.camel.maven.htmlxlsx.model.ChildEipStatistic;
import org.apache.camel.maven.htmlxlsx.model.Components;
import org.apache.camel.maven.htmlxlsx.model.EipAttribute;
import org.apache.camel.maven.htmlxlsx.model.EipStatistic;
import org.apache.camel.maven.htmlxlsx.model.Route;
import org.apache.camel.maven.htmlxlsx.model.RouteStatistic;
import org.apache.camel.maven.htmlxlsx.model.RouteTotalsStatistic;
import org.apache.camel.maven.htmlxlsx.model.Routes;
import org.apache.camel.maven.htmlxlsx.model.Test;
import org.apache.camel.maven.htmlxlsx.model.TestResult;

public class TestUtil {

    public static TestResult testResult() {

        TestResult result = new TestResult();

        result.setTest(test());
        result.setCamelContextRouteCoverage(camelContextRouteCoverage());

        return result;
    }

    public static Test test() {

        Test result = new Test();

        result.setClazz("some_class");
        result.setMethod("some_method");

        return result;
    }

    public static CamelContextRouteCoverage camelContextRouteCoverage() {

        CamelContextRouteCoverage result = new CamelContextRouteCoverage();

        result.setExchangesTotal(3);
        result.setId("some_route_coverage");
        result.setTotalProcessingTime(10);
        result.setRoutes(routes());

        return result;
    }

    public static Routes routes() {

        Routes result = new Routes();

        result.setRouteList(Collections.singletonList(route()));

        return result;
    }

    public static Route route() {

        Route result = new Route();

        result.setCustomId("true");
        result.setId("greetings-route");
        result.setExchangesTotal(3);
        result.setTotalProcessingTime(15);
        result.setComponentsMap(componentsMap());

        return result;
    }

    public static Map<String, Object> componentsMap() {

        Map<String, Object> result = new TreeMap<>();

        Properties properties = new Properties();
        properties.put("uri", "direct:greetings");
        properties.put("index", 0);
        properties.put("exchangesTotal", 1);
        properties.put("totalProcessingTime", 15);
        result.put("from", properties);

        properties = new Properties();
        properties.put("id", "setBody2");
        properties.put("constant", "Hello from Cloud Cuckoo Camel Land!");
        properties.put("index", 24);
        properties.put("exchangesTotal", 1);
        properties.put("totalProcessingTime", 6);
        result.put("setBody", properties);

        properties = new Properties();
        properties.put("id", "to7");
        properties.put("uri", "file:target/output");
        properties.put("index", 25);
        properties.put("exchangesTotal", 1);
        properties.put("totalProcessingTime", 4);
        result.put("to", properties);

        return result;
    }

    public static ChildEip childEip() {

        ChildEip result = new ChildEip();

        return result;
    }

    public static ChildEipStatistic childEipStatistic() {

        ChildEipStatistic result = new ChildEipStatistic();

        return result;

    }

    public static Components components() {

        Components result = new Components();

        return result;
    }

    public static EipAttribute eipAttribute() {

        EipAttribute result = new EipAttribute();
        result.setId("dont-care");
        result.setIndex(0);

        return result;
    }

    public static EipStatistic eipStatistic() {

        EipStatistic result = new EipStatistic();

        return result;
    }

    public static RouteStatistic routeStatistic() {

        RouteStatistic result = new RouteStatistic();

        return result;
    }

    public static RouteTotalsStatistic routeTotalsStatistic() {

        RouteTotalsStatistic result = new RouteTotalsStatistic();

        return result;
    }

}
