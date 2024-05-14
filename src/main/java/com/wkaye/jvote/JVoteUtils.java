package com.wkaye.jvote;

public class JVoteUtils {

    public static String formatColor(String s) {
        return s.replaceAll("(&([a-f0-9]))", "\u00A7$2");

    }

    public static String printMessage(String message) {
        return formatColor("[&bJVote&f]&7 " + message);
    }
}
