package com.coocoofroggy.utils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class LuckyConfig {
    public static final String STALK_ME_SUBJECT = URLEncoder.encode("Stalk Me Pls", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
    public static final String STALK_ME_MESSAGE = URLEncoder.encode("/stalkme", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
    public static final String CONGRATS_TEMPLATE = """
            All the numbers in your comment added up to {total}. Congrats!
            
            {terms}    = {total}
            
            ^([Click here](https://www.reddit.com/message/compose?to={selfUsername}&subject={subject}&message={message}) to have me scan all your future comments.) \\
            ^(Summon me on specific comments with u/{selfUsername}.)
            """;
}
