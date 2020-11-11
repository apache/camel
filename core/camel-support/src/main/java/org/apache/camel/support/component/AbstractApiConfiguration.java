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
package org.apache.camel.support.component;

import org.apache.camel.spi.UriParam;

public class AbstractApiConfiguration {

    @UriParam(label = "consumer", defaultValue = "true",
              description = "When consumer return an array or collection this will generate one exchange per element, and their routes will be executed once for each exchange."
                            + " Set this value to false to use a single exchange for the entire list or array.")
    private boolean splitResult;

    public boolean isSplitResult() {
        return splitResult;
    }

    public void setSplitResult(boolean splitResult) {
        this.splitResult = splitResult;
    }

}
