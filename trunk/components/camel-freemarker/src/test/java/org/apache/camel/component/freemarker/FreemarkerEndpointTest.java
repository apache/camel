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
package org.apache.camel.component.freemarker;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.core.io.Resource;

/**
 * Freemarker unit test
 */
public class FreemarkerEndpointTest extends FreemarkerTest {

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() throws Exception {
                FreemarkerEndpoint endpoint = new FreemarkerEndpoint();
                endpoint.setCamelContext(context);
                endpoint.setResourceUri("org/apache/camel/component/freemarker/example.ftl");

                Configuration configuraiton = new Configuration();
                configuraiton.setTemplateLoader(new ClassTemplateLoader(Resource.class, "/"));
                endpoint.setConfiguration(configuraiton);

                context.addEndpoint("free", endpoint);

                from("direct:a").to("free");
            }
        };
    }
}