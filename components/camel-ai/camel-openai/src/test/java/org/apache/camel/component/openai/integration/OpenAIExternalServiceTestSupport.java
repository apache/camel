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
package org.apache.camel.component.openai.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.CamelContext;
import org.apache.camel.component.openai.OpenAIComponent;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;

abstract class OpenAIExternalServiceTestSupport extends CamelTestSupport {

    static final String ENABLE_LIVE_TESTS = "openai.live.tests";
    static final String BASE_URL = "openai.live.baseUrl";
    static final String API_KEY = "openai.live.apiKey";

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        OpenAIComponent component = new OpenAIComponent();
        component.setBaseUrl(requiredProperty(BASE_URL));
        component.setApiKey(System.getProperty(API_KEY, "dummy"));
        context.addComponent("openai", component);
        return context;
    }

    protected static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (ObjectHelper.isEmpty(value)) {
            throw new IllegalStateException("Set the " + name + " system property when enabling live OpenAI tests");
        }
        return value;
    }

    protected static void writeArtifact(String fileName, byte[] contents) throws IOException {
        Path artifact = Path.of("target", "openai-live", fileName);
        Files.createDirectories(artifact.getParent());
        Files.write(artifact, contents);
    }
}
