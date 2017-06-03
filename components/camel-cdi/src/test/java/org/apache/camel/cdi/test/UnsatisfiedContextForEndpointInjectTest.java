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
package org.apache.camel.cdi.test;

import org.apache.camel.cdi.CdiCamelExtension;
import org.apache.camel.cdi.bean.EndpointInjectWrongContextRoute;
import org.apache.camel.cdi.rule.ExpectedDeploymentException;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.containsString;

@RunWith(Arquillian.class)
public class UnsatisfiedContextForEndpointInjectTest {

    @ClassRule
    public static TestRule exception = ExpectedDeploymentException.none()
        .expect(RuntimeException.class)
        .expectMessage(containsString("Error adding routes of type [" + EndpointInjectWrongContextRoute.class.getName() + "] to Camel context"))
        .expectMessage(containsString("No Camel context with name [foo] is deployed!"));

    @Deployment
    public static Archive<?> deployment() {
        return ShrinkWrap.create(JavaArchive.class)
            // Camel CDI
            .addPackage(CdiCamelExtension.class.getPackage())
            // Test class
            .addClass(EndpointInjectWrongContextRoute.class)
            // Bean archive deployment descriptor
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Test
    public void test() {
    }
}
