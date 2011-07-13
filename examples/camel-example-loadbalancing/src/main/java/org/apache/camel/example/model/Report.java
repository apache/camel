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
package org.apache.camel.example.model;

import java.io.Serializable;

public class Report implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private String title;
    private String content;
    private String reply;

    /**
    * @return the id
    */
    public Integer getId() {
        return id;
    }

    /**
    * @param id the id to set
    */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
    * @return the title
    */
    public String getTitle() {
        return title;
    }

    /**
    * @param title the title to set
    */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
    * @return the content
    */
    public String getContent() {
        return content;
    }

    /**
    * @param content the content to set
    */
    public void setContent(String content) {
        this.content = content;
    }

    /**
    * @return the reply
    */
    public String getReply() {
        return reply;
    }

    /**
    * @param reply the reply to set
    */
    public void setReply(String reply) {
        this.reply = reply;
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(">> ***********************************************" + "\n");
        result.append(">> Report id : " + this.id + "\n");
        result.append(">> Report title : " + this.title + "\n");
        result.append(">> Report content : " + this.content + "\n");
        result.append(">> Report reply : " + this.reply + "\n");
        result.append(">> ***********************************************" + "\n");
        return result.toString();
    }
}
