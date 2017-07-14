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

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

@UriEndpoint(scheme = "beverage", syntax = "beverage:drink", title = "Beverage", label = "bar", producerOnly = true)
public class BeverageEndpoint extends DefaultEndpoint {

    @UriPath @Metadata(required = "true")
    private Beverages drink;

    @UriParam(defaultValue = "1")
    private int amount = 1;

    @UriParam
    private boolean celebrity;

    public BeverageEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new BeverageProducer(this, drink, amount, celebrity);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public Beverages getDrink() {
        return drink;
    }

    /**
     * What drink to order
     */
    public void setDrink(Beverages drink) {
        this.drink = drink;
    }

    public int getAmount() {
        return amount;
    }

    /**
     * Number of drinks in the order
     */
    public void setAmount(int amount) {
        this.amount = amount;
    }

    public boolean isCelebrity() {
        return celebrity;
    }

    /**
     * Is this a famous person ordering
     */
    public void setCelebrity(boolean celebrity) {
        this.celebrity = celebrity;
    }
}
