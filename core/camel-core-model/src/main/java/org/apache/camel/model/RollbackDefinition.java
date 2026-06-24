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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DslArg;

/**
 * Forces a rollback by stopping routing the message
 */
@Metadata(label = "eip,errorhandling,routing",
          description = "Forces a rollback of the current transaction and stops routing the message."
                        + " Can set a custom message on the exception.")
@XmlRootElement(name = "rollback")
@XmlAccessorType(XmlAccessType.FIELD)
public class RollbackDefinition extends NoOutputDefinition<RollbackDefinition> {

    @XmlAttribute
    @DslArg
    @Metadata(description = "The message to set on the exception when rolling back.")
    private String message;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean",
              description = "If enabled then only the current transaction is marked for rollback."
                            + " No exception is thrown and the route continues to execute.")
    private String markRollbackOnly;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean",
              description = "If enabled then only the last sub-transaction (from the last transacted EIP) is marked for rollback."
                            + " This allows partial rollbacks in nested transaction scenarios.")
    private String markRollbackOnlyLast;

    public RollbackDefinition() {
    }

    protected RollbackDefinition(RollbackDefinition source) {
        super(source);
        this.message = source.message;
        this.markRollbackOnly = source.markRollbackOnly;
        this.markRollbackOnlyLast = source.markRollbackOnlyLast;
    }

    public RollbackDefinition(String message) {
        this.message = message;
    }

    @Override
    public RollbackDefinition copyDefinition() {
        return new RollbackDefinition(this);
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

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMarkRollbackOnly() {
        return markRollbackOnly;
    }

    public void setMarkRollbackOnly(String markRollbackOnly) {
        this.markRollbackOnly = markRollbackOnly;
    }

    public String getMarkRollbackOnlyLast() {
        return markRollbackOnlyLast;
    }

    public void setMarkRollbackOnlyLast(String markRollbackOnlyLast) {
        this.markRollbackOnlyLast = markRollbackOnlyLast;
    }

}
