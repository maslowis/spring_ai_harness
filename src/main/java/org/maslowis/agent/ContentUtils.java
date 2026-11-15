package org.maslowis.agent;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ContentUtils {
    public static final int PREVIEW_MAX_LENGTH = 150;

    public static String preview(String content) {
        String firstLine = isNotBlank(content) ? content.lines().findFirst().orElse("") : "";
        return firstLine.length() <= PREVIEW_MAX_LENGTH ? firstLine : firstLine.substring(0, PREVIEW_MAX_LENGTH) + "...";
    }

    public static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }

    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }
}
