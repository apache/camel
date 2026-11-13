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
package org.apache.camel.maven.packaging.endpoint;

import org.apache.camel.spi.ApiMethod;
import org.apache.camel.spi.ApiParam;
import org.apache.camel.spi.ApiParams;
import org.apache.camel.spi.UriParam;

/**
 * API method parameters of type boolean and java.lang.Boolean, as the API component plugin generates them.
 */
@ApiParams(apiName = "test", apiMethods = @ApiMethod(methodName = "m"))
public class SomeApiWithBooleanOptions {

    @UriParam(description = "A java.lang.Boolean parameter")
    @ApiParam(apiMethods = @ApiMethod(methodName = "m", description = "A java.lang.Boolean parameter"))
    private Boolean wrapperParameter;
    @UriParam(description = "A boolean parameter")
    @ApiParam(apiMethods = @ApiMethod(methodName = "m", description = "A boolean parameter"))
    private boolean primitiveParameter;
}
