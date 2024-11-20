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
package org.apache.camel.dsl.jbang.it;

import java.io.IOException;

import org.apache.camel.dsl.jbang.it.support.JBangTestSupport;
import org.junit.jupiter.api.Test;

public class TransformMessageITCase extends JBangTestSupport {

    @Test
    public void testTransformFromSrc() throws IOException {
        copyResourceInDataFolder(TestResources.SRC_MAPPING_DATA);
        copyResourceInDataFolder(TestResources.SRC_MAPPING_TEMPLATE);
        checkCommandOutputs(
                String.format("transform message --body=file:%s/data.json --source=%s/transform.yaml --pretty", mountPoint(),
                        mountPoint()),
                "Pabst Blue Ribbon");
    }

    @Test
    public void testTransformUsingComponents() throws IOException {
        copyResourceInDataFolder(TestResources.COMP_MAPPING_DATA);
        copyResourceInDataFolder(TestResources.COMP_MAPPING_TEMPLATE);
        checkCommandOutputs(String.format(
                "transform message --body=file:%s/data.xml --component=xslt --template=file:%s/transform.xml --pretty",
                mountPoint(), mountPoint()), "ABN AMRO MEZZANINE (UK) LIMITED");
    }

    @Test
    public void testTransformUsingDataFormats() throws IOException {
        copyResourceInDataFolder(TestResources.FORMATS_MAPPING_DATA);
        checkCommandOutputs(String.format("transform message --body=file:%s/data.csv --dataformat=csv", mountPoint()),
                "[[Jack Dalton,  115,  mad at Averell]");
    }
}
