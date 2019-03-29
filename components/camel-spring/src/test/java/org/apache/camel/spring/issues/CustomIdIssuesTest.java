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
package org.apache.camel.spring.issues;

import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.LogDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.WhenDefinition;
import org.apache.camel.spring.SpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CustomIdIssuesTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/issues/CustomIdIssueTest.xml");
    }

    @Test
    public void testCustomId() {
        RouteDefinition route = context.getRouteDefinition("myRoute");
        assertNotNull(route);
        assertTrue(route.hasCustomIdAssigned());

        FromDefinition from = route.getInput();
        assertEquals("fromFile", from.getId());
        assertTrue(from.hasCustomIdAssigned());

        ChoiceDefinition choice = (ChoiceDefinition) route.getOutputs().get(0);
        assertEquals("myChoice", choice.getId());
        assertTrue(choice.hasCustomIdAssigned());

        WhenDefinition when = choice.getWhenClauses().get(0);
        assertTrue(when.hasCustomIdAssigned());
        assertEquals("UK", when.getId());

        LogDefinition log = (LogDefinition) choice.getOtherwise().getOutputs().get(0);
        assertFalse(log.hasCustomIdAssigned());
    }
}

