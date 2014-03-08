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

import java.util.List;

import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemWriter;

public class CamelItemWriter<I> implements ItemWriter<I> {

    private static final Logger LOG = LoggerFactory.getLogger(CamelItemWriter.class);

    private final ProducerTemplate producerTemplate;

    private final String endpointUri;

    public CamelItemWriter(ProducerTemplate producerTemplate, String endpointUri) {
        this.producerTemplate = producerTemplate;
        this.endpointUri = endpointUri;
    }

    @Override
    public void write(List<? extends I> items) throws Exception {
        for (I item : items) {
            LOG.debug("writing item [{}]...", item);
            producerTemplate.sendBody(endpointUri, item);
            LOG.debug("wrote item");
        }
    }

}
