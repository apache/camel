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
package org.apache.camel.component.aws.ec2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EC2OperationsTest {

    @Test
    public void supportedOperationCount() {
        assertEquals(11, EC2Operations.values().length);
    }

    @Test
    public void valueOf() {
        assertEquals(EC2Operations.valueOf("createAndRunInstances"), EC2Operations.createAndRunInstances);
        assertEquals(EC2Operations.valueOf("startInstances"), EC2Operations.startInstances);
        assertEquals(EC2Operations.valueOf("stopInstances"), EC2Operations.stopInstances);
        assertEquals(EC2Operations.valueOf("terminateInstances"), EC2Operations.terminateInstances);
        assertEquals(EC2Operations.valueOf("describeInstances"), EC2Operations.describeInstances);
        assertEquals(EC2Operations.valueOf("describeInstancesStatus"), EC2Operations.describeInstancesStatus);
        assertEquals(EC2Operations.valueOf("rebootInstances"), EC2Operations.rebootInstances);
        assertEquals(EC2Operations.valueOf("monitorInstances"), EC2Operations.monitorInstances);
        assertEquals(EC2Operations.valueOf("unmonitorInstances"), EC2Operations.unmonitorInstances);
        assertEquals(EC2Operations.valueOf("createTags"), EC2Operations.createTags);
        assertEquals(EC2Operations.valueOf("deleteTags"), EC2Operations.deleteTags);
    }

    @Test
    public void testToString() {
        assertEquals("createAndRunInstances", EC2Operations.createAndRunInstances.toString());
        assertEquals("startInstances", EC2Operations.startInstances.toString());
        assertEquals("stopInstances", EC2Operations.stopInstances.toString());
        assertEquals("terminateInstances", EC2Operations.terminateInstances.toString());
        assertEquals("describeInstances", EC2Operations.describeInstances.toString());
        assertEquals("describeInstancesStatus", EC2Operations.describeInstancesStatus.toString());
        assertEquals("rebootInstances", EC2Operations.rebootInstances.toString());
        assertEquals("monitorInstances", EC2Operations.monitorInstances.toString());
        assertEquals("unmonitorInstances", EC2Operations.unmonitorInstances.toString());
        assertEquals("createTags", EC2Operations.createTags.toString());
        assertEquals("deleteTags", EC2Operations.deleteTags.toString());
    }
}
