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
package org.apache.camel.view;

import java.io.File;
import javax.xml.bind.JAXBContext;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class ModelFileGeneratorTest extends ContextTestSupport {
    protected String outputDirectory = "target/site/model";

    @Override
    protected void setUp() throws Exception {
        deleteDirectory(outputDirectory);
        super.setUp();
    }

    public void testGenerateModel() throws Exception {
        try {
            ModelFileGenerator generator = new ModelFileGenerator(JAXBContext.newInstance("org.apache.camel.model"));
            generator.marshalRoutesUsingJaxb(outputDirectory + "/route.xml", context.getRouteDefinitions());
        } catch (IllegalArgumentException e) {
            if (e.getMessage().startsWith("Not supported")) {
                // ignore as some OS does not support indent-number etc.
                return;
            } else {
                throw e;
            }
        }

        File out = new File(outputDirectory + "/route.xml");
        assertTrue("File should have been generated", out.exists());

        String content = context.getTypeConverter().convertTo(String.class, out);
        assertTrue("Should contain a route", content.contains("<route"));
        assertTrue("Should contain a route", content.contains("</route>"));
        assertTrue("Should contain a route", content.contains("direct:start"));
        assertTrue("Should contain a route", content.contains("mock:result"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("mock:result");
            }
        };
    }
}
