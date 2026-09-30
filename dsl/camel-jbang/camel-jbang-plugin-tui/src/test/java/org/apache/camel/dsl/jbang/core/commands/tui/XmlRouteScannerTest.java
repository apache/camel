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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XmlRouteScannerTest {

    @Test
    void routesAndTheirStepsWithLines() {
        List<ScannedRoute> routes = XmlRouteScanner.scan("""
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                  <route id="tickets">
                    <from uri="direct:tickets"/>
                    <choice>
                      <when>
                        <simple>${header.vip}</simple>
                        <to uri="direct:vip"/>
                      </when>
                    </choice>
                    <switch>
                      <selector><header>department</header></selector>
                      <case value="billing" uri="direct:billing"/>
                      <otherwise uri="direct:review"/>
                    </switch>
                    <enrich>
                      <constant>direct:customer</constant>
                    </enrich>
                    <wireTap uri="seda:audit?block=false"/>
                  </route>
                  <route>
                    <from uri="direct:vip"/>
                    <toD uri="kafka:vip-${header.region}"/>
                  </route>
                </routes>
                """);
        assertThat(routes).extracting(ScannedRoute::id).containsExactly("tickets", null);
        ScannedRoute tickets = routes.get(0);
        assertThat(tickets.fromUri()).isEqualTo("direct:tickets");
        assertThat(tickets.line()).isEqualTo(2);
        // lines from 0, as the Source tab counts them
        assertThat(tickets.tos()).containsExactly(
                new ScannedRoute.To("direct:vip", 6),
                new ScannedRoute.To("direct:billing", 11),
                new ScannedRoute.To("direct:review", 12),
                new ScannedRoute.To("direct:customer", 14),
                new ScannedRoute.To("seda:audit?block=false", 17));
        assertThat(routes.get(1).tos()).containsExactly(new ScannedRoute.To("kafka:vip-${header.region}", 21));
    }

    @Test
    void notWellFormedOrWithoutRoutes() {
        assertThat(XmlRouteScanner.scan("<routes><route><from uri=\"direct:a\"/>")).isEmpty();
        assertThat(XmlRouteScanner.scan("<beans/>")).isEmpty();
        // no DTD, no external entities
        assertThat(XmlRouteScanner.scan("""
                <!DOCTYPE routes [<!ENTITY x SYSTEM "file:///etc/passwd">]>
                <routes><route><from uri="direct:&x;"/></route></routes>
                """)).allSatisfy(r -> assertThat(r.fromUri()).doesNotContain("root"));
    }
}
