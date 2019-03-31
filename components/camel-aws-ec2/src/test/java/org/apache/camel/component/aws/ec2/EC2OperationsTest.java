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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EC2OperationsTest {

    @Test
    public void supportedOperationCount() {
        assertEquals(11, EC2Operations.values().length);
    }
    
    @Test
    public void valueOf() {
        assertEquals(EC2Operations.createAndRunInstances, EC2Operations.valueOf("createAndRunInstances"));
        assertEquals(EC2Operations.startInstances, EC2Operations.valueOf("startInstances"));
        assertEquals(EC2Operations.stopInstances, EC2Operations.valueOf("stopInstances"));
        assertEquals(EC2Operations.terminateInstances, EC2Operations.valueOf("terminateInstances"));
        assertEquals(EC2Operations.describeInstances, EC2Operations.valueOf("describeInstances"));
        assertEquals(EC2Operations.describeInstancesStatus, EC2Operations.valueOf("describeInstancesStatus"));
        assertEquals(EC2Operations.rebootInstances, EC2Operations.valueOf("rebootInstances"));
        assertEquals(EC2Operations.monitorInstances, EC2Operations.valueOf("monitorInstances"));
        assertEquals(EC2Operations.unmonitorInstances, EC2Operations.valueOf("unmonitorInstances"));
        assertEquals(EC2Operations.createTags, EC2Operations.valueOf("createTags"));
        assertEquals(EC2Operations.deleteTags, EC2Operations.valueOf("deleteTags"));
    }
    
    @Test
    public void testToString() {
        assertEquals(EC2Operations.createAndRunInstances.toString(), "createAndRunInstances");
        assertEquals(EC2Operations.startInstances.toString(), "startInstances");
        assertEquals(EC2Operations.stopInstances.toString(), "stopInstances");
        assertEquals(EC2Operations.terminateInstances.toString(), "terminateInstances");
        assertEquals(EC2Operations.describeInstances.toString(), "describeInstances");
        assertEquals(EC2Operations.describeInstancesStatus.toString(), "describeInstancesStatus");
        assertEquals(EC2Operations.rebootInstances.toString(), "rebootInstances");
        assertEquals(EC2Operations.monitorInstances.toString(), "monitorInstances");
        assertEquals(EC2Operations.unmonitorInstances.toString(), "unmonitorInstances");
        assertEquals(EC2Operations.createTags.toString(), "createTags");
        assertEquals(EC2Operations.deleteTags.toString(), "deleteTags");
    }
}
