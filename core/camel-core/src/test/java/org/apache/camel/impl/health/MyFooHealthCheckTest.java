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
package org.apache.camel.impl.health;

import java.util.Collection;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.HealthCheckRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MyFooHealthCheckTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testMyFoo() throws Exception {
        context.start();

        HealthCheck hc
                = context.adapt(ExtendedCamelContext.class).getHealthCheckResolver().resolveHealthCheck("myfoo");
        Assertions.assertNotNull(hc);

        Assertions.assertEquals("acme", hc.getGroup());
        Assertions.assertEquals("myfoo", hc.getId());

        HealthCheck.Result r = hc.call();

        Assertions.assertEquals(HealthCheck.State.DOWN, r.getState());
        Assertions.assertEquals("Chaos Monkey was here", r.getMessage().get());
    }

    @Test
    public void testAddToRegistry() throws Exception {
        context.start();

        HealthCheck hc
                = context.adapt(ExtendedCamelContext.class).getHealthCheckResolver().resolveHealthCheck("myfoo");
        Assertions.assertNotNull(hc);

        HealthCheckRegistry hcr = context.getExtension(HealthCheckRegistry.class);
        hcr.register(hc);

        Collection<HealthCheck.Result> col = HealthCheckHelper.invoke(context);
        Assertions.assertEquals(1, col.size());

        HealthCheck.Result r = col.iterator().next();
        Assertions.assertEquals(HealthCheck.State.DOWN, r.getState());
        Assertions.assertEquals("Chaos Monkey was here", r.getMessage().get());
    }

}
