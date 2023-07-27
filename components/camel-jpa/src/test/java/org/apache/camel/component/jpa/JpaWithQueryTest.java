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
package org.apache.camel.component.jpa;

import org.apache.camel.examples.MultiSteps;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisabledIfSystemProperty(named = "ci.env.name", matches = "apache.org",
                          disabledReason = "Apache CI is hanging on this test")
public class JpaWithQueryTest extends JpaWithNamedQueryTest {

    @Override
    protected void assertURIQueryOption(JpaConsumer jpaConsumer) {
        assertEquals("select o from " + entityName + " o where o.step = 1", jpaConsumer.getQuery());
    }

    @Override
    protected String getEndpointUri() {
        return "jpa://" + MultiSteps.class.getName() + "?query=select o from " + entityName + " o where o.step = 1";
    }
}
