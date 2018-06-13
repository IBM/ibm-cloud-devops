package com.ibm.devops.dra;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Created by lix on 6/13/18.
 */
public class UIMessages {
    public static ResourceBundle bundle;
    public static final String USERNAME_PASSWORD_DEPRECATED = "USERNAME_PASSWORD_DEPRECATED";
    public static final String FREESTYLE_DEPRECATED = "FREESTYLE_DEPRECATED";

    public UIMessages() {
        bundle = ResourceBundle.getBundle("messages", Locale.ENGLISH);
    }

    public static String getMessage (String messageKey) {
        return bundle.getString(messageKey);
    }
}
