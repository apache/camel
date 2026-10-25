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
package org.apache.camel.component.springai.image;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Spring AI Image Generation
 */
@UriEndpoint(firstVersion = "4.19.0", scheme = SpringAiImage.SCHEME, title = "Spring AI Image",
             syntax = "spring-ai-image:imageId", producerOnly = true, category = {
                     Category.AI
             }, headersClass = SpringAiImageHeaders.class)
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  description = "The prompt as text, or an ImagePrompt, which keeps its own options: n,"
                                                + " width and the other options and headers then do not apply. Another"
                                                + " body is converted to text, and no body fails"),
           producerOut = @BodyType(value = { Carrier.POJO, Carrier.LIST },
                                   description = "The generated org.springframework.ai.image.Image, with a URL or base64"
                                                 + " data, or a List of Image when the model returns more than one (n"
                                                 + " above 1). An Image converts to byte[] by decoding the base64 data;"
                                                 + " an Image with only a URL gives the bytes of the URL text"))
public class SpringAiImageEndpoint extends DefaultEndpoint {
    @Metadata(required = true)
    @UriPath(description = "The id")
    private final String imageId;

    @UriParam
    private SpringAiImageConfiguration configuration;

    public SpringAiImageEndpoint(
                                 String endpointUri,
                                 Component component,
                                 String imageId,
                                 SpringAiImageConfiguration configuration) {

        super(endpointUri, component);

        this.imageId = imageId;
        this.configuration = configuration;
    }

    public SpringAiImageConfiguration getConfiguration() {
        return this.configuration;
    }

    public String getImageId() {
        return this.imageId;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SpringAiImageProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer is not implemented for this component");
    }
}
