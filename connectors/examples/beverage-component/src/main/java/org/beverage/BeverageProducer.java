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
package org.beverage;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;

public class BeverageProducer extends DefaultProducer {

    private final Beverages drink;
    private final int amount;
    private final boolean celebrity;

    private transient int total;

    public BeverageProducer(Endpoint endpoint, Beverages drink, int amount, boolean celebrity) {
        super(endpoint);
        this.drink = drink;
        this.amount = amount;
        this.celebrity = celebrity;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        total += amount;

        exchange.getIn().setBody("total " + total + " of " + drink.name().toLowerCase() + " ordered"
            + (celebrity ? " from famous person" : ""));
    }
}
