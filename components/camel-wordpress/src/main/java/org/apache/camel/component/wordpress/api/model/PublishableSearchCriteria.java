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
package org.apache.camel.component.wordpress.api.model;

import java.util.Date;
import java.util.List;

public abstract class PublishableSearchCriteria extends SearchCriteria {

    private static final long serialVersionUID = 9178680514805178843L;

    private Date after;
    private Date before;
    private List<Integer> author;
    private List<Integer> authorExclude;
    private List<Integer> offset;
    private List<String> slug;
    private PublishableStatus status;
    private Context context;

    public Date getAfter() {
        return after;
    }

    public void setAfter(Date after) {
        this.after = after;
    }

    public Date getBefore() {
        return before;
    }

    public void setBefore(Date before) {
        this.before = before;
    }

    public List<Integer> getAuthor() {
        return author;
    }

    public void setAuthor(List<Integer> author) {
        this.author = author;
    }

    public List<Integer> getAuthorExclude() {
        return authorExclude;
    }

    public void setAuthorExclude(List<Integer> authorExclude) {
        this.authorExclude = authorExclude;
    }

    public List<Integer> getOffset() {
        return offset;
    }

    public void setOffset(List<Integer> offset) {
        this.offset = offset;
    }

    public List<String> getSlug() {
        return slug;
    }

    public void setSlug(List<String> slug) {
        this.slug = slug;
    }

    public PublishableStatus getStatus() {
        return status;
    }

    public void setStatus(PublishableStatus status) {
        this.status = status;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

}
