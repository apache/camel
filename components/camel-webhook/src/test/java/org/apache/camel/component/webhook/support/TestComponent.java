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
package org.apache.camel.component.webhook.support;

import java.util.Map;
import java.util.function.Consumer;

import org.apache.camel.support.DefaultComponent;

public class TestComponent extends DefaultComponent {

    private TestEndpoint endpoint;

    private Consumer<TestEndpoint> customizer;

    public TestComponent() {
    }

    public TestComponent(Consumer<TestEndpoint> customizer) {
        this.customizer = customizer;
    }

    @Override
    protected TestEndpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (this.endpoint == null) {
            this.endpoint = new TestEndpoint(uri, this);
            if (this.customizer != null) {
                this.customizer.accept(this.endpoint);
            }
        }

        return this.endpoint;
    }

}
