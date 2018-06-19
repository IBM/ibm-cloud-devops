package com.ibm.devops.dra;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Created by lix on 6/13/18.
 */
public class UIMessages {
    public static ResourceBundle bundle = ResourceBundle.getBundle("messages", Locale.ENGLISH);
    public static final String PLUGIN_PREFIX = "PLUGIN_PREFIX";
    public static final String PUBLISH_BUILD_DISPLAY = "PUBLISH_BUILD_DISPLAY";
    public static final String PUBLISH_DEPLOY_DISPLAY = "PUBLISH_DEPLOY_DISPLAY";
    public static final String PUBLISH_SQ_DISPLAY = "PUBLISH_SQ_DISPLAY";

    public static final String USERNAME_PASSWORD_DEPRECATED = "USERNAME_PASSWORD_DEPRECATED";
    public static final String LOGIN_IN_SUCCEED = "LOGIN_IN_SUCCEED";
    public static final String LOGIN_IN_FAIL = "LOGIN_IN_FAIL";
    public static final String CHECK_BUILD_STATUS = "CHECK_BUILD_STATUS";
    public static final String CHECK_TEST_RESULT = "CHECK_TEST_RESULT";
    public static final String CHECK_DEPLOY_STATUS = "CHECK_DEPLOY_STATUS";
    public static final String NO_DECISION_FOUND = "NO_DECISION_FOUND";

    public static final String TOOLCHAIN_ID_IS_REQUIRED = "TOOLCHAIN_ID_IS_REQUIRED";
    public static final String TEST_CONNECTION_SUCCEED = "TEST_CONNECTION_SUCCEED";
    public static final String MISS_CONFIGURATIONS = "MISS_CONFIGURATIONS";
    public static final String PROJECT_URL_MISSED = "PROJECT_URL_MISSED";
    public static final String FAIL_TO_FIND_BUILD_JOB = "FAIL_TO_FIND_BUILD_JOB";
    public static final String FAIL_TO_FIND_FILE = "FAIL_TO_FIND_FILE";
    public static final String FAIL_TO_GET_API_TOKEN = "FAIL_TO_GET_API_TOKEN";
    public static final String GOT_EXCEPTION = "GOT_EXCEPTION";
    public static final String GOT_ERRORS = "GOT_ERRORS";
    public static final String FAIL_TO_GET_CREDENTIAL = "FAIL_TO_GET_CREDENTIAL";
    public static final String VERSION = "VERSION";
    public static final String RUN_JOB_INDEPENDENTLY = "RUN_JOB_INDEPENDENTLY";
    public static final String BUILD_JOB_IS_CURRENT_JOB = "BUILD_JOB_IS_CURRENT_JOB";
    public static final String UPLOAD_BUILD_SUCCESS = "UPLOAD_BUILD_SUCCESS";
    public static final String UPLOAD_DEPLOY_SUCCESS = "UPLOAD_DEPLOY_SUCCESS";
    public static final String FAIL_TO_UPLOAD_DATA = "FAIL_TO_UPLOAD_DATA";
    public static final String QUERY_SQ_QUALITY_SUCCESS = "QUERY_SQ_QUALITY_SUCCESS";
    public static final String QUERY_SQ_ISSUE_SUCCESS = "QUERY_SQ_ISSUE_SUCCESS";
    public static final String QUERY_SQ_METRIC_SUCCESS = "QUERY_SQ_METRIC_SUCCESS";
    public static final String FAIL_TO_QUERY_SQ_ISSUE = "FAIL_TO_QUERY_SQ_ISSUE";
    public static final String SQ_ISSUE_FAILURE_MESSAGE = "SQ_ISSUE_FAILURE_MESSAGE";
    public static final String FAIL_TO_AUTH_SQ = "FAIL_TO_AUTH_SQ";
    public static final String SQ_ISSUE_OVER_LIMIT = "SQ_ISSUE_OVER_LIMIT";
    public static final String SO_PROJECT_KEY_NOT_FOUND = "SO_PROJECT_KEY_NOT_FOUND";
    public static final String SQ_OTHER_EXCEPTION = "SQ_OTHER_EXCEPTION";
    public static final String UPLOAD_SQ_SUCCESS = "UPLOAD_SQ_SUCCESS";
    public static final String UPLOAD_FILE_SUCCESS = "UPLOAD_FILE_SUCCESS";
    public static final String UNSUPPORTED_RESULT_FILE = "UNSUPPORTED_RESULT_FILE";
    public static final String FAIL_TO_UPLOAD_DATA_WITH_REASON = "FAIL_TO_UPLOAD_DATA_WITH_REASON";
    public static final String GET_DECISION_SUCCESS = "GET_DECISION_SUCCESS";
    public static final String FAIL_TO_GET_DECISION_WITH_REASON = "FAIL_TO_GET_DECISION_WITH_REASON";
    public static final String FAIL_TO_GET_DECISION = "FAIL_TO_GET_DECISION";
    public static final String DECISION_REPORT = "DECISION_REPORT";
    public static final String UNIT_TEST = "UNIT_TEST";
    public static final String FVT = "FVT";
    public static final String CODE_COVERAGE = "CODE_COVERAGE";
    public static final String STATIC_SCAN = "STATIC_SCAN";
    public static final String DYNAMIC_SCAN = "DYNAMIC_SCAN";
    public static final String FAIL_TO_GET_POLICY_LIST = "FAIL_TO_GET_POLICY_LIST";
    public static final String FAIL_TO_CREATE_FILE = "FAIL_TO_CREATE_FILE";
    public static final String FAIL_TO_GET_JOB_RESULT = "FAIL_TO_GET_JOB_RESULT";
    public static final String MISS_REQUIRED_ENV_VAR = "MISS_REQUIRED_ENV_VAR";
    public static final String MISS_REQUIRED_STEP_PARAMS = "MISS_REQUIRED_STEP_PARAMS";
    public static final String RESULT_NEEDED = "RESULT_NEEDED";

    public static String getMessage(String messageKey) {
        return bundle.getString(messageKey);
    }

    public static String getMessageWithPrefix(String messageKey) {
        return getPrefix() + getMessage(messageKey);
    }

    public static String getMessageWithVar (String key, String... variable) {
        int index = 1;
        String str = bundle.getString(key);
        for (String s : variable) {
            String placeholder = "$" + index;
            str = str.replace(placeholder, s);
            index++;
        }
        return str;
    }

    public static String getMessageWithVarAndPrefix (String key, String... variable) {
        int index = 1;
        String str = bundle.getString(key);
        for (String s : variable) {
            String placeholder = "$" + index;
            str = str.replace(placeholder, s);
            index++;
        }
        return getPrefix() + str;
    }

    public static String getPrefix() {
        return bundle.getString(PLUGIN_PREFIX) + " ";
    }
}
