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
package org.apache.camel.component.djl;

import java.io.ByteArrayOutputStream;

import ai.djl.modality.cv.Image;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class CvImageGenerationTest extends CamelTestSupport {

    @BeforeAll
    public static void setupDefaultEngine() {
        // Since Apache MXNet is discontinued, prefer PyTorch as the default engine
        System.setProperty("ai.djl.default_engine", "PyTorch");
    }

    @Test
    void testDJL() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(5);
        mock.await();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("timer:testDJL?repeatCount=1")
                        .setBody(constant(new int[] { 100, 207, 971, 970, 933 }))
                        .to("djl:cv/image_generation?artifactId=ai.djl.pytorch:biggan-deep:0.0.1")
                        .split(body())
                        .log("image = ${body}")
                        .process(exchange -> {
                            var image = exchange.getIn().getBody(Image.class);
                            var os = new ByteArrayOutputStream();
                            image.save(os, "png");
                            exchange.getIn().setBody(os.toByteArray());
                        })
                        .to("file:target/output?fileName=CvImageGenerationTest-${date:now:ssSSS}.png")
                        .to("mock:result");
            }
        };
    }

}
