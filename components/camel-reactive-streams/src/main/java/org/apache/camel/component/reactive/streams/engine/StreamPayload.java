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
package org.apache.camel.component.reactive.streams.engine;

import org.apache.camel.component.reactive.streams.api.DispatchCallback;

/**
 * A helper object that wraps the emitted item and the corresponding dispatch callback.
 */
public class StreamPayload<D> {

    private D item;

    private DispatchCallback<D> callback;

    public StreamPayload(D item, DispatchCallback<D> callback) {
        this.item = item;
        this.callback = callback;
    }

    public D getItem() {
        return item;
    }

    public DispatchCallback<D> getCallback() {
        return callback;
    }


    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("StreamPayload{");
        sb.append("item=").append(item);
        sb.append(", callback=").append(callback);
        sb.append('}');
        return sb.toString();
    }
}
