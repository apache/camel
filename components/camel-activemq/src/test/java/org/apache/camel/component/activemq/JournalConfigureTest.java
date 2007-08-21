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
package org.apache.camel.component.activemq;

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;

/**
 * @version $Revision$
 */
public class JournalConfigureTest extends ContextTestSupport {

    public void testDefaltConfig() throws Exception {
        JournalEndpoint endpoint = resolveMandatoryEndpoint("activemq.journal:target/test");
        assertEquals("directory", new File("target", "test"), endpoint.getDirectory());
        assertEquals("syncConsume", false, endpoint.isSyncConsume());
        assertEquals("syncProduce", true, endpoint.isSyncProduce());
    }

    public void testConfigViaOptions() throws Exception {
        JournalEndpoint endpoint = resolveMandatoryEndpoint("activemq.journal:target/test?syncConsume=true&syncProduce=false");
        assertEquals("directory", new File("target", "test"), endpoint.getDirectory());
        assertEquals("syncConsume", true, endpoint.isSyncConsume());
        assertEquals("syncProduce", false, endpoint.isSyncProduce());
    }

    @Override
    protected JournalEndpoint resolveMandatoryEndpoint(String uri) {
        Endpoint endpoint = super.resolveMandatoryEndpoint(uri);
        return assertIsInstanceOf(JournalEndpoint.class, endpoint);
    }
}
