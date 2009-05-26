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
package org.apache.camel.component.ibatis;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version $Revision$
 */
public class IBatisUnknownStatementTypeTest extends ContextTestSupport {

    public void testStatementTypeNotSet() throws Exception {
        try {
            template.sendBody("direct:start", "Hello");
            fail("Should have thrown an IllegalArgumentException");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(FailedToCreateProducerException.class, e.getCause());
            assertEquals("statementType must be specified on: Endpoint[ibatis:selectAllAccounts]", e.getCause().getCause().getMessage());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("ibatis:selectAllAccounts");
            }
        };
    }
}
