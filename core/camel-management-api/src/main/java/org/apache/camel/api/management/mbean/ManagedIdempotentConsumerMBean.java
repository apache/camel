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
package org.apache.camel.api.management.mbean;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;

public interface ManagedIdempotentConsumerMBean extends ManagedProcessorMBean {

    @ManagedAttribute(description = "The language for the expression")
    String getExpressionLanguage();

    @ManagedAttribute(description = "Expression used to calculate the correlation key to use for duplicate check")
    String getExpression();

    @ManagedAttribute(description = "Whether to eagerly add the key to the idempotent repository or wait until the exchange is complete")
    Boolean isEager();

    @ManagedAttribute(description = "Whether to complete the idempotent consumer eager or when the exchange is done")
    Boolean isCompletionEager();

    @ManagedAttribute(description = "whether to skip duplicates or not")
    Boolean isSkipDuplicate();

    @ManagedAttribute(description = "whether to remove or keep the key on failure")
    Boolean isRemoveOnFailure();

    @ManagedAttribute(description = "Current count of duplicate Messages")
    long getDuplicateMessageCount();

    @ManagedOperation(description = "Reset the current count of duplicate Messages")
    void resetDuplicateMessageCount();
    
    @ManagedOperation(description = "Clear the repository containing Messages")
    void clear();

}
