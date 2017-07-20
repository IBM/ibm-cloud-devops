/*
 <notice>

 Copyright 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.notification;

import com.ibm.devops.dra.Util;
import hudson.EnvVars;
import java.io.PrintStream;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Message Utilities functions
 */

public class MessageUtil {
	
	public static JSONArray buildGitData(EnvVars envVars, PrintStream printStream) {
		try {
			String gitUrl = Util.getGitRepoUrl(envVars);
        	String gitBranch = Util.getGitBranch(envVars);
        	String gitCommit = Util.getGitCommit(envVars);
        	
        	JSONObject gitInfo = new JSONObject();
        	gitInfo.put("GitURL" , gitUrl);
        	gitInfo.put("GitBranch" , gitBranch);
        	gitInfo.put("GitCommitID" , gitCommit);
        	
			JSONArray gitData = new JSONArray();
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
