package org.apache.camel.component.telegram.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReplyKeyboardMarkup implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("one_time_keyboard")
    private Boolean oneTimeKeyboard;
    
    @JsonProperty("remove_keyboard")
    private Boolean removeKeyboard;
    
    private List<List<InlineKeyboardButton>> keyboard;
    
    public ReplyKeyboardMarkup() {
        
    }
    
    public ReplyKeyboardMarkup(Boolean oneTimeKeyboard, Boolean removeKeyboard, List<List<InlineKeyboardButton>> keyboard) {
        this.oneTimeKeyboard = oneTimeKeyboard;
        this.removeKeyboard = removeKeyboard;
        this.keyboard = keyboard;
    }
    
    public Boolean getOneTimeKeyboard() {
        return oneTimeKeyboard;
    }

    public void setOneTimeKeyboard(Boolean oneTimeKeyboard) {
        this.oneTimeKeyboard = oneTimeKeyboard;
    }

    public Boolean getRemoveKeyboard() {
        return removeKeyboard;
    }

    public void setRemoveKeyboard(Boolean removeKeyboard) {
        this.removeKeyboard = removeKeyboard;
    }

    public List<List<InlineKeyboardButton>> getKeyboard() {
        return keyboard;
    }

    public void setKeyboard(List<List<InlineKeyboardButton>> keyboard) {
        this.keyboard = keyboard;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ReplyKeyboardMarkup{");
        sb.append("oneTimeKeyboard='").append(oneTimeKeyboard).append('\'');
        sb.append(", keyboard='").append(keyboard);
        sb.append('}');
        return sb.toString();
    }    

    public static Builder builder() {

        return new Builder();
    }

    public static class Builder {

        private Boolean oneTimeKeyboard;        
        private Boolean removeKeyboard;
        private List<List<InlineKeyboardButton>> keyboard;

        public Builder oneTimeKeyboard(Boolean oneTimeKeyboard) {

            this.oneTimeKeyboard = oneTimeKeyboard;
            return this;
        }
        
        public Builder removeKeyboard(Boolean removeKeyboard) {
            
            this.removeKeyboard = removeKeyboard;
            return this;
        }

        public ReplyKeyboardMarkup build() {
            
            return new ReplyKeyboardMarkup(oneTimeKeyboard, removeKeyboard, keyboard);
        }

        public KeyboardBuilder keyboard() {
            
            return new KeyboardBuilder(this);
        }
        
        public static class KeyboardBuilder {
            
            private Builder builder;
            private List<List<InlineKeyboardButton>> keyboard;
            
            public KeyboardBuilder(Builder builder) {
                
                this.builder = builder;
                this.keyboard = new ArrayList<>();
            }

            public KeyboardBuilder addRow(List<InlineKeyboardButton> InlineKeyboardButtons) {

                keyboard.add(InlineKeyboardButtons);
                return this;
            }
            
            public KeyboardBuilder addOneRowByEachButton(List<InlineKeyboardButton> InlineKeyboardButtons) {
                
                for (Iterator<InlineKeyboardButton> iterator = InlineKeyboardButtons.iterator(); iterator.hasNext();) {
                    
                    keyboard.add(Arrays.asList(iterator.next()));
                }

                return this;
            }

            public Builder close() {

                builder.keyboard = keyboard;
                return builder;
            }
        }
    }
}