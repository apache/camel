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

import org.apache.camel.util.ObjectHelper;

public class OrderItem {

    private DrinkType type;

    private int shots = 1;

    private boolean iced;

    private final Order order;

    public OrderItem(Order order, DrinkType type, int shots, boolean iced) {
        this.order = order;
        this.type = type;
        this.shots = shots;
        this.iced = iced;
    }

    public Order getOrder() {
        return this.order;
    }

    public boolean isIced() {
        return this.iced;
    }

    public int getShots() {
        return shots;
    }

    public DrinkType getDrinkType() {
        return this.type;
    }

    @Override
    public String toString() {
        return ((this.iced) ? "iced " : "hot ") + this.shots + " shot " + this.type;
    }
    
    @Override
    public boolean equals(Object o) {
        if (o instanceof OrderItem) {
            OrderItem that = (OrderItem)o;
            return ObjectHelper.equal(this.type, that.type) 
                && ObjectHelper.equal(this.order.getNumber(), that.order.getNumber())
                && ObjectHelper.equal(this.iced, that.iced)
                && ObjectHelper.equal(this.shots, that.shots);                 
        }
        return false;
    }
  
    @Override
    public int hashCode() {
        if (iced) {
            return type.hashCode() + order.getNumber() * 10000 + shots * 100;
        } else {
            return type.hashCode() + order.getNumber() * 10000 + shots * 100 + 5;
        }
    }

}
