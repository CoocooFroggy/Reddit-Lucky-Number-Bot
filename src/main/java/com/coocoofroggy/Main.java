package com.coocoofroggy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.coocoofroggy.objects.LNUser;
import com.coocoofroggy.utils.LuckyConfig;
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
import org.apache.commons.text.StringSubstitutor;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    static final String USERNAME = System.getenv("LUCKYNUM_USERNAME");
    static RedditClient reddit;
    static final Logger logger = ((Logger) LoggerFactory.getLogger(Main.class));
    private static final boolean debugMode = false;

    public static void main(String[] args) {
        final String PASSWORD = System.getenv("LUCKYNUM_PASSWORD");
        final String CLIENT_SECRET = System.getenv("LUCKYNUM_CLIENTSECRET");
        final String CLIENT_ID = System.getenv("LUCKYNUM_CLIENTID");
        
        // We have a 'script' reddit app
        Credentials oAuthCredentials = Credentials.script(USERNAME, PASSWORD, CLIENT_ID, CLIENT_SECRET);
        // Create a unique User-Agent for our bot
        UserAgent userAgent = new UserAgent("bot", "com.coocoofroggy.luckynumberbot", "1.0.3", USERNAME);
        // Authenticate our client
        reddit = OAuthHelper.automatic(new OkHttpNetworkAdapter(userAgent), oAuthCredentials);
        // Don't show http requests if uncommented
//        reddit.setLogHttp(false);


        // Loops

        if (debugMode) {
            // region Debug mode
            logger.setLevel(Level.DEBUG);
            logger.debug("DEBUG MODE");

            new Timer("r/AnonymousBotTesting").schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        anonymousCommentLoop();
                    } catch (Exception e) {
                        logger.error("Error in anonymousCommentLoop()", e);
                    }
                }
            }, 0, TimeUnit.SECONDS.toMillis(1));

