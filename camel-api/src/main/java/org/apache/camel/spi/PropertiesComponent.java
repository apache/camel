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
package org.apache.camel.spi;

import org.apache.camel.Component;

public interface PropertiesComponent extends Component {

    /**
     * The default prefix token.
     */
    String DEFAULT_PREFIX_TOKEN = "{{";

    /**
     * The default suffix token.
     */
    String DEFAULT_SUFFIX_TOKEN = "}}";

    /**
     * Has the component been created as a default by {@link org.apache.camel.CamelContext} during starting up Camel.
     */
    String DEFAULT_CREATED = "PropertiesComponentDefaultCreated";

    String getPrefixToken();

    String getSuffixToken();

    String parseUri(String uri) throws Exception;

    String parseUri(String uri, String... uris) throws Exception;

    /**
     * A list of locations to load properties. You can use comma to separate multiple locations.
     * This option will override any default locations and only use the locations from this option.
     */
    void setLocation(String location);

}
