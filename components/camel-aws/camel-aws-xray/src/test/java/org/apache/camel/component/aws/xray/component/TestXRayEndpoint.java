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
package org.apache.camel.component.aws.xray.component;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.support.DefaultEndpoint;

public class TestXRayEndpoint extends DefaultEndpoint {

    private final String remaining;

    public TestXRayEndpoint(final String uri, final String remaining, final TestXRayComponent component) {
        super(uri, component);

        this.remaining = remaining;
    }

    @Override
    public TestXRayComponent getComponent() {
        return (TestXRayComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() {
        return new TestXRayProducer(this, remaining);
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException("You cannot create a Consumer for message monitoring");
    }
}
