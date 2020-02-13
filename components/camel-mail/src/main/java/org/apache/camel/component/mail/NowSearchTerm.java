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
package org.apache.camel.component.mail;

import java.util.Date;

import javax.mail.Message;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.DateTerm;

/**
 * A {@link javax.mail.search.SearchTerm} that is based on
 * {@link DateTerm} that compares with current date (eg now).
 * <p/>
 * This allows to compare with a dynamic derived value.
 */
public class NowSearchTerm extends ComparisonTerm {

    private static final long serialVersionUID = 1L;
    private final int comparison;
    private final boolean sentDate;
    private final long offset;

    /**
     * Constructor
     *
     * @param comparison the comparison operator
     * @param sentDate <tt>true</tt> for using sent date, <tt>false</tt> for using received date.
     * @param offset an optional offset as delta from now, can be a positive or negative value, for example
     *               to say within last 24 hours.
     * @see ComparisonTerm
     */
    public NowSearchTerm(int comparison, boolean sentDate, long offset) {
        this.comparison = comparison;
        this.sentDate = sentDate;
        this.offset = offset;
    }

    private Date getDate() {
        long now = System.currentTimeMillis();
        return new Date(now + offset);
    }

    @Override
    public boolean match(Message msg) {
        Date d;

        try {
            if (sentDate) {
                d = msg.getSentDate();
            } else {
                d = msg.getReceivedDate();
            }
        } catch (Exception e) {
            return false;
        }

        if (d == null) {
            return false;
        }

        return match(d, getDate(), comparison);
    }

    private static boolean match(Date d1, Date d2, int comparison) {
        switch (comparison) {
            case LE:
                return d1.before(d2) || d1.equals(d2);
            case LT:
                return d1.before(d2);
            case EQ:
                return d1.equals(d2);
            case NE:
                return !d1.equals(d2);
            case GT:
                return d1.after(d2);
            case GE:
                return d1.after(d2) || d1.equals(d2);
            default:
                return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        NowSearchTerm that = (NowSearchTerm) o;

        if (sentDate != that.sentDate) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return comparison;
    }

}
