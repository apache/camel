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

package org.apache.camel.example.cdi.metrics;

import javax.enterprise.context.ApplicationScoped;

import com.codahale.metrics.annotation.Metered;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeExchangeException;

@ApplicationScoped
public class UnreliableService {

    @Metered
    public void attempt(Exchange exchange) {
        if (Math.random() < 0.5) {
            throw new RuntimeExchangeException("Random failure", exchange);
        }
    }
}
