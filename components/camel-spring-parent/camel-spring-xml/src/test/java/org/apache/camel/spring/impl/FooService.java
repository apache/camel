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
package org.apache.camel.spring.impl;

import org.apache.camel.Consume;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;

/**
 *
 */
public class FooService {

    private String fooEndpoint;
    private String barEndpoint;
    @Produce
    private ProducerTemplate bar;

    public String getFooEndpoint() {
        return fooEndpoint;
    }

    public void setFooEndpoint(String fooEndpoint) {
        this.fooEndpoint = fooEndpoint;
    }

    public String getBarEndpoint() {
        return barEndpoint;
    }

    public void setBarEndpoint(String barEndpoint) {
        this.barEndpoint = barEndpoint;
    }

    @Consume
    public void onFoo(String input) {
        bar.sendBody(input);
    }
}
