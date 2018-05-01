package org.apache.camel.component.telegram.model;

public class InlineKeyboardButton {

    private String text;

    public InlineKeyboardButton() {
        
    }
    
    public InlineKeyboardButton(String text) {

        this.text = text;
    }

    public String getText() {
        
        return text;
    }

    public void setText(String text) {
        
        this.text = text;
    }
    
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("InlineKeyboardButton{");
        sb.append("text='").append(text);
        sb.append('}');
        return sb.toString();
    }        

    public static Builder builder() {

        return new Builder();
    }

    public static class Builder {

        private String text;

        public Builder text(String text) {

            this.text = text;
            return this;
        }

        public InlineKeyboardButton build() {
            return new InlineKeyboardButton(text);
        }
    }
}
