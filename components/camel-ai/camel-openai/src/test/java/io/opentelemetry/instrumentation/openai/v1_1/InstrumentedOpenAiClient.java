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
package io.opentelemetry.instrumentation.openai.v1_1;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * Test stand-in for the proxy handler of the OpenTelemetry openai-java instrumentation (the Java agent uses the same
 * class under its shaded package). It carries the real class name so that the detection is tested on that name: it
 * would clash if the OpenTelemetry instrumentation library were ever added to the test classpath of this module.
 */
public class InstrumentedOpenAiClient implements InvocationHandler {

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        return null;
    }
}
