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

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * Endpoint options of type boolean and java.lang.Boolean, with and without a declared default value.
 */
@UriEndpoint(scheme = "some", syntax = "some:wrapperPath", title = "some")
public class SomeEndpointWithBooleanOptions {

    @UriPath(description = "A java.lang.Boolean path option without default value")
    private Boolean wrapperPath;
    @UriParam(description = "A java.lang.Boolean option without default value")
    private Boolean wrapperWithoutDefault;
    @UriParam(description = "A java.lang.Boolean option with the default value false", defaultValue = "false")
    private Boolean wrapperFalse;
    @UriParam(description = "A java.lang.Boolean option with the default value true", defaultValue = "true")
    private Boolean wrapperTrue;
    @UriParam(description = "A java.lang.Boolean option with the default value true in @Metadata")
    @Metadata(defaultValue = "true")
    private Boolean wrapperMetadataTrue;
    @UriParam(description = "A boolean option without default value")
    private boolean primitiveWithoutDefault;
}
