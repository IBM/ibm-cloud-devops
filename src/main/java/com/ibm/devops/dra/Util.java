/*
 <notice>

 Copyright 2016, 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.dra;


import hudson.EnvVars;
import java.io.PrintStream;

/**
 * Utilities functions
 */

public class Util {
    /**
     * check if the str is null or empty
     * @param str
     * @return true if it is null or empty
     */
    public static boolean isNullOrEmpty(String str) {
        if (str == null || str.isEmpty()) {
            return true;
        }
        return false;
    }

    public static boolean allNotNullOrEmpty(String... strs) {
        for (String str : strs) {
            if (isNullOrEmpty(str)) {
                return false;
            }
        }
        return true;
    }
    
    public static boolean validateEnvVariables(EnvVars envVars, PrintStream printStream) {
    	Boolean valid = true;
    	if(envVars != null) {
    		String org = getOrg(envVars);
    		String space = getSpace(envVars);
    		String appName = getAppName(envVars);
    		String user = getUser(envVars);
    		String pwd = getPassword(envVars);
    		String webhook = getWebhookUrl(envVars);
    		
    		// perform validation and warn for each missing required property		
    		if (isNullOrEmpty(org)) {
    			printStream.println("[IBM Cloud DevOps] Missing required property IBM_CLOUD_DEVOPS_ORG");
    			valid = false;
    		}
    		if (isNullOrEmpty(space)) {
    			printStream.println("[IBM Cloud DevOps] Missing required property IBM_CLOUD_DEVOPS_SPACE");
    			valid = false;
    		}
    		if (isNullOrEmpty(appName)) {
    			printStream.println("[IBM Cloud DevOps] Missing required property IBM_CLOUD_DEVOPS_APP_NAME");
    			valid = false;
    		}
    		if (isNullOrEmpty(user)) {
    			printStream.println("[IBM Cloud DevOps] Missing required property IBM_CLOUD_DEVOPS_CREDS_USR");
    			valid = false;
    		}
    		if (isNullOrEmpty(pwd)) {
    			printStream.println("[IBM Cloud DevOps] Missing required property IBM_CLOUD_DEVOPS_CREDS_PSW");
    			valid = false;
    		}
    		if (isNullOrEmpty(webhook)) {
    			printStream.println("[IBM Cloud DevOps] Missing required property IBM_CLOUD_DEVOPS_WEBHOOK_URL");
    			valid = false;
    		}
    	}
    	return valid;
    }
    
    public static String getWebhookUrl(EnvVars envVars) {
    	String webhook = envVars.get("IBM_CLOUD_DEVOPS_WEBHOOK_URL");
    	//backward compatibility
		if (isNullOrEmpty(webhook)) {
			webhook = envVars.get("ICD_WEBHOOK_URL");
		}
		return webhook;
    }
    
    public static String getOrg(EnvVars envVars) {
    	String org = envVars.get("IBM_CLOUD_DEVOPS_ORG");
    	//backward compatibility
		if (isNullOrEmpty(org)) {
			org = envVars.get("CF_ORG");
		}
		return org;
    }
    
    public static String getSpace(EnvVars envVars) {
    	String space = envVars.get("IBM_CLOUD_DEVOPS_SPACE");
    	//backward compatibility
		if (isNullOrEmpty(space)) {
			space = envVars.get("CF_SPACE");
		}
		return space;
    }
    
    public static String getAppName(EnvVars envVars) {
    	String appName = envVars.get("IBM_CLOUD_DEVOPS_APP_NAME");
    	//backward compatibility
		if (isNullOrEmpty(appName)) {
			appName = envVars.get("CF_APP");
		}
		return appName;
    }
    
    public static String getUser(EnvVars envVars) {
    	String user = envVars.get("IBM_CLOUD_DEVOPS_CREDS_USR");
    	//backward compatibility
		if (isNullOrEmpty(user)) {
			user = envVars.get("CF_CREDS_USR");
		}
		return user;
    }
    
    public static String getPassword(EnvVars envVars) {
    	String pwd = envVars.get("IBM_CLOUD_DEVOPS_CREDS_PSW");
    	//backward compatibility
		if (isNullOrEmpty(pwd)) {
			pwd = envVars.get("CF_CREDS_PSW");
		}
		return pwd;
    }
    
    public static String getGitRepoUrl(EnvVars envVars) {
    	String gitUrl = envVars.get("GIT_URL");
		if (isNullOrEmpty(gitUrl)) {
			gitUrl = envVars.get("GIT_REPO"); // used in pipeline scripts
		}
		return gitUrl;
    }
    
    public static String getGitBranch(EnvVars envVars) {
    	return envVars.get("GIT_BRANCH");
    }
    
    public static String getGitCommit(EnvVars envVars) {
    	return envVars.get("GIT_COMMIT");
    }
}
