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
package org.apache.camel;

import java.net.URL;

/**
 * Represents a failure to open a Properties file at a given URL
 */
public class LoadPropertiesException extends CamelException {

    private final URL url;

    public LoadPropertiesException(URL url, Exception cause) {
        super("Failed to load URL: " + url + ". Reason: " + cause,  cause);
        this.url = url;
    }

    public URL getUrl() {
        return url;
    }
}