//            InboxReference inbox = reddit.me().inbox();
//            new Timer("Inbox").schedule(new TimerTask() {
//                @Override
//                public void run() {
//                    try {
//                        inboxLoop(inbox);
//                    } catch (Exception e) {
//                        logger.error("Error in inboxLoop()", e);
//                    }
//                }
//            }, 0, TimeUnit.SECONDS.toMillis(10));
            // endregion
        } else {
            // r/all always has a thread running
            new Timer("r/all").schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        allCommentLoop();
                    } catch (Exception e) {
                        logger.error("Error in allCommentLoop()", e);
                    }
                }
            }, 0, TimeUnit.SECONDS.toMillis(1));

            // Inbox always has a thread running
            InboxReference inbox = reddit.me().inbox();
            new Timer("Inbox").schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        inboxLoop(inbox);
                    } catch (Exception e) {
                        logger.error("Error in inboxLoop()", e);
                    }
                }
            }, 0, TimeUnit.SECONDS.toMillis(10));

            // Connect to DB
            MongoUtils.connectToDatabase(System.getenv("MONGO_URI"));
            // Users take turns to share this thread
            new Timer("Users").schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        List<LNUser> manuallySearchingUsers = MongoUtils.fetchManuallySearchingUsers();
                        for (LNUser user : manuallySearchingUsers) {
                            userCommentLoop(user.getUsername());
                            // Sleep for 1 second between every user
                            TimeUnit.SECONDS.sleep(1);
                        }
                    } catch (Exception e) {
                        logger.error("Error in userCommentLoop()", e);
                    }
                }
            }, 0, TimeUnit.MINUTES.toMillis(1));
            // There's always at least one minute of break for MongoDB.
        }
    }

    // region Methods

    // This is the readable regex:
    // \[[^\]^\[]*\]\([^\)^\(]*\)|(?<=[^\d])(-?\d+(?:\.\d+)?)
    // This is added before every bracket "[, ], (, )" to prevent markdown escapes with "\"
    // (?<=[^\\])
    @SuppressWarnings("RegExpRedundantEscape")
    static final Pattern PATTERN_1 = Pattern.compile("(?<=[^\\\\])\\[[^\\]^\\[]*(?<=[^\\\\])\\](?<=[^\\\\])\\([^\\)^\\(]*(?<=[^\\\\])\\)|(?<=[^\\d])(-?\\d+(?:\\.\\d+)?)");
    static final Pattern PATTERN_2 = Pattern.compile("\\(.*\\)|(?<=[^\\d])(-?\\d+(?:\\.\\d+)?)");
    private static void countComment(Comment comment, int minimumTerms) {
        String content = comment.getBody();

        Matcher matcher1 = PATTERN_1.matcher(content);

        int matches = 0;
        ArrayList<Double> numbers = new ArrayList<>();
        double total = 0;

        while (matcher1.find()) {
            // It's a [brackets](match)
            if (matcher1.group(1) == null) {
                // Group 0 / entire match = throwaway since we can't have fixed-width lookbehind
                // This would be parts of links in parentheses
                // Group 1 = True match
                // How this works:
                // https://stackoverflow.com/a/23589204/13668740
                Matcher matcher2 = PATTERN_2.matcher(matcher1.group(0));
                while (matcher2.find()) {
                    // Skip past throwaway matches
                    if (matcher2.group(1) == null) continue;
                    double number = Double.parseDouble(matcher2.group(1));
                    // Don't count 0 as a number
                    if (number == 0)
                        continue;
                    total += number;
                    numbers.add(number);
                    matches++;
                }
                continue;
            }
            // Regular number
            double number = Double.parseDouble(matcher1.group(1));
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

        // If more than n numbers in their comment
        if (matches >= minimumTerms) {
            if (total == 69 | total == 420) {
                CommentReference commentReference = reddit.comment(comment.getId());

                StringBuilder termBuilder = new StringBuilder();
                NumberFormat nf = new DecimalFormat("#.###");
                for (int i = 0; i < numbers.size(); i++) {
                    double number = numbers.get(i);
                    // Code block
                    termBuilder.append("    ");
                    // On the first run, we don't want an operator sign
                    if (i == 0) {
                        // Number will never = 0 since we ignore zeros
                        if (number > 0) {
                            termBuilder.append("  ");
                        } else {
                            // Leave room for the negative sign
                            termBuilder.append(" ");
                        }
                    } else {
                        // Number will never = 0 since we ignore zeros
                        if (number > 0) {
                            // Addition symbol
                            termBuilder.append("+ ");
                        } else {
                            termBuilder.append("- ");
                            // We don't want the negative sign appearing again
                            number = Math.abs(number);
                        }
                    }
                    // Number prettified
                    termBuilder.append(nf.format(number))
                            // New line
                            .append("\n");
                }

                Map<String, String> replacements = Map.ofEntries(
                        Map.entry("total", nf.format(total)),
                        Map.entry("terms", termBuilder.toString()),
                        Map.entry("selfUsername", USERNAME),
                        Map.entry("subject", LuckyConfig.STALK_ME_SUBJECT),
                        Map.entry("message", LuckyConfig.STALK_ME_MESSAGE)
                );
                String commentBody = StringSubstitutor.replace(LuckyConfig.CONGRATS_TEMPLATE, replacements, "{", "}");

                try {
                    commentReference.reply(commentBody);
                } catch (ApiException e) {
                    logger.error("Post comment error", e);
                }
                commentReference.save();
            }
        }
    }

    private static void countCommentsUpTree(Comment comment) throws InterruptedException {
        String parentFullName = comment.getParentFullName();
        // Check all the parent comments
        // While the parent ID is still a comment
        while (parentFullName.startsWith("t1")) {
            // Count it
            Comment parentComment = (Comment) superLookup(parentFullName).getChildren().get(0);
            countComment(parentComment, 2);
            parentFullName = parentComment.getParentFullName();
        }
    }

    private static boolean mentionsSelf(String body) {
        return (body.toLowerCase().contains(("u/" + USERNAME).toLowerCase()));
    }

    // endregion

    // region Loops

    public static void allCommentLoop() {
        // Paginator for r/all comments
        BarebonesPaginator<Comment> allComments = reddit.subreddit("all").comments()
                .limit(Paginator.RECOMMENDED_MAX_LIMIT)
                .build();
        Listing<Comment> commentListing = allComments.next();
        for (Comment comment : commentListing) {
            countComment(comment, 3);
        }
    }

    // Debug mode
    public static void anonymousCommentLoop() {
        // Paginator for r/AnonymousBotTesting comments
        BarebonesPaginator<Comment> allComments = reddit.subreddit("AnonymousBotTesting").comments()
                .limit(Paginator.RECOMMENDED_MAX_LIMIT)
                .build();
        Listing<Comment> commentListing = allComments.next();
        for (Comment comment : commentListing) {
            countComment(comment, 3);
        }
    }

    private static void inboxLoop(InboxReference inbox) throws InterruptedException {
        BarebonesPaginator<Message> inboxIterate = inbox.iterate("unread")
                .limit(Paginator.RECOMMENDED_MAX_LIMIT)
                .build();
        // Loop through every page of unread messages
        for (Listing<Message> messages : inboxIterate) {
            // Every message on the page
            for (Message message : messages) {
                final String body = message.getBody();
                if (message.isComment()) {
                    List<Object> lookupResultList = superLookup(message.getFullName()).getChildren();
                    // Sometimes the comment is deleted or something, ignore it
                    if (!lookupResultList.isEmpty()) {
                        // We can cast to Comment because we already checked for isComment() above
                        Comment comment = (Comment) lookupResultList.get(0);
                        // Count this comment
                        countComment(comment, 2);
                        // If the comment mentions us
                        if (mentionsSelf(body)) {
                            // Count all comments above
                            countCommentsUpTree(comment);
                        }
                    }
                }
                if (body.contains("/stalkme")) {
                    UpdateResult result = MongoUtils.addUserToManualSearch(message.getAuthor());
                    if (result.wasAcknowledged()) {
                        if (result.getModifiedCount() > 0 || result.getUpsertedId() != null) {
                            reply(message, "All your new messages will now be scanned for Lucky Numbers!\n\nUse `/unstalkme` to undo.", inbox);
                        } else {
                            reply(message, "Your new messages are already being scanned for Lucky Numbers!\n\nUse `/unstalkme` to opt out.", inbox);
                        }
                    } else {
                        reply(message, "Something went wrong. Please try again!", inbox);
                    }
                } else if (body.contains("/unstalkme")) {
                    UpdateResult result = MongoUtils.removeUserFromManualSearch(message.getAuthor());
                    if (result.wasAcknowledged()) {
                        if (result.getModifiedCount() > 0 || result.getUpsertedId() != null) {
                            reply(message, "Your new messages will no longer be scanned for Lucky Numbers.\n\nUse `/stalkme` to opt back in.", inbox);
                        } else {
                            reply(message, "Your new messages are not being scanned for Lucky Numbers.\n\nUse `/stalkme` to opt in.", inbox);
                        }
                    } else {
                        reply(message, "Something went wrong. Please try again!", inbox);
                    }
                }
                inbox.markRead(true, message.getFullName());
            }
        }
    }

    public static void userCommentLoop(String username) {
        DefaultPaginator<PublicContribution<?>> userComments = reddit.user(username).history("comments")
                .sorting(UserHistorySort.NEW)
                .limit(Paginator.RECOMMENDED_MAX_LIMIT)
                .build();
        Listing<PublicContribution<?>> commentListing = userComments.next();
        for (PublicContribution<?> contribution : commentListing) {
            Comment comment = (Comment) contribution; // Can cast because of history("comments")
            countComment(comment, 2);
        }
    }

    // endregion

    // region Utils

    private static void reply(Message message, String replyBody, InboxReference inbox) {
        if (message.isComment()) {
            if (message.getAuthor() == null) return;
            inbox.compose(message.getAuthor(), "Lucky Message", replyBody);
        } else {
            inbox.replyTo(message.getFullName(), replyBody);
        }
    }

    /**
     * Same as RedditClient.lookup() but retries up to 10 times.
     * @param fullName fullname of the Reddit object
     * @return Returns the lookup listing
     */
    public static Listing<Object> superLookup(String fullName) throws InterruptedException {
        Listing<Object> lookupResult = reddit.lookup(fullName);
        int retryCounter = 0;
        while (true) {
            // Yes, sometimes it really takes 10 tries. Reddit is weird
            if (lookupResult.getChildren().isEmpty() && retryCounter < 10) {
                TimeUnit.SECONDS.sleep(1);
                lookupResult = reddit.lookup(fullName);
                retryCounter++;
            } else break;
        }
        return lookupResult;
    }

    // endregion
}
