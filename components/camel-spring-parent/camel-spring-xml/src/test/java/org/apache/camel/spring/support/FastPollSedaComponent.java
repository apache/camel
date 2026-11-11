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
package org.apache.camel.spring.support;

import org.apache.camel.component.seda.SedaComponent;
import org.apache.camel.spi.annotations.Component;

/**
 * A {@link SedaComponent} for the tests, which polls its queue every 10 ms instead of every 1000 ms.
 * <p/>
 * A seda consumer blocks in the poll of its queue, and stopping a CamelContext waits for the consumer to leave it, so
 * with the default each test stopping a context with a seda route spends up to a second in its shutdown. The seda
 * endpoints are created with the default of the component, so it has to be set before they are created: the
 * CamelContext of a Spring XML test is started when the Spring context is refreshed, before the test support can
 * configure the component. It is therefore registered as the "seda" component with
 * {@code META-INF/services/org/apache/camel/component/seda} of the test resources, which hides the one of camel-seda.
 */
@Component("seda")
public class FastPollSedaComponent extends SedaComponent {

    public FastPollSedaComponent() {
        setDefaultPollTimeout(10);
    }
}
