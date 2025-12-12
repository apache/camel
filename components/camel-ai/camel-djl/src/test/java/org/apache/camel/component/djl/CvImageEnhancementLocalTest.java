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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;

import ai.djl.MalformedModelException;
import ai.djl.Model;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import ai.djl.util.TarUtils;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                          disabledReason = "Requires too much network resources")
public class CvImageEnhancementLocalTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(CvImageEnhancementLocalTest.class);

    private static final String MODEL_URL
            = "https://www.kaggle.com/api/v1/models/kaggle/esrgan-tf2/tensorFlow2/esrgan-tf2/1/download";
    private static final String MODEL_NAME = "esrgan-tf2";

    @BeforeAll
    public static void setupDefaultEngine() {
        // ESRGAN-TF2 is a TensorFlow model
        System.setProperty("ai.djl.default_engine", "TensorFlow");
    }

    @Test
    void testDJL() throws Exception {
        LOG.info("Read and load local model");
        loadLocalModel();

        LOG.info("Starting route to infer");
        context.createProducerTemplate().sendBody("controlbus:route?routeId=image_enhancement&action=start", null);
        var mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.await();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("file:src/test/resources/data/enhance?recursive=true&noop=true")
                        .routeId("image_enhancement").autoStartup(false)
                        .to("djl:cv/image_enhancement?model=MyModel&translator=MyTranslator")
                        .log("${header.CamelFileName} = ${body}")
                        .process(exchange -> {
                            var image = exchange.getIn().getBody(Image.class);
                            var os = new ByteArrayOutputStream();
                            image.save(os, "png");
                            exchange.getIn().setBody(os.toByteArray());
                        })
                        .to("file:target/output?fileName=CvImageEnhancementLocalTest-${date:now:ssSSS}.png")
                        .to("mock:result");
            }
        };
    }

    private void loadLocalModel() throws IOException, MalformedModelException, URISyntaxException {
        // Load a model
        var model = Model.newInstance(MODEL_NAME);
        // TfModel doesn't allow direct loading from remote input stream yet
        // https://github.com/deepjavalibrary/djl/issues/3303
        var modelDir = Files.createTempDirectory(MODEL_NAME);
        TarUtils.untar(new URI(MODEL_URL).toURL().openStream(), modelDir, true);
        model.load(modelDir);

        // Bind model beans
        context.getRegistry().bind("MyModel", model);
        context.getRegistry().bind("MyTranslator", new MyTranslator());
    }

    private static class MyTranslator implements Translator<Image, Image> {
        @Override
        public NDList processInput(TranslatorContext ctx, Image input) {
            NDManager manager = ctx.getNDManager();
            return new NDList(input.toNDArray(manager).toType(DataType.FLOAT32, false));
        }

        @Override
        public Image processOutput(TranslatorContext ctx, NDList list) {
            NDArray output = list.get(0).clip(0, 255);
            return ImageFactory.getInstance().fromNDArray(output.squeeze());
        }
    }
}
