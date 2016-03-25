/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.zipkin;

import java.util.Collection;
import java.util.Collections;

import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.ServerResponseAdapter;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

public class ZipkinServerResponseAdapter implements ServerResponseAdapter {

    private final Exchange exchange;
    private final Endpoint endpoint;

    public ZipkinServerResponseAdapter(Exchange exchange) {
        this.exchange = exchange;
        this.endpoint = exchange.getFromEndpoint();
    }

    @Override
    public Collection<KeyValueAnnotation> responseAnnotations() {
        if (exchange.getException() != null) {
            return Collections.singletonList(KeyValueAnnotation.create("failure", exchange.getException().getMessage()));
        } else {
            return Collections.emptyList();
        }
    }
}
