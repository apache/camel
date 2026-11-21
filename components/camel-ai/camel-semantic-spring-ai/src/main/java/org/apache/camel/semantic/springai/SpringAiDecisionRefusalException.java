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
package org.apache.camel.semantic.springai;

import org.apache.camel.RuntimeCamelException;

/**
 * The decision model refused to answer an evaluation. The evaluation fails rather than becoming a decision, so that a
 * guardrail never lets a message through for lack of an answer. When a batch has a refused evaluation, none of its
 * evaluations returns a result.
 */
public class SpringAiDecisionRefusalException extends RuntimeCamelException {

    private final String evaluationName;
    private final String reason;

    public SpringAiDecisionRefusalException(String evaluationName, String reason) {
        super("The decision model refused to answer the evaluation '" + evaluationName + "'"
              + (reason != null ? ": " + reason : ""));
        this.evaluationName = evaluationName;
        this.reason = reason;
    }

    /**
     * The name of the evaluation that was refused.
     */
    public String getEvaluationName() {
        return evaluationName;
    }

    /**
     * The reason the provider gave, or null when it gave none.
     */
    public String getReason() {
        return reason;
    }
}
