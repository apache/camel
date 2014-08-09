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
package org.apache.camel.component.swagger;

import com.wordnik.swagger.config.SwaggerConfig;
import com.wordnik.swagger.model.ApiListing;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.rest.RestDefinition;
import org.junit.Ignore;
import org.junit.Test;
import scala.Option;

import static org.junit.Assert.assertNotNull;

public class RestSwaggerReaderTest {

    @Test
    @Ignore
    public void testReaderRead() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().component("jetty").host("localhost").port(9090);

                rest("/hello")
                    .get("/hi").to("log:hi");
            }
        });
        context.start();

        RestDefinition rest = context.getRestDefinitions().get(0);
        assertNotNull(rest);

        SwaggerConfig config = new SwaggerConfig();
        RestSwaggerReader reader = new RestSwaggerReader();
        Option<ApiListing> option = reader.read(rest, config);
        assertNotNull(option);
        ApiListing listing = option.get();
        assertNotNull(listing);

        System.out.println(listing);

        context.stop();
    }

}
