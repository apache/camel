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
package org.apache.camel.component.telegram.model;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the result of a call to <i>getUpdates</i> REST service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateResult implements Serializable {

    private static final long serialVersionUID = -4560342931918215225L;

    private boolean ok;

    @JsonProperty("result")
    private List<Update> updates;

    public UpdateResult() {
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public List<Update> getUpdates() {
        return updates;
    }

    public void setUpdates(List<Update> updates) {
        this.updates = updates;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("UpdateResult{");
        sb.append("ok=").append(ok);
        sb.append(", updates=").append(updates);
        sb.append('}');
        return sb.toString();
    }
}
