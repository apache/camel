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
package org.apache.camel.component.salesforce;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.camel.component.salesforce.internal.OperationName;
import org.apache.camel.spi.UriPath;
import org.junit.Assert;
import org.junit.Test;

public class SalesforceEndpointTest {

    @Test
    public void allOperationValuesShouldBeListedInOperationNameUriPath() throws NoSuchFieldException, SecurityException {
        UriPath uriPath = SalesforceEndpoint.class.getDeclaredField("operationName").getAnnotation(UriPath.class);

        String[] operationNamesInAnnotation = uriPath.enums().split(",");
        Arrays.sort(operationNamesInAnnotation);

        String[] operationNamesInEnum = Arrays.stream(OperationName.values()).map(OperationName::value).toArray(length -> new String[length]);
        Arrays.sort(operationNamesInEnum);

        Assert.assertArrayEquals("All operation values, the String value returned from OperationName::value, must be defined in the @UriPath "
                                 + "enum parameter of the operationName field in SalesforceEndpoint, set the enums parameter to:\n"
                                 + Arrays.stream(operationNamesInEnum).collect(Collectors.joining(",")), operationNamesInEnum, operationNamesInAnnotation);
    }

}
