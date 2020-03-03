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
package org.apache.camel.component.telegram.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents one row of the high scores table for a game.
 *
 * @see <a href="https://core.telegram.org/bots/api#gamehighscore">https://core.telegram.org/bots/api#gamehighscore</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GameHighScore {

    private Integer position;

    private User user;

    private Integer score;

    public GameHighScore() {
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("GameHighScore{");
        sb.append("position=").append(position);
        sb.append(", user=").append(user);
        sb.append(", score=").append(score);
        sb.append('}');
        return sb.toString();
    }
}
