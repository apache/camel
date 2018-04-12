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
package org.apache.camel.component.ganglia;

import java.util.HashMap;
import java.util.Map;

import info.ganglia.gmetric4j.gmetric.GMetricSlope;
import info.ganglia.gmetric4j.gmetric.GMetricType;

import org.apache.camel.ResolveEndpointFailedException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * {@code GangliaCamelTest} is not shipped with an embedded gmond agent, as such
 * datagrams sent by the camel-ganglia component during those tests are simply
 * dropped at UDP level.
 */
public class GangliaCamelTest extends CamelGangliaTestSupport {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private String getTestUri() {
        return "ganglia:localhost:" + getTestPort() + "?mode=UNICAST&metricName=temperature&type=Double";
    }

    @Test
    public void sendShouldNotThrow() {
        template.sendBody(getTestUri(), 28.0);
    }

    @Test
    public void sendUsingWireFormat30xShouldNotThrow() {
        template.sendBody(getTestUri() + "&wireFormat31x=false", 28.0);
    }

    @Test
    public void sendMessageHeadersOverridingEndpointOptionsShouldNotThrow() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(GangliaConstants.GROUP_NAME, "sea-mesure");
        headers.put(GangliaConstants.METRIC_NAME, "depth");
        headers.put(GangliaConstants.METRIC_TYPE, GMetricType.FLOAT);
        headers.put(GangliaConstants.METRIC_SLOPE, GMetricSlope.NEGATIVE);
        headers.put(GangliaConstants.METRIC_UNITS, "cm");
        headers.put(GangliaConstants.METRIC_TMAX, 100);
        headers.put(GangliaConstants.METRIC_DMAX, 10);
        template.sendBodyAndHeaders(getTestUri(), -3.0f, headers);
    }

    @Test
    public void sendWithWrongTypeShouldThrow() {
        thrown.expect(ResolveEndpointFailedException.class);
        template.sendBody(getTestUri() + "&type=wrong", "");
    }
}
