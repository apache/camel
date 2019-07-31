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
package org.apache.camel.example.sql;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Bean that generates and process orders.
 */
public class OrderBean {

    private int counter;
    private Random ran = new Random();

    /**
     * Generates a new order structured as a {@link Map}
     */
    public Map<String, Object> generateOrder() {
        Map<String, Object> answer = new HashMap<>();
        answer.put("id", counter++);
        answer.put("item", counter % 2 == 0 ? 111 : 222);
        answer.put("amount", ran.nextInt(10) + 1);
        answer.put("description", counter % 2 == 0 ? "Camel in Action" : "ActiveMQ in Action");
        return answer;
    }

    /**
     * Processes the order
     *
     * @param data  the order as a {@link Map}
     * @return the transformed order
     */
    public String processOrder(Map<String, Object> data) {
        return "Processed order id " + data.get("id") + " item " + data.get("item") + " of " + data.get("amount") + " copies of " + data.get("description");
    }
}
