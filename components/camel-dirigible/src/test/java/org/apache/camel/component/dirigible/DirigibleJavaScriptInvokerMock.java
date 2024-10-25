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
package org.apache.camel.component.dirigible;

import org.apache.camel.Message;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A test implementation of DirigibleJavaScriptInvoker which:<br>
 * - asserts that the passed JavaScript path <br>
 * - asserts that the passed body is a string <br>
 * - converts the passed body to uppercase
 */
public class DirigibleJavaScriptInvokerMock implements DirigibleJavaScriptInvoker {

    private static final String EXPECTED_JAVASCRIPT_PATH = "dirigible-java-script-component/handler.mjs";

    private String passedJavaScriptPath;

    public String getPassedJavaScriptPath() {
        return passedJavaScriptPath;
    }

    @Override
    public void invoke(Message camelMessage, String javaScriptPath) {
        assertEquals(EXPECTED_JAVASCRIPT_PATH, javaScriptPath, "Unexpected javascript path has been passed");

        Object body = camelMessage.getBody();
        assertThat(body, instanceOf(String.class));

        if (body instanceof String strBody) {
            this.passedJavaScriptPath = strBody;

            camelMessage.setBody(strBody.toUpperCase());
        }
    }
}
