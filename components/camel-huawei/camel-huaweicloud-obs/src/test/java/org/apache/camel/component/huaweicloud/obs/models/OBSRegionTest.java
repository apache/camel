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
package org.apache.camel.component.huaweicloud.obs.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OBSRegionTest {
    @Test
    public void testRegions() {
        assertEquals("obs.af-south-1.myhuaweicloud.com", OBSRegion.valueOf("af-south-1"));
        assertEquals("obs.ap-southeast-2.myhuaweicloud.com", OBSRegion.valueOf("ap-southeast-2"));
        assertEquals("obs.ap-southeast-3.myhuaweicloud.com", OBSRegion.valueOf("ap-southeast-3"));
        assertEquals("obs.cn-east-3.myhuaweicloud.com", OBSRegion.valueOf("cn-east-3"));
        assertEquals("obs.cn-east-2.myhuaweicloud.com", OBSRegion.valueOf("cn-east-2"));
        assertEquals("obs.cn-north-1.myhuaweicloud.com", OBSRegion.valueOf("cn-north-1"));
        assertEquals("obs.cn-south-1.myhuaweicloud.com", OBSRegion.valueOf("cn-south-1"));
        assertEquals("obs.ap-southeast-1.myhuaweicloud.com", OBSRegion.valueOf("ap-southeast-1"));
        assertEquals("obs.sa-argentina-1.myhuaweicloud.com", OBSRegion.valueOf("sa-argentina-1"));
        assertEquals("obs.sa-peru-1.myhuaweicloud.com", OBSRegion.valueOf("sa-peru-1"));
        assertEquals("obs.na-mexico-1.myhuaweicloud.com", OBSRegion.valueOf("na-mexico-1"));
        assertEquals("obs.la-south-2.myhuaweicloud.com", OBSRegion.valueOf("la-south-2"));
        assertEquals("obs.sa-chile-1.myhuaweicloud.com", OBSRegion.valueOf("sa-chile-1"));
        assertEquals("obs.sa-brazil-1.myhuaweicloud.com", OBSRegion.valueOf("sa-brazil-1"));
    }
}
