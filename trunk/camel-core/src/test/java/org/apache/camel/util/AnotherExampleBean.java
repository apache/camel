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
package org.apache.camel.util;

import java.util.Collection;
import java.util.Date;

/**
 * @version 
 */
public class AnotherExampleBean {
    private String id;
    private String name;
    private double price;
    private Date date;
    private Collection<?> children;
    private Boolean goldCustomer;
    private boolean little;

    public String toString() {
        return "AnotherExampleBean[name: " + name + " price: " + price + " id: " + id + "]";
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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public Collection<?> getChildren() {
        return children;
    }

    public void setChildren(Collection<?> children) {
        this.children = children;
    }

    public Boolean isGoldCustomer() {
        return goldCustomer;
    }

    public void setGoldCustomer(Boolean goldCustomer) {
        this.goldCustomer = goldCustomer;
    }

    public boolean isLittle() {
        return little;
    }

    public void setLittle(boolean little) {
        this.little = little;
    }
}