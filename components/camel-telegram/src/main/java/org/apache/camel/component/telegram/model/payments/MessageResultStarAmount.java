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

package org.apache.camel.component.telegram.model.payments;

import java.io.Serial;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import org.apache.camel.component.telegram.model.MessageResult;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageResultStarAmount extends MessageResult {

    @Serial
    private static final long serialVersionUID = -1031136214896586553L;

    @JsonProperty("result")
    private StarAmount starAmount;

    public MessageResultStarAmount() {
    }

    public StarAmount getStarAmount() {
        return starAmount;
    }

    public void setStarAmount(StarAmount starAmount) {
        this.starAmount = starAmount;
    }

    @JsonSetter("result")
    public void setResult(StarAmount result) {
        this.starAmount = result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("MessageResultStarAmount{");
        sb.append("starAmount=").append(starAmount);
        sb.append('}');
        return sb.toString();
    }
}
