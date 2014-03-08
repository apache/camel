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
package org.apache.camel.dataformat.beanio;

public class B1Record extends Record {
    String securityName;

    public B1Record() {
    }

    public B1Record(String sedol, String source, String securityName) {
        super(sedol, source);
        this.securityName = securityName;
    }

    public String getSecurityName() {
        return securityName;
    }

    public void setSecurityName(String securityName) {
        this.securityName = securityName;
    }

    @Override
    public int hashCode() {
        return securityName != null ? securityName.hashCode() : 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        } else if (obj == this) {
            return true;
        } else {
            B1Record record = (B1Record) obj;
            return super.equals(record) && this.securityName.equals(record.getSecurityName());
        }
    }

    @Override
    public String toString() {
        return "SEDOL[" + this.sedol + "], SOURCE[" + this.source + "], NAME[" + this.securityName + "]";
    }
}