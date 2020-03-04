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
package org.apache.camel.component.telegram.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Object to send when answering to an inline query.
 *
 * @see <a href="https://core.telegram.org/bots/api#answerinlinequery">
 * https://core.telegram.org/bots/api#answerinlinequery</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OutgoingAnswerInlineQuery extends OutgoingMessage {

    private static final long serialVersionUID = -1928788814068921178L;

    @JsonProperty("inline_query_id")
    private String inlineQueryId;

    private List<InlineQueryResult> results;

    @JsonProperty("cache_time")
    private Integer cacheTime;

    @JsonProperty("is_personal")
    private Boolean personal;

    @JsonProperty("next_offset")
    private String nextOffset;

    @JsonProperty("switch_pm_text")
    private String switchPmText;

    @JsonProperty("switch_pm_parameter")
    private String switchPmParameter;


    public OutgoingAnswerInlineQuery(String inlineQueryId, List<InlineQueryResult> results, Integer cacheTime,
                                     Boolean personal, String nextOffset, String switchPmText,
                                     String switchPmParameter) {
        this.inlineQueryId = inlineQueryId;
        this.results = results;
        this.cacheTime = cacheTime;
        this.personal = personal;
        this.nextOffset = nextOffset;
        this.switchPmText = switchPmText;
        this.switchPmParameter = switchPmParameter;
    }

    public OutgoingAnswerInlineQuery() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String inlineQueryId;
        private List<InlineQueryResult> results;
        private Integer cacheTime;
        private Boolean personal;
        private String nextOffset;
        private String switchPmText;
        private String switchPmParameter;

        public Builder() {
        }

        public Builder inlineQueryId(String inlineQueryId) {
            this.inlineQueryId = inlineQueryId;
            return this;
        }

        public Builder results(List<InlineQueryResult> results) {
            this.results = results;
            return this;
        }

        public Builder cacheTime(Integer cacheTime) {
            this.cacheTime = cacheTime;
            return this;
        }

        public Builder personal(Boolean personal) {
            this.personal = personal;
            return this;
        }

        public Builder nextOffset(String nextOffset) {
            this.nextOffset = nextOffset;
            return this;
        }

        public Builder switchPmText(String switchPmText) {
            this.switchPmText = switchPmText;
            return this;
        }

        public Builder switchPmParameter(String switchPmParameter) {
            this.switchPmParameter = switchPmParameter;
            return this;
        }

        public OutgoingAnswerInlineQuery build() {
            OutgoingAnswerInlineQuery outgoingAnswerInlineQuery = new OutgoingAnswerInlineQuery();
            outgoingAnswerInlineQuery.setInlineQueryId(inlineQueryId);
            outgoingAnswerInlineQuery.setResults(results);
            outgoingAnswerInlineQuery.setCacheTime(cacheTime);
            outgoingAnswerInlineQuery.setPersonal(personal);
            outgoingAnswerInlineQuery.setNextOffset(nextOffset);
            outgoingAnswerInlineQuery.setSwitchPmText(switchPmText);
            outgoingAnswerInlineQuery.setSwitchPmParameter(switchPmParameter);
            return outgoingAnswerInlineQuery;
        }
    }

    public String getInlineQueryId() {
        return inlineQueryId;
    }

    public void setInlineQueryId(String inlineQueryId) {
        this.inlineQueryId = inlineQueryId;
    }

    public List<InlineQueryResult> getResults() {
        return results;
    }

    public void setResults(List<InlineQueryResult> results) {
        this.results = results;
    }

    public Integer getCacheTime() {
        return cacheTime;
    }

    public void setCacheTime(Integer cacheTime) {
        this.cacheTime = cacheTime;
    }

    public Boolean getPersonal() {
        return personal;
    }

    public void setPersonal(Boolean personal) {
        this.personal = personal;
    }

    public String getNextOffset() {
        return nextOffset;
    }

    public void setNextOffset(String nextOffset) {
        this.nextOffset = nextOffset;
    }

    public String getSwitchPmText() {
        return switchPmText;
    }

    public void setSwitchPmText(String switchPmText) {
        this.switchPmText = switchPmText;
    }

    public String getSwitchPmParameter() {
        return switchPmParameter;
    }

    public void setSwitchPmParameter(String switchPmParameter) {
        this.switchPmParameter = switchPmParameter;
    }
}
