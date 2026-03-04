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
package org.apache.camel.component.huggingface;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

@UriEndpoint(firstVersion = "4.19.0", scheme = "huggingface", title = "Hugging Face",
             syntax = "huggingface:task", producerOnly = true, category = { Category.AI },
             headersClass = HuggingFaceConstants.class)
@Metadata(label = "ai")
public class HuggingFaceEndpoint extends DefaultEndpoint {

    @UriParam
    private HuggingFaceConfiguration configuration;

    public HuggingFaceEndpoint(String uri, Component component, HuggingFaceConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new HuggingFaceProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported for HuggingFace endpoint");
    }

    public HuggingFaceConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(HuggingFaceConfiguration configuration) {
        this.configuration = configuration;
    }
}
