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
package org.apache.camel.component.xslt;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

public final class XsltConstants {

    @Metadata(description = "A URI for the template resource to load and use instead of the endpoint configured.",
              javaType = "String")
    public static final String XSLT_RESOURCE_URI = "CamelXsltResourceUri";
    @Metadata(description = "The template to use instead of the endpoint configured. ", javaType = "String")
    public static final String XSLT_STYLESHEET = "CamelXsltStylesheet";
    @Metadata(description = "The XSLT file name", javaType = "String")
    public static final String XSLT_FILE_NAME = Exchange.XSLT_FILE_NAME;

    private XsltConstants() {
    }

}
