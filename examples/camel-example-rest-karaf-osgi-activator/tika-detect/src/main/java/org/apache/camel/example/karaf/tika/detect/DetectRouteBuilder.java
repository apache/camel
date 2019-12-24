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
package org.apache.camel.example.karaf.tika.detect;

import java.io.File;
import java.net.URLDecoder;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.example.karaf.models.TikaOutput;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.osgi.service.component.annotations.Component;

@Component(service = RouteBuilder.class)
public class DetectRouteBuilder extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        rest("/tika")
            .get("detect")
            .bindingMode(RestBindingMode.json)
            .outType(TikaOutput.class)
            .param().name("filePath").type(RestParamType.query).description("Path to file").endParam()
            .to("direct:detect");

        from("direct:detect")
            .to("direct:convertPathToFile")
            .to("tika:detect")
            .removeHeaders("*")
            .to("direct:convertTikaTextToOutput");
        
        from("direct:convertTikaTextToOutput")
            .convertBodyTo(String.class)
            .process(exchange -> {
                String body = exchange.getIn().getBody(String.class);
                TikaOutput tikaOutput = new TikaOutput();
                tikaOutput.setOutput(body);
                exchange.getIn().setBody(tikaOutput);
            });
        
        from("direct:convertPathToFile")
            .process(exchange -> {
                String path = exchange.getIn().getHeader("filePath", String.class);
                path = URLDecoder.decode(path, "UTF-8");
                File file = new File(path);
                exchange.getIn().setBody(file);
            });
    }
}
