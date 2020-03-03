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

import javax.mail.Flags;
import javax.mail.Message;
import javax.mail.search.AndTerm;
import javax.mail.search.BodyTerm;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.FlagTerm;
import javax.mail.search.FromStringTerm;
import javax.mail.search.HeaderTerm;
import javax.mail.search.NotTerm;
import javax.mail.search.OrTerm;
import javax.mail.search.ReceivedDateTerm;
import javax.mail.search.RecipientStringTerm;
import javax.mail.search.SearchTerm;
import javax.mail.search.SentDateTerm;
import javax.mail.search.SubjectTerm;

/**
 * A builder to build compound {@link SearchTerm}s.
 */
public class SearchTermBuilder {

    private SearchTerm term;

    public enum Op {
        and, or, not;
    }

    public enum Comparison {
        LE, LT, EQ, NE, GT, GE;

        int asNum() {
            switch (this) {
                case LE :
                    return ComparisonTerm.LE;
                case LT :
                    return ComparisonTerm.LT;
                case EQ :
                    return ComparisonTerm.EQ;
                case NE :
                    return ComparisonTerm.NE;
                case GT :
                    return ComparisonTerm.GT;
                case GE :
                    return ComparisonTerm.GE;
                default :
                    throw new IllegalArgumentException("Unknown comparison " + this);
            }
        }
    }

    public SearchTerm build() {
        return term;
    }

    public SearchTermBuilder unseen() {
        return unseen(Op.and);
    }

    public SearchTermBuilder unseen(Op op) {
        SearchTerm st = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
        addTerm(op, st);
        return this;
    }

    public SearchTermBuilder header(String headerName, String pattern) {
        return header(Op.and, headerName, pattern);
    }

    public SearchTermBuilder header(Op op, String headerName, String pattern) {
        SearchTerm st = new HeaderTerm(headerName, pattern);
        addTerm(op, st);
        return this;
    }

    public SearchTermBuilder subject(String pattern) {
        return subject(Op.and, pattern);
    }

    public SearchTermBuilder subject(Op op, String pattern) {
        SearchTerm st = new SubjectTerm(pattern);
        addTerm(op, st);
        return this;
    }

    public SearchTermBuilder body(String pattern) {
        return body(Op.and, pattern);
    }

    public SearchTermBuilder body(Op op, String pattern) {
        SearchTerm st = new BodyTerm(pattern);
        addTerm(op, st);
        return this;
    }

    public SearchTermBuilder from(String pattern) {
        return from(Op.and, pattern);
    }

    public SearchTermBuilder from(Op op, String pattern) {
        SearchTerm st = new FromStringTerm(pattern);
        addTerm(op, st);
        return this;
    }

    public SearchTermBuilder recipient(Message.RecipientType type, String pattern) {
        return recipient(Op.and, type, pattern);
    }

    public SearchTermBuilder recipient(Op op, Message.RecipientType type, String pattern) {
        SearchTerm st = new RecipientStringTerm(type, pattern);
        addTerm(op, st);
        return this;
    }

    public SearchTermBuilder flag(Flags flags, boolean set) {
        return flag(Op.and, flags, set);
    }

    public SearchTermBuilder flag(Op op, Flags flags, boolean set) {
        SearchTerm st = new FlagTerm(flags, set);
        addTerm(op, st);
        return this;
    }

    public SearchTermBuilder sent(Comparison comparison, Date date) {
        return sent(Op.and, comparison, date);
    }

    public SearchTermBuilder sent(Op op, Comparison comparison, Date date) {
        SentDateTerm st = new SentDateTerm(comparison.asNum(), date);
        addTerm(op, st);
        return this;
    }

    public SearchTermBuilder sentNow(Comparison comparison, long offset) {
        return sentNow(Op.and, comparison, offset);
    }

    public SearchTermBuilder sentNow(Op op, Comparison comparison, long offset) {
        NowSearchTerm st = new NowSearchTerm(comparison.asNum(), true, offset);
        addTerm(op, st);
        return this;
    }

    public SearchTermBuilder received(Comparison comparison, Date date) {
        return received(Op.and, comparison, date);
    }

    public SearchTermBuilder received(Op op, Comparison comparison, Date date) {
        ReceivedDateTerm st = new ReceivedDateTerm(comparison.asNum(), date);
        addTerm(op, st);
        return this;
    }

    public SearchTermBuilder receivedNow(Comparison comparison, long offset) {
        return sentNow(Op.and, comparison, offset);
    }

    public SearchTermBuilder receivedNow(Op op, Comparison comparison, long offset) {
        NowSearchTerm st = new NowSearchTerm(comparison.asNum(), false, offset);
        addTerm(op, st);
        return this;
    }

    public SearchTermBuilder and(SearchTerm term) {
        addTerm(Op.and, term);
        return this;
    }

    public SearchTermBuilder or(SearchTerm term) {
        addTerm(Op.or, term);
        return this;
    }

    public SearchTermBuilder not(SearchTerm term) {
        addTerm(Op.not, term);
        return this;
    }

    private void addTerm(Op op, SearchTerm newTerm) {
        if (term == null) {
            term = newTerm;
        } else if (op == Op.and) {
            term = new AndTerm(term, newTerm);
        } else if (op == Op.or) {
            term = new OrTerm(term, newTerm);
        } else {
            // need to and the existing with the not
            term = new AndTerm(term, new NotTerm(newTerm));
        }
    }
}
