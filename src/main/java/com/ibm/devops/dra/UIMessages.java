package com.ibm.devops.dra;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Created by lix on 6/13/18.
 */
public class UIMessages {
    public static ResourceBundle bundle = ResourceBundle.getBundle("messages", Locale.ENGLISH);
    public static final String PLUGIN_PREFIX = "PLUGIN_PREFIX";
    public static final String USERNAME_PASSWORD_DEPRECATED = "USERNAME_PASSWORD_DEPRECATED";
    public static final String LOGIN_IN_SUCCEED = "LOGIN_IN_SUCCEED";
    public static final String LOGIN_IN_FAIL = "LOGIN_IN_FAIL";
    public static final String GO_TO_CONTROL_CENTER = "GO_TO_CONTROL_CENTER";
    public static final String CHECK_BUILD_STATUS = "CHECK_BUILD_STATUS";
    public static final String CHECK_TEST_RESULT = "CHECK_TEST_RESULT";
    public static final String CHECK_DEPLOY_STATUS = "CHECK_DEPLOY_STATUS";
    public static final String NO_DECISION_FOUND = "NO_DECISION_FOUND";

    public static final String TOOLCHAIN_ID_IS_REQUIRED = "TOOLCHAIN_ID_IS_REQUIRED";
    public static final String TEST_CONNECTION_SUCCEED = "TEST_CONNECTION_SUCCEED";
    public static final String MISS_CONFIGURATIONS = "MISS_CONFIGURATIONS";
    public static final String PROJECT_URL_MISSED = "PROJECT_URL_MISSED";
    public static final String FAILED_TO_FIND_BUILD_JOB = "FAILED_TO_FIND_BUILD_JOB";
    public static final String FAILED_TO_FIND_FILE = "FAILED_TO_FIND_FILE";
    public static final String GOT_EXCEPTION = "GOT_EXCEPTION";
    public static final String GOT_ERRORS = "GOT_ERRORS";

    public static String getMessage(String messageKey) {
        return bundle.getString(messageKey);
    }

    public static String getMessageWithPrefix(String messageKey) {
        return getPrefix() + " " + getMessage(messageKey);
    }

    public static String getMessageWithVar (String key1, String key2, String variable) {
        return getPrefix() + bundle.getString(key1) + variable + bundle.getString(key2);
    }

    public static String getPrefix() {
        return bundle.getString(PLUGIN_PREFIX);
    }
}
