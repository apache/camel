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
package org.apache.camel.component.netty.http.rest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.netty.http.BaseNettyTestSupport;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RestProducerOutTypeBindingTest extends BaseNettyTestSupport {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Resp1 {
        private String value1;

        public Resp1() {
        }

        public Resp1(String value1) {
            this.value1 = value1;
        }

        public String getValue1() {
            return value1;
        }

        public void setValue1(String value1) {
            this.value1 = value1;
        }

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Resp2 {
        private String value1;
        private String value2;

        public Resp2() {
        }

        public Resp2(String value1, String value2) {
            this.value1 = value1;
            this.value2 = value2;
        }

        public String getValue1() {
            return value1;
        }

        public void setValue1(String value1) {
            this.value1 = value1;
        }

        public String getValue2() {
            return value2;
        }

        public void setValue2(String value2) {
            this.value2 = value2;
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                // mock server
                restConfiguration().component("netty-http").host("localhost").port(getPort()).bindingMode(RestBindingMode.auto);

                rest()
                    .get("/req1").to("direct:r1")
                    .get("/req2").to("direct:r2");
                from("direct:r1")
                        .log("Got req1")
                        .setBody(constant(new Resp1("1")));
                from("direct:r2")
                        .log("Got req2")
                        .setBody(constant(new Resp2(null, "2")))
                    .end();

                // faulty client
                from("direct:req1")
                        .toF("rest:get:/req1?host=localhost:%d&outType=%s", getPort(), Resp1.class.getName());
                from("direct:req2")
                        .toF("rest:get:/req2?host=localhost:%d&outType=%s", getPort(), Resp2.class.getName());
            }
        };
    }

    @Test
    public void type1Test() {
        assertThat(template.requestBody("direct:req1", ""))
                .isInstanceOfSatisfying(Resp1.class, resp -> assertThat(resp.getValue1()).isEqualTo("1"));
    }

    @Test
    public void type2Test() {
        assertThat(template.requestBody("direct:req2", ""))
                .isInstanceOfSatisfying(Resp2.class, resp -> {
                    assertThat(resp.getValue1()).isNull();
                    assertThat(resp.getValue2()).isEqualTo("2");
                });
    }
}
