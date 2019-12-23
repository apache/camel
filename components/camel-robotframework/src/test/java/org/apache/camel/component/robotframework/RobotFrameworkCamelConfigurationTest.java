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
package org.apache.camel.component.robotframework;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class RobotFrameworkCamelConfigurationTest extends CamelTestSupport {

    @Test
    public void testNameParamInRobotFrameworkCamelConfigurations() throws Exception {
        RobotFrameworkEndpoint robotFrameworkEndpoint = createEndpointWithOption("name=testName");
        assertEquals("testName", robotFrameworkEndpoint.getConfiguration().getName());
    }

    @Test
    public void testDryRunParamInRobotFrameworkCamelConfigurations() throws Exception {
        RobotFrameworkEndpoint robotFrameworkEndpoint = createEndpointWithOption("");
        assertEquals(false, robotFrameworkEndpoint.getConfiguration().isDryrun());
        robotFrameworkEndpoint = createEndpointWithOption("dryrun=true");
        assertEquals(true, robotFrameworkEndpoint.getConfiguration().isDryrun());
    }

    @Test
    public void testOutputParamInRobotFrameworkCamelConfigurations() throws Exception {
        RobotFrameworkEndpoint robotFrameworkEndpoint = createEndpointWithOption("output=customOutput.log");
        assertEquals("customOutput.log", robotFrameworkEndpoint.getConfiguration().getOutput().getName());
    }

    private RobotFrameworkEndpoint createEndpointWithOption(String option) throws Exception {
        RobotFrameworkComponent robotFrameworkComponent = context.getComponent("robotframework", RobotFrameworkComponent.class);
        RobotFrameworkEndpoint robotFrameworkEndpoint = (RobotFrameworkEndpoint)robotFrameworkComponent
            .createEndpoint("robotframework:src/test/resources/org/apache/camel/component/robotframework/send_no_camel_exchnage_only_camel_configs.robot?" + option);
        return robotFrameworkEndpoint;
    }

}
