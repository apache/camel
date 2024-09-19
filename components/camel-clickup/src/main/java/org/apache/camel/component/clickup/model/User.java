package org.apache.camel.component.clickup.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serial;
import java.io.Serializable;

public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 0L;

    @JsonProperty("id")
    private Long id; // example: 2478114,

    @JsonProperty("username")
    private String username; // example: "Nicolò Scarpa",

    @JsonProperty("email")
    private String email; // example: "nicolo.scarpa@blutec.it",

    @JsonProperty("color")
    private String color; // example: "#2ea52c",

    @JsonProperty("initials")
    private String initials; // example: "NS",

    @JsonProperty("profilePicture")
    private String profilePicture; // example: "https://attachments.clickup.com/profilePictures/2478114_RSb.jpg"

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getColor() {
        return color;
    }

    public String getInitials() {
        return initials;
    }

    public String getProfilePicture() {
        return profilePicture;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", color='" + color + '\'' +
                ", initials='" + initials + '\'' +
                ", profilePicture='" + profilePicture + '\'' +
                '}';
    }

}
