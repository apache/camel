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
package org.apache.camel.component.etcd.policy;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.etcd.EtcdConstants;
import org.apache.camel.main.Main;

public final class EtcdRoutePolicyMain {

    private EtcdRoutePolicyMain() {
    }

    public static void main(final String[] args) throws Exception {
        Main main = new Main();
        main.addRouteBuilder(new RouteBuilder() {
            public void configure() {
                EtcdRoutePolicy policy = new EtcdRoutePolicy();
                policy.setClientUris(EtcdConstants.ETCD_DEFAULT_URIS);
                policy.setServicePath("/camel/services/leader");
                policy.setServiceName(args[1]);
                policy.setTtl(15);

                from("file:///tmp/camel?delete=true")
                    .routeId(args[1])
                    .routePolicy(policy)
                    .setHeader("EtcdRouteID", constant(args[1]))
                    .setHeader("EtcdServiceName", constant(args[0]))
                    .to("log:org.apache.camel.component.etcd?level=INFO&showAll=true");
            }
        });

        main.run();
    }
}
