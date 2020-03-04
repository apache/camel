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
package org.apache.camel.component.aws2.ec2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EC2OperationsTest {

    @Test
    public void supportedOperationCount() {
        assertEquals(11, AWS2EC2Operations.values().length);
    }

    @Test
    public void valueOf() {
        assertEquals(AWS2EC2Operations.createAndRunInstances, AWS2EC2Operations.valueOf("createAndRunInstances"));
        assertEquals(AWS2EC2Operations.startInstances, AWS2EC2Operations.valueOf("startInstances"));
        assertEquals(AWS2EC2Operations.stopInstances, AWS2EC2Operations.valueOf("stopInstances"));
        assertEquals(AWS2EC2Operations.terminateInstances, AWS2EC2Operations.valueOf("terminateInstances"));
        assertEquals(AWS2EC2Operations.describeInstances, AWS2EC2Operations.valueOf("describeInstances"));
        assertEquals(AWS2EC2Operations.describeInstancesStatus, AWS2EC2Operations.valueOf("describeInstancesStatus"));
        assertEquals(AWS2EC2Operations.rebootInstances, AWS2EC2Operations.valueOf("rebootInstances"));
        assertEquals(AWS2EC2Operations.monitorInstances, AWS2EC2Operations.valueOf("monitorInstances"));
        assertEquals(AWS2EC2Operations.unmonitorInstances, AWS2EC2Operations.valueOf("unmonitorInstances"));
        assertEquals(AWS2EC2Operations.createTags, AWS2EC2Operations.valueOf("createTags"));
        assertEquals(AWS2EC2Operations.deleteTags, AWS2EC2Operations.valueOf("deleteTags"));
    }

    @Test
    public void testToString() {
        assertEquals(AWS2EC2Operations.createAndRunInstances.toString(), "createAndRunInstances");
        assertEquals(AWS2EC2Operations.startInstances.toString(), "startInstances");
        assertEquals(AWS2EC2Operations.stopInstances.toString(), "stopInstances");
        assertEquals(AWS2EC2Operations.terminateInstances.toString(), "terminateInstances");
        assertEquals(AWS2EC2Operations.describeInstances.toString(), "describeInstances");
        assertEquals(AWS2EC2Operations.describeInstancesStatus.toString(), "describeInstancesStatus");
        assertEquals(AWS2EC2Operations.rebootInstances.toString(), "rebootInstances");
        assertEquals(AWS2EC2Operations.monitorInstances.toString(), "monitorInstances");
        assertEquals(AWS2EC2Operations.unmonitorInstances.toString(), "unmonitorInstances");
        assertEquals(AWS2EC2Operations.createTags.toString(), "createTags");
        assertEquals(AWS2EC2Operations.deleteTags.toString(), "deleteTags");
    }
}
