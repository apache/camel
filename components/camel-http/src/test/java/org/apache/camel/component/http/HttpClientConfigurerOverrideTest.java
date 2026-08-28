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
package org.apache.camel.component.http;

import java.util.Map;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpClientConfigurerOverrideTest extends CamelTestSupport {

    @Test
    public void existingTwoArgumentOverrideIsStillInvoked() {
        TrackingHttpComponent component = new TrackingHttpComponent();
        context.addComponent("http-tracking", component);

        assertThat(context.getEndpoint("http-tracking://localhost:8080")).isNotNull();
        assertThat(component.invoked).isTrue();
    }

    private static final class TrackingHttpComponent extends HttpComponent {

        private boolean invoked;

        @Override
        protected HttpClientConfigurer createHttpClientConfigurer(Map<String, Object> parameters, boolean secure)
                throws Exception {
            invoked = true;
            return super.createHttpClientConfigurer(parameters, secure);
        }
    }
}
