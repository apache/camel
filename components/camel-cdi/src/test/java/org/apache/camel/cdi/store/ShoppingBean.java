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
package org.apache.camel.cdi.store;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.enterprise.inject.New;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Named;

@Named
public class ShoppingBean implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject
    private Products products;
    private List<Item> items = new ArrayList<Item>();

    public ShoppingBean() {
    }

    @Inject
    public ShoppingBean(@New Item defaultItem) {
        defaultItem.setName("Default Item");
        defaultItem.setPrice(1000L);

        items.add(defaultItem);
    }

    @Produces
    @Named("selectedItems")
    public List<Item> listSelectedItems() {
        return this.items;
    }

    @Produces
    @Named("allProducts")
    public List<Item> listAllProducts() {
        return this.products.getProducts();
    }

}
