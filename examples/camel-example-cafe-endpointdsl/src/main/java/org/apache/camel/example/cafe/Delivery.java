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
package org.apache.camel.example.cafe;

import java.util.List;

public class Delivery {

    private static final String SEPARATOR = "-----------------------";

    private List<Drink> deliveredDrinks;

    private int orderNumber;

    public Delivery(List<Drink> deliveredDrinks) {
        assert deliveredDrinks.size() > 0;
        this.deliveredDrinks = deliveredDrinks;
        this.orderNumber = deliveredDrinks.get(0).getOrderNumber();
    }

    public int getOrderNumber() {
        return orderNumber;
    }

    public List<Drink> getDeliveredDrinks() {
        return deliveredDrinks;
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder(SEPARATOR + "\n");
        buffer.append("Order #").append(getOrderNumber()).append("\n");
        for (Drink drink : getDeliveredDrinks()) {
            buffer.append(drink).append("\n");
        }
        buffer.append(SEPARATOR + "\n");
        return buffer.toString();
    }

}

