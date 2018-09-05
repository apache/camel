/**
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
package org.apache.camel.component.http.handler;

import javax.servlet.http.HttpServletRequest;

import org.apache.camel.util.StringHelper;

public class DrinkValidationHandler extends BasicValidationHandler {

    private final String header;

    public DrinkValidationHandler(String expectedMethod, String expectedQuery, Object expectedContent, String header) {
        super(expectedMethod, expectedQuery, expectedContent, null);
        this.header = header;
    }

    @Override
    protected String buildResponse(HttpServletRequest request) {
        String value = request.getHeader(header);
        if (value == null) {
            value = StringHelper.after(request.getQueryString(), "drink=");
        }
        return "Drinking " + value;
    }
}
