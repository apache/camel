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

public class Drink {

    private boolean iced;

    private int shots;

    private DrinkType drinkType;

    private int orderNumber;

    public Drink(int orderNumber, DrinkType drinkType, boolean hot, int shots) {
        this.orderNumber = orderNumber;
        this.drinkType = drinkType;
        this.iced = hot;
        this.shots = shots;
    }

    public int getOrderNumber() {
        return orderNumber;
    }

    @Override
    public String toString() {
        return (iced ? "Iced" : "Hot") + " " + drinkType.toString() + ", " + shots + " shots.";
    }
    
    @Override
    public boolean equals(Object o) {
        if (o instanceof Drink) {
            Drink that = (Drink)o;
            return ObjectHelper.equal(this.drinkType, that.drinkType) 
                && ObjectHelper.equal(this.orderNumber, that.orderNumber)
                && ObjectHelper.equal(this.iced, that.iced)
                && ObjectHelper.equal(this.shots, that.shots);                 
        }
        return false;
    }
  
    @Override
    public int hashCode() {
        if (iced) {
            return drinkType.hashCode() + orderNumber * 1000 + shots * 100;
        } else {
            return drinkType.hashCode() + orderNumber * 1000 + shots * 100 + 5;
        }
    }

}
