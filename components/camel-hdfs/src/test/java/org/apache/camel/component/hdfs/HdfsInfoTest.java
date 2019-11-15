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
package org.apache.camel.component.hdfs;

import java.io.IOException;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class HdfsInfoTest {

    private HdfsInfo underTest;

    @Test
    public void createHdfsInfo() throws IOException {
        // given
        String hdfsPath = "hdfs://localhost/target/test/multiple-consumers";
        HdfsConfiguration endpointConfig = mock(HdfsConfiguration.class);

        // when
        underTest = new HdfsInfoFactory(endpointConfig).newHdfsInfoWithoutAuth(hdfsPath);

        // then
        assertThat(underTest, notNullValue());
        assertThat(underTest.getConfiguration(), notNullValue());
        assertThat(underTest.getFileSystem(), notNullValue());
        assertThat(underTest.getPath(), notNullValue());
    }
}
