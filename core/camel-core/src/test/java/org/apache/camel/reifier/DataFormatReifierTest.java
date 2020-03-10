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
package org.apache.camel.reifier;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.dataformat.CustomDataFormat;
import org.apache.camel.reifier.dataformat.CustomDataFormatReifier;
import org.apache.camel.reifier.dataformat.DataFormatReifier;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import static junit.framework.TestCase.fail;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class DataFormatReifierTest {

    @Test
    public void testHandleCustomDataFormat() {
        DefaultCamelContext context = new DefaultCamelContext();
        try {
            DataFormatReifier.reifier(context, new MyDataFormat());
            fail("Should throw IllegalStateException instead");
        } catch (IllegalStateException e) {
        }

        DataFormatReifier.registerReifier(MyDataFormat.class, CustomDataFormatReifier::new);
        DataFormatReifier.reifier(context, new MyDataFormat());
    }

    public static class MyDataFormat extends CustomDataFormat {
    }
}
