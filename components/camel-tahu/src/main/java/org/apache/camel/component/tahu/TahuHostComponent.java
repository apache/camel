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

package org.apache.camel.component.tahu;

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.annotations.Component;

@Component(TahuConstants.HOST_APP_SCHEME)
public class TahuHostComponent extends TahuDefaultComponent {
    public TahuHostComponent() {
        super();
    }

    public TahuHostComponent(CamelContext context) {
        super(context);
    }

    public TahuHostComponent(TahuConfiguration configuration) {
        super(configuration);
    }

    protected TahuHostEndpoint doCreateEndpoint(
            String uri, List<String> descriptorSegments, TahuConfiguration tahuConfig) throws Exception {

        String hostId = descriptorSegments.get(0);

        return new TahuHostEndpoint(uri, this, tahuConfig, hostId);
    }

    public final TahuHostEndpoint createHostAppEndpoint(String hostId) throws Exception {
        String uri = TahuConstants.HOST_APP_SCHEME + ":" + hostId;

        TahuHostEndpoint endpoint = (TahuHostEndpoint) createEndpoint(uri, hostId, Map.of());

        return endpoint;
    }
}
