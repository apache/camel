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

/**
 * Test fixture mirroring {@code src/main/resources/examples/quick-start/routes/Greeter.java}, which the CLI compiles at
 * runtime. A compiled copy is needed on the test classpath so that {@code examples/quick-start/routes/beans.yaml} can
 * instantiate its {@code greeter} bean while {@link org.apache.camel.dsl.jbang.core.common.ExampleRoutesLoadTest}
 * pre-parses it.
 *
 * Keep this in sync with the example source (synced from camel-jbang-examples). Only the type and its {@code greeting}
 * property are load-bearing for the test: a missing property fails bean binding loudly, whereas {@link #greet} is never
 * called (the test does not start the context), so behavioral drift would go unnoticed.
 */
public class Greeter {

    private String greeting;

    public void setGreeting(String greeting) {
        this.greeting = greeting;
    }

    public String greet(String name) {
        return greeting + ", " + name + "!";
    }
}
