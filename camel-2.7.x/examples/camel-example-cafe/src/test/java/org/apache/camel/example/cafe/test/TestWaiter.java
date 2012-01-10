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
package org.apache.camel.example.cafe.test;

import java.util.List;

import org.apache.camel.example.cafe.Delivery;
import org.apache.camel.example.cafe.Drink;
import org.apache.camel.example.cafe.stuff.Waiter;

public class TestWaiter extends Waiter {
    protected List<Drink> expectDrinks;
    protected List<Drink> deliveredDrinks;

    public void setVerfiyDrinks(List<Drink> drinks) {
        this.expectDrinks = drinks;
    }

    public void deliverCafes(Delivery delivery) {
        super.deliverCafes(delivery);
        deliveredDrinks = delivery.getDeliveredDrinks();
    }

    public void verifyDrinks() {
        if (deliveredDrinks == null || expectDrinks.size() != deliveredDrinks.size()) {
            throw new AssertionError("Did not deliver expected number of drinks " + expectDrinks.size() + " was "
                    + (deliveredDrinks != null ? deliveredDrinks.size() : "null"));
        }

        for (Drink drink : expectDrinks) {
            if (!deliveredDrinks.contains(drink)) {
                throw new AssertionError("Cannot find expected drink " + drink + " in the delivered drinks");
            }
        }
    }

}
