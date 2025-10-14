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

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.component.huggingface.tasks.HuggingFaceTask;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HealthCheckComponent;
import org.apache.camel.util.ObjectHelper;

@Component("huggingface")
@Metadata(label = "ai")
public class HuggingFaceComponent extends HealthCheckComponent {

    @Metadata
    private HuggingFaceConfiguration configuration = new HuggingFaceConfiguration();

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        HuggingFaceConfiguration configuration
                = this.configuration != null ? this.configuration.copy() : new HuggingFaceConfiguration();

        HuggingFaceEndpoint endpoint = new HuggingFaceEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        if (remaining != null && !remaining.isEmpty()) {
            String normalized = remaining.toUpperCase().replace("-", "_");
            try {
                configuration.setTask(HuggingFaceTask.valueOf(normalized));
            } catch (IllegalArgumentException e) {
                if (ObjectHelper.isEmpty(configuration.getPredictorBean())) {
                    throw new IllegalArgumentException(
                            String.format("Custom camel-huggingface task %s was specified, please set predictor bean option",
                                    normalized));
                }
            }
        }
        return endpoint;
    }

    public HuggingFaceConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The configuration.
     */
    public void setConfiguration(HuggingFaceConfiguration configuration) {
        this.configuration = configuration;
    }
}
