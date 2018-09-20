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
package org.apache.camel.itest.jms;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.util.ObjectHelper;

/**
 * @version 
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class PurchaseOrder implements Serializable {
    private static final long serialVersionUID = 1L;
    @XmlAttribute
    private String name;
    @XmlAttribute
    private double price;
    @XmlAttribute
    private double amount;

    @Override
    public String toString() {
        return "PurchaseOrder[name: " + name + " amount: " + amount + " price: " + price + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PurchaseOrder) {
            PurchaseOrder that = (PurchaseOrder)o;
            return ObjectHelper.equal(this.name, that.name) && ObjectHelper.equal(this.amount, that.amount)
                   && ObjectHelper.equal(this.price, that.price);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return name.hashCode() + (int)Math.round(price * 100) + (int)Math.round(amount * 100);
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

}
