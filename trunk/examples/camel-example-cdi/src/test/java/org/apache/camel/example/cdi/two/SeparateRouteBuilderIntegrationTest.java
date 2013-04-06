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
package org.apache.camel.example.cdi.two;

import javax.inject.Inject;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;

// TODO: This should be refactored, to unit test the MyRoutes from the src/main/java
// we should not add new routes and whatnot. This is an example for end-users to use as best practice
// so we should show them how to unit test their routes from their main source code

/**
 * Lets use a separate {@link org.apache.camel.example.cdi.two.TestRouteBuilder} to test the routes
 */
@RunWith(Arquillian.class)
public class SeparateRouteBuilderIntegrationTest extends DeploymentFactory {

    @Inject
    TestRouteBuilder testRouteBuilder;

    @Test
    public void integrationTest() throws Exception {
        assertNotNull("testRouteBuilder not injected!", testRouteBuilder);

        testRouteBuilder.assertIsSatisfied();
    }
}
