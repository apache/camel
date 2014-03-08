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
package org.apache.camel.component.spring.batch.support;

import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;

/**
 * Spring Batch {@link ItemProcessor} sending synchronous request to the given Camel endpoint. The actual processing of
 * the item is delegated to the Camel routes.
 */
public class CamelItemProcessor<I, O> implements ItemProcessor<I, O> {

    private static final Logger LOG = LoggerFactory.getLogger(CamelItemProcessor.class);

    private final ProducerTemplate producerTemplate;

    private final String endpointUri;

    public CamelItemProcessor(ProducerTemplate producerTemplate, String endpointUri) {
        this.producerTemplate = producerTemplate;
        this.endpointUri = endpointUri;
    }

    @Override
    @SuppressWarnings("unchecked")
    public O process(I i) throws Exception {
        LOG.debug("processing item [{}]...", i);
        O result = (O) producerTemplate.requestBody(endpointUri, i);
        LOG.debug("processed item");
        return result;
    }

}
