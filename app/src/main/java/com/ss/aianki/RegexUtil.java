package com.ss.aianki;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class RegexUtil {
    private static String REGEX_TEXT_AND_LINK_FRAGMENT = "^\"{0,1}([\\w\\W]+?)\"{0,1}\\s*(https{0,1}://[^\\s]+:~:text=[^\\s]+)$";
    public static boolean isScrollingToTextFragment(final String text) {
        return text.matches(REGEX_TEXT_AND_LINK_FRAGMENT);
    }

    public static String getTextOfFragment(String plain) {
        String text = plain.replaceAll(REGEX_TEXT_AND_LINK_FRAGMENT, "$1");
        return (text == null || text == "") ? "" : text;
    }
    public static String getLinkOfFragment(String plain) {
        String text = plain.replaceAll(REGEX_TEXT_AND_LINK_FRAGMENT, "$2");
        return (text == null || text == "") ? "" : text;
    }

    public static String htmlTagFilter (String str)throws PatternSyntaxException {
        String regEx = "<.*?>";
        Pattern p_html = Pattern.compile(regEx, Pattern.CASE_INSENSITIVE);
        Matcher m_html = p_html.matcher(str);
        str = m_html.replaceAll("");

        return str.trim(); // 返回文本字符串
    }
}
