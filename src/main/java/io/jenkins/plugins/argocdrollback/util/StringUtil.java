package io.jenkins.plugins.argocdrollback.util;

public class StringUtil {

    public static boolean isNotNullOrEmpty(String param) {
        return param != null && !param.isEmpty();
    }

}
