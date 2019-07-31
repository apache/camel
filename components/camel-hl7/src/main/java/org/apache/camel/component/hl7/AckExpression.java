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
package org.apache.camel.component.hl7;

import ca.uhn.hl7v2.AcknowledgmentCode;
import ca.uhn.hl7v2.ErrorCode;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.ExpressionAdapter;

public class AckExpression extends ExpressionAdapter {

    private AcknowledgmentCode acknowledgementCode;
    private String errorMessage;
    private ErrorCode errorCode;

    public AckExpression() {
        this((AcknowledgmentCode)null, null, ErrorCode.APPLICATION_INTERNAL_ERROR);
    }

    public AckExpression(AcknowledgmentCode acknowledgementCode) {
        this(acknowledgementCode, null, ErrorCode.APPLICATION_INTERNAL_ERROR);
    }

    public AckExpression(AcknowledgmentCode acknowledgementCode, String errorMessage, ErrorCode errorCode) {
        this.acknowledgementCode = acknowledgementCode;
        this.errorMessage = errorMessage;
        this.errorCode = errorCode;
    }

    @Override
    public Object evaluate(Exchange exchange) {
        Throwable t = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
        Message msg = exchange.getIn().getBody(Message.class);
        try {
            HL7Exception hl7e = generateHL7Exception(t);
            AcknowledgmentCode code = acknowledgementCode;
            if (t != null && code == null) {
                code = AcknowledgmentCode.AE;
            }
            return msg.generateACK(code == null ? AcknowledgmentCode.AA : code, hl7e);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    private HL7Exception generateHL7Exception(Throwable t) {
        HL7Exception hl7Exception = null;
        if (t == null) {
            if (acknowledgementCode != null && !isSuccess(acknowledgementCode)) {
                hl7Exception = new HL7Exception(errorMessage, errorCode);
            }
        } else {
            if (t instanceof HL7Exception) {
                hl7Exception = (HL7Exception)t;
            } else {
                hl7Exception = new HL7Exception(errorMessage != null ? errorMessage : t.getMessage(),
                                                errorCode, t);
            }
        }
        return hl7Exception;
    }

    private boolean isSuccess(AcknowledgmentCode code) {
        return code.name().endsWith("A");
    }

}
