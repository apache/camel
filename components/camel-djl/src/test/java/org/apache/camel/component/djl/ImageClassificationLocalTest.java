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

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import ai.djl.MalformedModelException;
import ai.djl.Model;
import ai.djl.basicmodelzoo.basic.Mlp;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.transform.ToTensor;
import ai.djl.modality.cv.translator.ImageClassificationTranslator;
import ai.djl.translate.Translator;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ImageClassificationLocalTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ImageClassificationLocalTest.class);

    private static final String MODEL_DIR = "src/test/resources/models/mnist";
    private static final String MODEL_NAME = "mlp";

    @Test
    void testDJL() throws Exception {
        LOG.info("Read and load local model");
        loadLocalModel();

        LOG.info("Starting route to infer");
        context.createProducerTemplate().sendBody("controlbus:route?routeId=infer&action=start", null);
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(98);
        mock.await();
        long count = mock.getExchanges().stream().filter(exchange -> exchange.getIn().getBody(Boolean.class)).count();
        assertEquals(97, count);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("file:src/test/resources/data/mnist?recursive=true&noop=true")
                        .routeId("infer").autoStartup(false)
                        .convertBodyTo(byte[].class)
                        .to("djl:cv/image_classification?model=MyModel&translator=MyTranslator")
                        .log("${header.CamelFileName} = ${body}")
                        .process(exchange -> {
                            String filename = exchange.getIn().getHeader("CamelFileName", String.class);
                            Map<String, Float> result = exchange.getIn().getBody(Map.class);
                            String max = Collections.max(result.entrySet(), Comparator.comparingDouble(Map.Entry::getValue))
                                    .getKey();
                            exchange.getIn().setBody(filename.startsWith(max));
                        })
                        .log("${header.CamelFileName} = ${body}")
                        .to("mock:result");
            }
        };
    }

    private void loadLocalModel() throws IOException, MalformedModelException {
        // create deep learning model
        Model model = Model.newInstance(MODEL_NAME);
        model.setBlock(new Mlp(28 * 28, 10, new int[] { 128, 64 }));
        model.load(Paths.get(MODEL_DIR), MODEL_NAME);
        // create translator for pre-processing and postprocessing
        List<String> classes = IntStream.range(0, 10).mapToObj(String::valueOf).collect(Collectors.toList());
        Translator<Image, Classifications> translator
                = ImageClassificationTranslator.builder().addTransform(new ToTensor()).optSynset(classes).build();

        // Bind model beans
        context.getRegistry().bind("MyModel", model);
        context.getRegistry().bind("MyTranslator", translator);
    }
}
