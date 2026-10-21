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
package camel.example;

import java.util.List;

/**
 * An order of the shop, as the JSON of POST /orders binds to it: the REST DSL turns the request body into this record
 * (bindingMode json, type camel.example.Order), and the record back into JSON for the answer.
 */
public record Order(String orderId, String customer, String country, String status, List<Line> lines) {

    /** One line of an order. */
    public record Line(String sku, int qty, double price) {
    }
}
