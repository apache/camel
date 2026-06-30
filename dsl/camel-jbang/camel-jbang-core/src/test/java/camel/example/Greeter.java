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
package camel.example;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Test fixture mirroring {@code src/main/resources/examples/routes/Greeter.java}, which the CLI compiles at runtime. A
 * compiled copy is needed on the test classpath so that {@code examples/routes/beans.yaml} can instantiate its
 * {@code greeter} bean while {@link org.apache.camel.dsl.jbang.core.common.ExampleRoutesLoadTest} pre-parses it.
 *
 * Keep this in sync with the example source. Only the type and its {@code message} property are load-bearing for the
 * test: a missing property fails bean binding loudly, whereas the {@link #process} behavior is never exercised (the
 * test does not start the context), so behavioral drift would go unnoticed.
 */
public class Greeter implements Processor {

    private String message;

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String body = exchange.getIn().getBody(String.class);
        exchange.getIn().setBody(message + " " + body);
    }

}
