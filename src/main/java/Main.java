import net.dean.jraw.RedditClient;
import net.dean.jraw.http.OkHttpNetworkAdapter;
import net.dean.jraw.http.UserAgent;
import net.dean.jraw.models.Comment;
import net.dean.jraw.models.Listing;
import net.dean.jraw.oauth.Credentials;
import net.dean.jraw.oauth.OAuthHelper;
import net.dean.jraw.pagination.BarebonesPaginator;
import net.dean.jraw.pagination.Paginator;
import net.dean.jraw.references.CommentReference;

import java.util.ArrayList;
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
        UserAgent userAgent = new UserAgent("bot", "com.coocoofroggy.luckynumberbot", "1.0.0", "LuckyNumberBot");

        // Authenticate our client
        RedditClient reddit = OAuthHelper.automatic(new OkHttpNetworkAdapter(userAgent), oauthCreds);

        // Paginator for r/all comments
        BarebonesPaginator<Comment> allComments = reddit.subreddit("all").comments()
//        BarebonesPaginator<Comment> allComments = reddit.subreddit("AnonymousBotTesting").comments()
                .limit(Paginator.RECOMMENDED_MAX_LIMIT)
                .build();
        commentLoop(reddit, allComments);
    }

    public static void commentLoop(RedditClient reddit, BarebonesPaginator<Comment> allComments) {
        for (Listing<Comment> commentListing : allComments) {
            for (Comment comment : commentListing) {
                String content = comment.getBody();
                Pattern numberPattern = Pattern.compile("\\d+\\.*\\d*");
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
                    continue;

                // If more than 1 number in their comment
                if (matches > 1) {
                    if (total == 69) {
                        CommentReference commentReference = reddit.comment(comment.getId());

                        StringBuilder stringBuilder = new StringBuilder();
                        for (float number: numbers) {
                            stringBuilder.append("    ").append(number).append(" +").append("\n");
                        }

                        commentReference.reply(
                                "All the numbers in your comment added up to 69. Congrats!\n\n" +
                                        stringBuilder +
                                        "    = 69.0"
                        );
                        commentReference.save();

                        System.out.println(total);
                        System.out.println(comment.getUrl());
                    }
                }
            }

            // Restart the stream
            allComments.restart();
            // Start again
            commentLoop(reddit, allComments);
        }
    }
}
