/**
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
package org.apache.camel.rx;

import org.apache.camel.rx.support.ExchangeToBodyFunc1;
import org.apache.camel.rx.support.ObservableProcessor;

/**
 * A base class for a {@link org.apache.camel.Processor} which allows you to process
 * messages using an {@link rx.Observable < org.apache.camel.Message>} by implementing the
 * abstract {@link org.apache.camel.rx.support.ObservableProcessor#configure(rx.Observable) method.
 */
public abstract class ObservableBody<T> extends ObservableProcessor<T> {
    private final Class<T> bodyType;

    public ObservableBody(Class<T> bodyType) {
        super(new ExchangeToBodyFunc1<>(bodyType));
        this.bodyType = bodyType;
    }

    @Override
    public String toString() {
        return "ObservableBody[" + bodyType.getName() + "]";
    }
}
