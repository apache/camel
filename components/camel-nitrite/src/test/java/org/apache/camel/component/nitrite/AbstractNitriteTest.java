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
package org.apache.camel.component.nitrite;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public abstract class AbstractNitriteTest extends CamelTestSupport implements BeforeEachCallback {

    protected String testMethodName;

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();
        FileUtil.deleteFile(new File(tempDb()));
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        testMethodName = context.getTestMethod().map(Method::getName).orElse("unknown");
        super.beforeEach(context);
    }

    protected String tempDb() {
        return "target/" + getClass().getSimpleName() + "_" + testMethodName + ".db";
    }

    protected List<Exchange> sortByChangeTimestamp(List<Exchange> input) {
        return input.stream().sorted((e1, e2) -> {
            Long timestamp1 = e1.getMessage().getHeader(NitriteConstants.CHANGE_TIMESTAMP, Long.class);
            Long timestamp2 = e2.getMessage().getHeader(NitriteConstants.CHANGE_TIMESTAMP, Long.class);
            if (timestamp1 == null || timestamp2 == null) {
                return 0;
            }
            return Long.compare(timestamp1, timestamp2);
        }).toList();
    }

}
