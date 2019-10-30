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
package org.apache.camel.component.kudu;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.kudu.client.KuduClient;
import org.apache.kudu.test.KuduTestHarness;
import org.junit.Assert;

/**
 * Use this class to run tests agains a local basic Kudu server. This
 * local kudu server is spinned up by
 * https://kudu.apache.org/docs/developing.html#_using_the_kudu_binary_test_jar
 */
public class IntegrationKuduConfiguration extends KuduTestHarness {

    public IntegrationKuduConfiguration() {
        super();
    }

    /**
     * Setup the Camel Context and make sure all KuduEndpoints use the
     * special KuduClient that connects to the local server.
     *
     * @param context
     */
    public void setupCamelContext(CamelContext context) {
        KuduClient client = this.getClient();
        Assert.assertNotNull(client);
        Assert.assertNotNull(context);
        for (Endpoint endpoint : context.getEndpoints()) {
            if (endpoint instanceof KuduEndpoint) {
                ((KuduEndpoint) endpoint).setKuduClient(this.getClient());
            }
        }
    }

    /**
     * Needed because we have to shutdown the local cluster, but as
     * Camel already closed the client, it will always throw an IllegalStateException.
     */
    @Override
    public void after() {
        try {
            super.after();
        } catch (java.lang.IllegalStateException e) {
            //Camel already closed the client so an exception will be thrown
            //no need to worry
        }
    }
}
