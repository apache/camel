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
package org.apache.camel.example.pojo;

import org.apache.camel.Consume;
import org.apache.camel.RecipientList;
import org.apache.camel.language.xpath.XPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//START SNIPPET: ex
public class DistributeRecordsBean {

    private static final Logger LOG = LoggerFactory.getLogger(DistributeRecordsBean.class);

    @Consume("activemq:personnel.records")
    @RecipientList
    public String[] route(@XPath("/person/city/text()") String city) {
        if (city.equals("London")) {
            LOG.info("Person is from EMEA region");
            return new String[] {"file:target/messages/emea/hr_pickup",
                                 "file:target/messages/emea/finance_pickup"};
        } else {
            LOG.info("Person is from AMER region");
            return new String[] {"file:target/messages/amer/hr_pickup",
                                 "file:target/messages/amer/finance_pickup"};
        }
    }
}
//END SNIPPET: ex

