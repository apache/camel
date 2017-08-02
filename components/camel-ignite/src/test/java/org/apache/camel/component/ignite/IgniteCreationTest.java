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
package org.apache.camel.component.ignite;

import org.apache.camel.component.ignite.cache.IgniteCacheComponent;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.junit.After;
import org.junit.Test;

public class IgniteCreationTest extends AbstractIgniteTest {
    
    private Ignite ignite;

    @Override
    protected String getScheme() {
        return "ignite-cache";
    }

    @Override
    protected AbstractIgniteComponent createComponent() {
        ignite = Ignition.start(createConfiguration());
        return IgniteCacheComponent.fromIgnite(ignite);
    }

    @Test
    public void testCAMEL11382() {
        assertNotNull(ignite());
    }

    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }
    
    @After
    public void stopUserManagedIgnite() {
        if (ignite != null) {
            Ignition.stop(ignite.name(), true);
        }
    }

}
