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
package org.apache.camel.example.ehcache;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ehcache.EhcacheConstants;
import org.apache.camel.model.rest.RestBindingMode;

public class CachingRoute extends RouteBuilder {

    private static final String EHCACHE_ENDPOINT = "ehcache://dataServiceCache"
        + "?configurationUri=/ehcache.xml"
        + "&keyType=java.lang.Long"
        + "&valueType=java.io.Serializable";

    @Override
    public void configure() {
        restConfiguration()
            .component("servlet")
            .bindingMode(RestBindingMode.json)
            .contextPath("/camel-example-ehcache-blueprint")
            .port(8181)
            .dataFormatProperty("prettyPrint", "true");

        rest("/data").consumes("application/json").produces("application/json")
            .get("/{id}").to("direct:getData");

        // @formatter:off
        from("direct:getData").routeId("ehcache-example-get")
            .log("ID = ${header.id}")
            .setHeader(EhcacheConstants.ACTION, constant(EhcacheConstants.ACTION_GET))
            .setHeader(EhcacheConstants.KEY, header("id").convertTo(Long.class))
            .to(EHCACHE_ENDPOINT)
            .choice()
                .when(header(EhcacheConstants.ACTION_HAS_RESULT).isNotEqualTo("true"))
                    .log("No cache hit. Fetching data from external service")
                    .to("bean:dataService?method=getData(${header.id})")
                    .setHeader(EhcacheConstants.ACTION, constant(EhcacheConstants.ACTION_PUT))
                    .setHeader(EhcacheConstants.KEY, header("id").convertTo(Long.class))
                    .to(EHCACHE_ENDPOINT)
                .otherwise()
                    .log("Cache hit. Returning")
            .endChoice();
        // @formatter:on
    }
}
