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

@Component(TahuConstants.EDGE_NODE_SCHEME)
public class TahuEdgeComponent extends TahuDefaultComponent {
    public TahuEdgeComponent() {
        super();
    }

    public TahuEdgeComponent(CamelContext context) {
        super(context);
    }

    public TahuEdgeComponent(TahuConfiguration configuration) {
        super(configuration);
    }

    protected TahuEdgeEndpoint doCreateEndpoint(
            String uri, List<String> descriptorSegments, TahuConfiguration tahuConfig) throws Exception {

        String groupId = descriptorSegments.get(0);
        String edgeNode = descriptorSegments.get(1);
        String deviceId = null;

        if (descriptorSegments.size() == 3) {
            deviceId = descriptorSegments.get(2);
        }

        return new TahuEdgeEndpoint(uri, this, tahuConfig, groupId, edgeNode, deviceId);
    }

    public final TahuEdgeEndpoint createEdgeNodeEndpoint(String groupId, String edgeNode) throws Exception {
        String sparkplugDescriptorString = groupId + TahuConstants.MAJOR_SEPARATOR + edgeNode;
        String uri = TahuConstants.EDGE_NODE_SCHEME + ":" + sparkplugDescriptorString;

        TahuEdgeEndpoint endpoint = (TahuEdgeEndpoint) createEndpoint(uri, sparkplugDescriptorString, Map.of());

        return endpoint;
    }
}
