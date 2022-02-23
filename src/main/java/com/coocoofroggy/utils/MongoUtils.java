package com.coocoofroggy.utils;

import com.coocoofroggy.objects.LNUser;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class MongoUtils {
    static MongoDatabase database;

    public static void connectToDatabase(String mongoUri) {
        MongoClient client = MongoClients.create(mongoUri);
        database = client.getDatabase("LuckyNumber-DB");
    }

    private static MongoCollection<Document> getUsersCollection() {
        return database.getCollection("Users");
    }

    public static UpdateResult addUserToManualSearch(String username) {
        MongoCollection<Document> usersCollection = getUsersCollection();
        return usersCollection.updateOne(
                Filters.eq("username", username),
                Updates.set("manuallySearching", true),
                new UpdateOptions().upsert(true)); // Upsert means insert if no update
    }

    public static UpdateResult removeUserFromManualSearch(String username) {
        MongoCollection<Document> usersCollection = getUsersCollection();
        return usersCollection.updateOne(
                Filters.eq("username", username),
                Updates.set("manuallySearching", false),
                new UpdateOptions().upsert(true)); // Upsert means insert if no update
    }

    public static List<LNUser> fetchManuallySearchingUsers() {
        MongoCollection<Document> usersCollection = getUsersCollection();
        List<LNUser> manuallySearchingUsers = new ArrayList<>();
        FindIterable<Document> iterable = usersCollection.find(Filters.eq("manuallySearching", true));
        for (Document document : iterable) {
            manuallySearchingUsers.add(
                    new LNUser(
                            document.getString("username"), document.getBoolean("manuallySearching")));
        }
        return manuallySearchingUsers;
    }
}
