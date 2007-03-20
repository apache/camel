/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.builder;

import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Exchange;
import org.apache.camel.processor.SendProcessor;

/**
 * @version $Revision$
 */
public class ToBuilder<E extends Exchange> extends FromBuilder<E> {
    private Endpoint<E> destination;

    public ToBuilder(FromBuilder<E> parent, Endpoint<E> endpoint) {
        super(parent);
        this.destination = endpoint;
    }

    @Override
    public Processor<E> createProcessor() {
        return new SendProcessor<E>(destination);
    }
}
