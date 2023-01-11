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
package org.apache.camel.test.cdi;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

@ExtendWith(CamelCdiExtension.class)
class CamelCdiCustomContextTest {

    @Default
    @Named("camel-test-cdi")
    @ApplicationScoped
    static class CustomCamelContext extends DefaultCamelContext {

        @PostConstruct
        void customize() {
            disableJMX();
        }
    }

    @Inject
    CamelContext context;

    @Test
    void test() {
        assertThat("Camel context type is incorrect!",
                context,
                is(instanceOf(CustomCamelContext.class)));
        assertThat("JMX should be disabled", ((DefaultCamelContext) context).isJMXDisabled(), is(true));
    }
}
