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
package org.apache.camel.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/**
 * Forces a rollback by stopping routing the message
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "rollback")
@XmlAccessorType(XmlAccessType.FIELD)
public class RollbackDefinition extends NoOutputDefinition<RollbackDefinition> {
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String markRollbackOnly;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String markRollbackOnlyLast;
    @XmlAttribute
    private String message;

    public RollbackDefinition() {
    }

    public RollbackDefinition(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        if (message != null) {
            return "Rollback[" + message + "]";
        } else {
            return "Rollback";
        }
    }

    @Override
    public String getShortName() {
        return "rollback";
    }

    @Override
    public String getLabel() {
        return "rollback";
    }

    public String getMessage() {
        return message;
    }

    /**
     * Message to use in rollback exception
     */
    public void setMessage(String message) {
        this.message = message;
    }

    public String getMarkRollbackOnly() {
        return markRollbackOnly;
    }

    /**
     * Mark the transaction for rollback only (cannot be overruled to commit)
     */
    public void setMarkRollbackOnly(String markRollbackOnly) {
        this.markRollbackOnly = markRollbackOnly;
    }

    public String getMarkRollbackOnlyLast() {
        return markRollbackOnlyLast;
    }

    /**
     * Mark only last sub transaction for rollback only.
     * <p/>
     * When using sub transactions (if the transaction manager support this)
     */
    public void setMarkRollbackOnlyLast(String markRollbackOnlyLast) {
        this.markRollbackOnlyLast = markRollbackOnlyLast;
    }

}
