package com.coocoofroggy;

import com.coocoofroggy.objects.LNUser;
import com.coocoofroggy.utils.MongoUtils;
import com.mongodb.client.result.UpdateResult;
import net.dean.jraw.ApiException;
import net.dean.jraw.RedditClient;
import net.dean.jraw.http.OkHttpNetworkAdapter;
import net.dean.jraw.http.UserAgent;
import net.dean.jraw.models.*;
import net.dean.jraw.oauth.Credentials;
import net.dean.jraw.oauth.OAuthHelper;
import net.dean.jraw.pagination.BarebonesPaginator;
import net.dean.jraw.pagination.DefaultPaginator;
import net.dean.jraw.pagination.Paginator;
import net.dean.jraw.references.CommentReference;
import net.dean.jraw.references.InboxReference;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) {
        String username = System.getenv("LUCKYNUM_USERNAME");
        String password = System.getenv("LUCKYNUM_PASSWORD");
        String clientId = System.getenv("LUCKYNUM_CLIENTID");
        String clientSecret = System.getenv("LUCKYNUM_CLIENTSECRET");

        // Assuming we have a 'script' reddit app
        Credentials oauthCreds = Credentials.script(username, password, clientId, clientSecret);

        // Create a unique User-Agent for our bot
        UserAgent userAgent = new UserAgent("bot", "com.coocoofroggy.luckynumberbot", "1.0.2", "LuckyNumberBot");

        // Authenticate our client
        RedditClient reddit = OAuthHelper.automatic(new OkHttpNetworkAdapter(userAgent), oauthCreds);
//        reddit.setLogHttp(false);
        InboxReference inbox = reddit.me().inbox();

        MongoUtils.connectToDatabase(System.getenv("MONGO_URI"));

        // r/all always has a thread running
        new Thread(() -> {
            while (true) {
                allCommentLoop(reddit);
            }
        }).start();

        // Inbox always has a thread running
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                inboxLoop(reddit, inbox);
            }
        }, 0, TimeUnit.SECONDS.toMillis(5));

        // Users take turns to share this thread
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                List<LNUser> manuallySearchingUsers = MongoUtils.fetchManuallySearchingUsers();
                for (LNUser user : manuallySearchingUsers) {
                    userCommentLoop(reddit, user.getUsername());
                }
            }
        }, 0, TimeUnit.MINUTES.toMillis(1));
        // There's always at least one minute of break for MongoDB.
        // This task won't overlap, even if it lasts more than 1 minute.
    }

    private static void inboxLoop(RedditClient reddit, InboxReference inbox) {
        BarebonesPaginator<Message> inboxIterate = inbox.iterate("unread")
                .limit(Paginator.RECOMMENDED_MAX_LIMIT)
                .build();
        // Loop through every page of unread messages
        for (Listing<Message> messages : inboxIterate) {
            // Every message on the page
            for (Message message : messages) {
                final String body = message.getBody();
                if (message.isComment()) {
                    countComment(reddit, (Comment) reddit.lookup(message.getFullName()).getChildren().get(0), body);
                }
                if (body.contains("!add")) {
                    UpdateResult result = MongoUtils.addUserToManualSearch(message.getAuthor());
                    if (result.wasAcknowledged()) {
                        if (result.getModifiedCount() > 0) {
                            reply(message, "All your new messages will now be scanned for Lucky Numbers!", inbox);
                        } else {
                            reply(message, "Your new messages are already being scanned for Lucky Numbers!", inbox);
                        }
                    } else {
                        reply(message, "Something went wrong. Please try again!", inbox);
                    }
                } else if (body.contains("!remove")) {
                    UpdateResult result = MongoUtils.removeUserFromManualSearch(message.getAuthor());
                    if (result.wasAcknowledged()) {
                        if (result.getModifiedCount() > 0) {
                            reply(message, "Your new messages will no longer be scanned for Lucky Numbers.", inbox);
                        } else {
                            reply(message, "Your new messages are not being scanned for Lucky Numbers. Use `!add` to opt in.", inbox);
                        }
                    } else {
                        reply(message, "Something went wrong. Please try again!", inbox);
                    }
                }
                inbox.markRead(true, message.getFullName());
            }
        }
    }

    public static void allCommentLoop(RedditClient reddit) {
        // Paginator for r/all comments
        BarebonesPaginator<Comment> allComments = reddit.subreddit("all").comments()
                .limit(Paginator.RECOMMENDED_MAX_LIMIT)
                .build();
        Listing<Comment> commentListing = allComments.next();
        for (Comment comment : commentListing) {
            String content = comment.getBody();
            countComment(reddit, comment, content);
        }
    }

    public static void userCommentLoop(RedditClient reddit, String username) {
        DefaultPaginator<PublicContribution<?>> userComments = reddit.user(username).history("comments")
                .sorting(UserHistorySort.NEW)
                .limit(Paginator.RECOMMENDED_MAX_LIMIT)
                .build();
        Listing<PublicContribution<?>> commentListing = userComments.next();
        for (PublicContribution<?> contribution : commentListing) {
            Comment comment = (Comment) contribution;
            String content = comment.getBody();
            countComment(reddit, comment, content);
        }
    }

    private static void countComment(RedditClient reddit, Comment comment, String content) {
        // Ignore comments with brackets for links
        if (content.contains("[") | content.contains("["))
            return;

        Pattern numberPattern = Pattern.compile("\\d*\\.?\\d+");
        Matcher numberMatcher = numberPattern.matcher(content);

        int matches = 0;
        ArrayList<Float> numbers = new ArrayList<>();
        double total = 0;

        while (numberMatcher.find()) {
            float number = Float.parseFloat(numberMatcher.group(0));
            // Don't count 0 as a number
            if (number == 0)
                continue;
            total += number;
            numbers.add(number);
            matches++;
        }

        // Skip saved comments
        if (comment.isSaved())
            return;

        // If more than 2 numbers in their comment
        if (matches > 2) {
            if (total == 69 | total == 420) {
                CommentReference commentReference = reddit.comment(comment.getId());

                StringBuilder stringBuilder = new StringBuilder();
                NumberFormat nf = new DecimalFormat("##.###");
                for (int i = 0; i < numbers.size(); i++) {
                    float number = numbers.get(i);
                    stringBuilder
                            // Code block
                            .append("    ");
                    if (i != 0) {
                        // Addition symbol
                        stringBuilder.append("+ ");
                    } else {
                        stringBuilder.append("  ");
                    }
                    // Number prettified
                    stringBuilder.append(nf.format(number))
                            // New line
                            .append("\n");
                }
                try {
                    String replyUrl = commentReference.reply(
                            "All the numbers in your comment added up to " + nf.format(total) + ". Congrats!\n\n" +
                                    stringBuilder +
                                    "    = " + nf.format(total)).getUrl();
                    System.out.println("New comment: " + replyUrl);
                } catch (ApiException e) {
                    e.printStackTrace();
                    commentReference.save();
                    return;
                }
                commentReference.save();

                // ~~To not get shadowbanned, let's sleep a bit~~
                //
                        /*
                        try {
                            TimeUnit.MINUTES.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        */
            }
        }
    }

    private static void reply(Message message, String replyBody, InboxReference inbox) {
        if (message.isComment()) {
            if (message.getAuthor() == null) return;
            inbox.compose(message.getAuthor(), "Lucky Message", replyBody);
        } else {
            inbox.replyTo(message.getFullName(), replyBody);
        }
        System.out.println("New reply to DM");
    }
}
