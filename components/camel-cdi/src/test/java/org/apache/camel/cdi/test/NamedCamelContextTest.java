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
package org.apache.camel.cdi.test;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.inject.Named;

import org.apache.camel.CamelContext;
import org.apache.camel.cdi.CdiCamelExtension;
import org.apache.camel.impl.DefaultCamelContext;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.Assert.assertThat;

@RunWith(Arquillian.class)
public class NamedCamelContextTest {

    @Named
    @Produces
    @ApplicationScoped
    private CamelContext emptyNamedFieldContext = new DefaultCamelContext();

    @Produces
    @ApplicationScoped
    @Named("named-field-context")
    private CamelContext namedFieldContext = new DefaultCamelContext();

    @Named
    @Produces
    @ApplicationScoped
    private CamelContext getEmptyNamedGetterContext() {
        return new DefaultCamelContext();
    }

    @Named
    @Produces
    @ApplicationScoped
    private CamelContext getEmptyNamedMethodContext() {
        return new DefaultCamelContext();
    }

    @Produces
    @ApplicationScoped
    @Named("named-getter-context")
    private CamelContext getNamedGetterContext() {
        return new DefaultCamelContext();
    }

    @Produces
    @ApplicationScoped
    @Named("named-method-context")
    private CamelContext getNamedMethodContext() {
        return new DefaultCamelContext();
    }

    @Deployment
    public static Archive<?> deployment() {
        return ShrinkWrap.create(JavaArchive.class)
            // Camel CDI
            .addPackage(CdiCamelExtension.class.getPackage())
            // Bean archive deployment descriptor
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Test
    public void verifyCamelContexts(Instance<CamelContext> contexts) {
        assertThat(contexts, containsInAnyOrder(
            hasProperty("name", equalTo("emptyNamedFieldContext")),
            hasProperty("name", equalTo("emptyNamedGetterContext")),
            hasProperty("name", equalTo("emptyNamedMethodContext")),
            hasProperty("name", equalTo("named-field-context")),
            hasProperty("name", equalTo("named-getter-context")),
            hasProperty("name", equalTo("named-method-context")),
            hasProperty("name", equalTo("emptyNamedBeanContext")),
            hasProperty("name", equalTo("named-bean-context"))
        ));
    }

    @Named
    @ApplicationScoped
    static class EmptyNamedBeanContext extends DefaultCamelContext {
    }

    @ApplicationScoped
    @Named("named-bean-context")
    static class NamedBeanContext extends DefaultCamelContext {
    }
}
