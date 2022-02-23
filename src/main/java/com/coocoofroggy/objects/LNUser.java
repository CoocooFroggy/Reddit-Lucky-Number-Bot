package com.coocoofroggy.objects;

import org.bson.Document;

public class LNUser {
    private final String username;
    private final boolean manuallySearching;

    public LNUser(String username, boolean manuallySearching) {
        this.username = username;
        this.manuallySearching = manuallySearching;
    }

    public Document toDocument() {
        return new Document()
                .append("username", username)
                .append("manuallySearching", manuallySearching);
    }

    public String getUsername() {
        return username;
    }
}
