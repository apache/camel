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
package org.apache.camel.component.kamelet;

import java.util.Properties;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.util.PropertiesHelper.asProperties;
import static org.assertj.core.api.Assertions.assertThat;

public class KameletGlobalPropertiesTest extends CamelTestSupport {
    @Test
    public void propertiesAreTakenFromRouteId() {
        assertThat(
                fluentTemplate
                        .to("direct:someId")
                        .request(String.class))
                .isEqualTo("from-route-someId");

        assertThat(
                fluentTemplate
                        .to("kamelet:setBody/test")
                        .request(String.class))
                .isEqualTo("from-route");
    }

    @Test
    public void propertiesAreTakenFromTemplateId() {
        assertThat(
                fluentTemplate
                        .to("kamelet:setBody")
                        .request(String.class))
                .isEqualTo("from-template");
    }

    @Test
    public void propertiesAreTakenFromURI() {
        assertThat(
                fluentTemplate
                        .to("kamelet:setBody?bodyValue={{bodyValue}}")
                        .request(String.class))
                .isEqualTo("from-uri");
    }

    @Test
    public void rawIsPropagated() {
        context.getEndpoint(
                "kamelet:http-send?proxyUsr=RAW(u+sr)&proxyPwd=RAW(p+wd)");

        assertThat(context.getEndpoints().stream().filter(HttpEndpoint.class::isInstance).findFirst()
                .map(HttpEndpoint.class::cast))
                .get()
                .hasFieldOrPropertyWithValue("endpointUri",
                        "http://localhost:8080?proxyAuthUsername=u%2Bsr&proxyAuthPassword=p%2Bwd")
                .hasFieldOrPropertyWithValue("proxyAuthUsername", "u+sr")
                .hasFieldOrPropertyWithValue("proxyAuthPassword", "p+wd");
    }

    @Test
    public void rawWithPlaceholdersIsPropagated() {
        context.getEndpoint(
                "kamelet:http-send?proxyUsr=RAW({{proxy.usr}})&proxyPwd=RAW({{proxy.pwd}})");

        assertThat(context.getEndpoints().stream().filter(HttpEndpoint.class::isInstance).findFirst()
                .map(HttpEndpoint.class::cast))
                .get()
                .hasFieldOrPropertyWithValue("endpointUri",
                        "http://localhost:8080?proxyAuthUsername=u%2Bsr&proxyAuthPassword=p%2Bwd")
                .hasFieldOrPropertyWithValue("proxyAuthUsername", "u+sr")
                .hasFieldOrPropertyWithValue("proxyAuthPassword", "p+wd");
    }

    @Test
    public void rawPropertiesIsPropagated() {
        context.getEndpoint(
                "kamelet:http-send?proxyUsr={{raw.proxy.usr}}&proxyPwd={{raw.proxy.pwd}}");

        assertThat(context.getEndpoints().stream().filter(HttpEndpoint.class::isInstance).findFirst()
                .map(HttpEndpoint.class::cast))
                .get()
                .hasFieldOrPropertyWithValue("endpointUri",
                        "http://localhost:8080?proxyAuthUsername=u%2Bsr&proxyAuthPassword=p%2Bwd")
                .hasFieldOrPropertyWithValue("proxyAuthUsername", "u+sr")
                .hasFieldOrPropertyWithValue("proxyAuthPassword", "p+wd");
    }

    @Test
    public void rawPropertyRefIsPropagated() {
        context.getEndpoint(
                "kamelet:http-send?proxyUsr=#property:proxy.usr&proxyPwd=#property:proxy.pwd");

        assertThat(context.getEndpoints().stream().filter(HttpEndpoint.class::isInstance).findFirst()
                .map(HttpEndpoint.class::cast))
                .get()
                .hasFieldOrPropertyWithValue("endpointUri",
                        "http://localhost:8080?proxyAuthUsername=%23property%3Aproxy.usr&proxyAuthPassword=%23property%3Aproxy.pwd")
                .hasFieldOrPropertyWithValue("proxyAuthUsername", "u+sr")
                .hasFieldOrPropertyWithValue("proxyAuthPassword", "p+wd");
    }

    @Test
    public void urlEncodingIsRespected() {
        assertThat(context.getEndpoint("kamelet:timer-source?message=Hello+Kamelets&period=1000", KameletEndpoint.class)
                .getKameletProperties())
                .containsEntry("message", "Hello Kamelets");
        assertThat(context.getEndpoint("kamelet:timer-source?message=Hi%20Kamelets&period=1000", KameletEndpoint.class)
                .getKameletProperties())
                .containsEntry("message", "Hi Kamelets");
        assertThat(context
                .getEndpoint("kamelet:timer-source?message=messaging.knative.dev%2Fv1beta1&period=1000", KameletEndpoint.class)
                .getKameletProperties())
                .containsEntry("message", "messaging.knative.dev/v1beta1");
    }

    // **********************************************
    //
    // test set-up
    //
    // **********************************************

    @Override
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        return asProperties(
                "proxy.usr", "u+sr",
                "proxy.pwd", "p+wd",
                "raw.proxy.usr", "RAW(u+sr)",
                "raw.proxy.pwd", "RAW(p+wd)",
                "bodyValue", "from-uri",
                Kamelet.PROPERTIES_PREFIX + "setBody.bodyValue", "from-template",
                Kamelet.PROPERTIES_PREFIX + "setBody.test.bodyValue", "from-route",
                Kamelet.PROPERTIES_PREFIX + "setBody.someId.bodyValue", "from-route-someId");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // template
                routeTemplate("setBody")
                        .templateParameter("bodyValue")
                        .from("kamelet:source")
                        .setBody().constant("{{bodyValue}}");

                // template
                routeTemplate("http-send")
                        .templateParameter("proxyUsr")
                        .templateParameter("proxyPwd")
                        .from("kamelet:source")
                        .log("info")
                        .to("http://localhost:8080?proxyAuthUsername=RAW({{proxyUsr}})&proxyAuthPassword=RAW({{proxyPwd}})");

                // template
                routeTemplate("timer-source")
                        .templateParameter("period")
                        .templateParameter("message")
                        .from("timer:tick")
                        .setBody().constant("{{message}}")
                        .to("kamelet:sink");

                // routes
                from("direct:someId").to("kamelet:setBody/someId");

                from("direct:test").to("kamelet:setBody");
            }
        };
    }
}
