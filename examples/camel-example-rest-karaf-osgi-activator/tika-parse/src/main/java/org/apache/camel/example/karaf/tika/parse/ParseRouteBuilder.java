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
package org.apache.camel.example.karaf.tika.parse;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.osgi.service.component.annotations.Component;

@Component(service = RouteBuilder.class)
public class ParseRouteBuilder extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        
        rest("/tika")
            .get()
                .produces("text/html")
                .bindingMode(RestBindingMode.off)
                .to("direct:content")
            .get("parse")
                .produces("text/plain")
                .param().name("filePath").type(RestParamType.query).description("Path to file").endParam()
                .to("direct:parse");
        
        from("direct:parse")
            .to("direct:convertPathToFile")
            .to("tika:parse?tikaParseOutputFormat=text")
            .removeHeaders("*");
    }
}
