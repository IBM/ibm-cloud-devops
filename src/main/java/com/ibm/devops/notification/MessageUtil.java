/*
 <notice>

 Copyright 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.notification;

import hudson.EnvVars;
import java.io.PrintStream;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Message Utilities functions
 */

public class MessageUtil {
	
	public static JSONObject getOrgDetails(EnvVars envVars, JSONObject orgs, PrintStream printStream) {
		try {		
			JSONObject orgDetails = new JSONObject();
			// build org details - name and guid
			String orgName = envVars.get("CF_ORG");
			orgDetails.put("Name" , orgName);
			
			JSONArray resources = orgs.getJSONArray("resources");
			JSONObject metadata = (JSONObject) resources.get(0);
			JSONObject metadataObject = (JSONObject) metadata.get("metadata");
			String guid= (String) metadataObject.get("guid");
			orgDetails.put("Guid" , guid);

			return orgDetails;
			
		} catch (Exception e) {
			printStream.println("[IBM Cloud DevOps] Error: Failed to extract details for org " + envVars.get("CF_ORG"));
			e.printStackTrace(printStream);
			throw e;
		}
	}
	
	public static JSONObject getSpaceDetails(EnvVars envVars, JSONObject spaces, PrintStream printStream) {
		try {
			JSONObject spaceDetails = new JSONObject();
			// build space details - name and guid
			String spaceName = envVars.get("CF_SPACE");
			spaceDetails.put("Name" , spaceName);
			
			JSONArray resources = spaces.getJSONArray("resources");
			JSONObject metadata = (JSONObject) resources.get(0);
			JSONObject metadataObject = (JSONObject) metadata.get("metadata");
			String guid= (String) metadataObject.get("guid");
			spaceDetails.put("Guid" , guid);
			
			return spaceDetails;
			
		} catch (Exception e) {
			printStream.println("[IBM Cloud DevOps] Error: Failed to extract details for space " + envVars.get("CF_SPACE"));
			e.printStackTrace(printStream);
			throw e;
		}
	}
	
	public static JSONObject getAppDetails(EnvVars envVars, JSONObject apps, PrintStream printStream) {
		try {
			JSONObject appDetails = new JSONObject();
			// build app details - name and guid
			String appName = envVars.get("CF_APP");
			appDetails.put("Name" , appName);
			
			JSONArray resources = apps.getJSONArray("resources");			
			JSONObject metadata = (JSONObject) resources.get(0);
			JSONObject metadataObject = (JSONObject) metadata.get("metadata");
			String guid= (String) metadataObject.get("guid");
			appDetails.put("Guid" , guid);

			return appDetails;
			
		} catch (Exception e) {
			printStream.println("[IBM Cloud DevOps] Error: Failed to extract details for app " + envVars.get("CF_APP"));
			e.printStackTrace(printStream);
			throw e;
		}
	}
	
	public static JSONArray buildGitData(EnvVars envVars, PrintStream printStream) {
		try {
			JSONArray gitData = new JSONArray();
			String gitUrl = envVars.get("GIT_URL");
        	String gitBranch = envVars.get("GIT_BRANCH");
        	String gitCommit = envVars.get("GIT_COMMIT");
        	JSONObject gitInfo = new JSONObject();
        	gitInfo.put("GitURL" , gitUrl);
        	gitInfo.put("GitBranch" , gitBranch);
        	gitInfo.put("GitCommitID" , gitCommit);
        	gitData.add(gitInfo);
			
        	return gitData;
			
		} catch (Exception e) {
			printStream.println("[IBM Cloud DevOps] Error: Failed to build Git data.");
			e.printStackTrace(printStream);
			throw e;
		}
	}
	
	public static JSONObject formatDeployableMappingMessage(JSONObject org, JSONObject space, JSONObject app, String apiUrl, JSONArray gitData, PrintStream printStream) {
		try {	
			JSONObject deployableMappingMessage = new JSONObject();
			deployableMappingMessage.put("Org" , org);
			deployableMappingMessage.put("Space" , space);
			deployableMappingMessage.put("App" , app);
			deployableMappingMessage.put("ApiEndpoint" , apiUrl);
			deployableMappingMessage.put("Method" , "POST");
			deployableMappingMessage.put("GitData" , gitData);
			return deployableMappingMessage;
			
		} catch (Exception e) {
			printStream.println("[IBM Cloud DevOps] Error: Failed to build Deployable Mapping Message.");
			e.printStackTrace(printStream);
			throw e;
		}
	}
}
