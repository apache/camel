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
package org.apache.camel.example.splunk;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.properties.PropertiesComponent;

public class SplunkSearchRouteBuilder extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        log.info("About to setup Splunk search route: Splunk Server --> log{results}");

        // configure properties component
        PropertiesComponent pc = getContext().getComponent("properties", PropertiesComponent.class);
        pc.setLocation("classpath:application.properties");

        from("splunk://normal?host={{splunk.host}}&port={{splunk.port}}&delay=10s"
                + "&username={{splunk.username}}&password={{splunk.password}}&initEarliestTime=08/17/13 08:35:46:456"
                + "&sourceType=access_combined_wcookie&search=search Code=D | head 5")
                .log("${body}");
    }
}
