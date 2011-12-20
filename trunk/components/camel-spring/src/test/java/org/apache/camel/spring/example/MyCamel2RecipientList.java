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
package org.apache.camel.spring.example;

import org.apache.camel.Consume;
import org.apache.camel.RecipientList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version 
 */
public class MyCamel2RecipientList {

    private static final Logger LOG = LoggerFactory.getLogger(MyCamel2RecipientList.class);

    @Consume(uri = "direct:foo", context = "camel-2")
    @RecipientList(context = "camel-2")
    public String[] doSomething(String body) {
        LOG.info("Received body: " + body);

        return new String[]{"mock:foo", "mock:result"};
    }

}
