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

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
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
        UserAgent userAgent = new UserAgent("bot", "com.coocoofroggy.luckynumberbot", "1.0.1", "LuckyNumberBot");

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
        while (true) {
            for (Listing<Comment> commentListing : allComments) {
                for (Comment comment : commentListing) {
                    String content = comment.getBody();

                    // Ignore comments with brackets for links
                    if (content.contains("[") | content.contains("["))
                        continue;

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
                        continue;

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

                            String replyUrl = commentReference.reply(
                                    "All the numbers in your comment added up to " + nf.format(total) + ". Congrats!\n\n" +
                                            stringBuilder +
                                            "    = " + nf.format(total)
                            ).getUrl();
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

                            System.out.println(total);
                            System.out.println(replyUrl);
                        }
                    }
                }

                // Restart the stream
                allComments.restart();
            }
        }
    }
}
