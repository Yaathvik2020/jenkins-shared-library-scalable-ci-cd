package org.example

class Utils {
    static String buildTag(String appName, String buildNumber) {
        return "${appName}-${buildNumber}"
    }
}
