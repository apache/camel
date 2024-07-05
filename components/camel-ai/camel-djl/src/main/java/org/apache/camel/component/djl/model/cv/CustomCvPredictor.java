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
package org.apache.camel.component.djl.model.cv;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import ai.djl.Model;
import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.djl.DJLConstants;
import org.apache.camel.component.djl.model.AbstractPredictor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomCvPredictor<T> extends AbstractPredictor {

    private static final Logger LOG = LoggerFactory.getLogger(CustomCvPredictor.class);

    protected final String modelName;
    protected final String translatorName;

    public CustomCvPredictor(String modelName, String translatorName) {
        this.modelName = modelName;
        this.translatorName = translatorName;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        T result;
        if (body instanceof byte[]) {
            byte[] bytes = exchange.getIn().getBody(byte[].class);
            result = predict(exchange, new ByteArrayInputStream(bytes));
        } else if (body instanceof File) {
            result = predict(exchange, exchange.getIn().getBody(File.class));
        } else if (body instanceof InputStream) {
            result = predict(exchange, exchange.getIn().getBody(InputStream.class));
        } else {
            throw new RuntimeCamelException("Data type is not supported. Body should be byte[], InputStream or File");
        }
        exchange.getIn().setBody(result);
    }

    protected T predict(Exchange exchange, File input) {
        try (InputStream fileInputStream = new FileInputStream(input)) {
            Image image = ImageFactory.getInstance().fromInputStream(fileInputStream);
            return predict(exchange, image);
        } catch (IOException e) {
            LOG.error(FAILED_TO_TRANSFORM_MESSAGE);
            throw new RuntimeCamelException(FAILED_TO_TRANSFORM_MESSAGE, e);
        }
    }

    protected T predict(Exchange exchange, InputStream input) {
        try {
            Image image = ImageFactory.getInstance().fromInputStream(input);
            return predict(exchange, image);
        } catch (IOException e) {
            LOG.error(FAILED_TO_TRANSFORM_MESSAGE);
            throw new RuntimeCamelException(FAILED_TO_TRANSFORM_MESSAGE, e);
        }
    }

    protected T predict(Exchange exchange, Image image) {
        Model model = exchange.getContext().getRegistry().lookupByNameAndType(modelName, Model.class);
        @SuppressWarnings("unchecked")
        Translator<Image, T> translator
                = exchange.getContext().getRegistry().lookupByNameAndType(translatorName, Translator.class);

        exchange.getIn().setHeader(DJLConstants.INPUT, image);
        try (Predictor<Image, T> predictor = model.newPredictor(translator)) {
            return predictor.predict(image);
        } catch (TranslateException e) {
            LOG.error("Could not process input or output", e);
            throw new RuntimeCamelException("Could not process input or output", e);
        }
    }
}
