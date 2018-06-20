/*
 <notice>

 Copyright 2016, 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.dra;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.EnvVars;
import hudson.ProxyConfiguration;
import hudson.model.*;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.cloudfoundry.client.lib.HttpProxyConfiguration;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

import static com.ibm.devops.dra.AbstractDevOpsAction.RESULT_SUCCESS;
import static com.ibm.devops.dra.UIMessages.*;

/**
 * Utilities functions
 */

public class Util {

	/**
	 * Print the plugin version
	 * @param loader
	 * @param printStream
	 */
	public static void printPluginVersion(ClassLoader loader, PrintStream printStream) {
		final Properties properties = new Properties();
		try {
			properties.load(loader.getResourceAsStream("plugin.properties"));
			printStream.println(getMessageWithPrefix(VERSION) + properties.getProperty("version"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * find Jenkins credential in the runtime
	 * @param credentialsId
	 * @param context
	 * @return
	 * @throws Exception
	 */
	public static StandardCredentials findCredentials(String credentialsId, Job context) throws Exception {
		List<StandardCredentials> standardCredentials = CredentialsProvider.lookupCredentials(
				StandardCredentials.class,
				context,
				ACL.SYSTEM);
		StandardCredentials credentials =
				CredentialsMatchers.firstOrNull(standardCredentials, CredentialsMatchers.withId(credentialsId));
		if (credentials == null)
			throw new Exception(getMessage(FAIL_TO_GET_CREDENTIAL));
		return credentials;
	}

	/**
	 * find Jenkins credentials in the UI configuration
	 * @param credentialsId
	 * @param context
	 * @return
	 * @throws Exception
	 */
	public static StandardCredentials findCredentials(String credentialsId, ItemGroup context) throws Exception {
		List<StandardCredentials> standardCredentials = CredentialsProvider.lookupCredentials(
				StandardCredentials.class,
				context,
				ACL.SYSTEM);
		StandardCredentials credentials =
				CredentialsMatchers.firstOrNull(standardCredentials, CredentialsMatchers.withId(credentialsId));
		if (credentials == null)
			throw new Exception(getMessage(FAIL_TO_GET_CREDENTIAL));
		return credentials;
	}

	/**
	 * check if the root url in the jenkins is set correctly
	 * @param printStream
	 * @return
	 */
	public static boolean checkRootUrl(PrintStream printStream) {
		if (isNullOrEmpty(Jenkins.getInstance().getRootUrl())) {
			printStream.println(getMessage(PROJECT_URL_MISSED));
			return false;
		}
		return true;
	}

	/**
	 * Get the current Jenkins job url
	 * @param build
	 * @param printStream
	 * @return
	 */
	public static String getJobUrl(Run build, PrintStream printStream) {
		String jobUrl;
		if (checkRootUrl(printStream)) {
			jobUrl = Jenkins.getInstance().getRootUrl() + build.getUrl();
		} else {
			jobUrl = build.getAbsoluteUrl();
		}
		return jobUrl;
	}

	/**
	 * Get the current Jenkins job result
	 * @param build
	 * @param result
     * @return
     */
	public static String getJobResult(Run build, String result) {
		String buildStatus;
		Result res = build.getResult();
		if ((res != null && res.equals(Result.SUCCESS))
				|| (result != null && result.equals(RESULT_SUCCESS))) {
			buildStatus = "pass";
		} else {
			buildStatus = "fail";
		}
		return buildStatus;
	}


	/**
	 * get the root project
	 * @param job - the source job
	 * @return the root project
	 */
	private static Job<?, ?> getRootProject(Job<?, ?> job) {
		if (job instanceof AbstractProject) {
			return ((AbstractProject<?,?>)job).getRootProject();
		} else {
			return job;
		}
	}

	// retrieve the "folder" (jenkins root if no folder used) for this build
	private static ItemGroup getItemGroup(Run<?, ?> build) {
		return getRootProject(build.getParent()).getParent();
	}


	/**
	 * Recursive function to locate the triggered build
	 * @param job - the target job
	 * @param parent - the current job
	 * @return the specific build of the target job
	 */
	private static Run<?,?> getBuild(Job<?,?> job, Run<?,?> parent) {
		Run<?,?> result = null;

		// Upstream job for matrix will be parent project, not only individual configuration:
		List<String> jobNames = new ArrayList<>();
		jobNames.add(job.getFullName());
		if ((job instanceof AbstractProject<?,?>) && ((AbstractProject<?,?>)job).getRootProject() != job) {
			jobNames.add(((AbstractProject<?,?>)job).getRootProject().getFullName());
		}

		List<Run<?, ?>> upstreamBuilds = new ArrayList<>();

		for (Cause cause: parent.getCauses()) {
			if (cause instanceof Cause.UpstreamCause) {
				Cause.UpstreamCause upstream = (Cause.UpstreamCause) cause;
				Run<?, ?> upstreamRun = upstream.getUpstreamRun();
				if (upstreamRun != null) {
					upstreamBuilds.add(upstreamRun);
				}
			}
		}

		if (parent instanceof AbstractBuild) {
			AbstractBuild<?, ?> parentBuild = (AbstractBuild<?,?>)parent;

			Map<AbstractProject, Integer> parentUpstreamBuilds = parentBuild.getUpstreamBuilds();
			for (Map.Entry<AbstractProject, Integer> buildEntry : parentUpstreamBuilds.entrySet()) {
				upstreamBuilds.add(buildEntry.getKey().getBuildByNumber(buildEntry.getValue()));
			}
		}

		for (Run<?, ?> upstreamBuild : upstreamBuilds) {
			Run<?,?> run;

			if(upstreamBuild == null) {
				continue;
			}
			if (jobNames.contains(upstreamBuild.getParent().getFullName())) {
				// Use the 'job' parameter instead of directly the 'upstreamBuild', because of Matrix jobs.
				run = job.getBuildByNumber(upstreamBuild.getNumber());
			} else {
				// Figure out the parent job and do a recursive call to getBuild
				run = getBuild(job, upstreamBuild);
			}

			if (run != null){
				if ((result == null) || (result.getNumber() > run.getNumber())) {
					result = run;
				}
			}

		}

		return result;
	}

	/**
	 * locate triggered build
	 * @param build - the current running build of this job
	 * @param name - the build job name that you are going to locate
	 * @return
	 */
	public static Run<?,?> getTriggeredBuild(Run build, String name, EnvVars envVars, PrintStream printStream) {
		// if user specify the build job as current job or leave it empty
		if (name == null || name.isEmpty() || name.equals(build.getParent().getName())) {
			printStream.println(getMessageWithPrefix(BUILD_JOB_IS_CURRENT_JOB));
			return build;
		} else {
			name = envVars.expand(name);
			Job<?, ?> job = Jenkins.getInstance().getItem(name, getItemGroup(build), Job.class);
			if (job != null) {
				Run src = getBuild(job, build);
				if (src == null) {
					// if user runs the test job independently
					printStream.println(getMessageWithPrefix(RUN_JOB_INDEPENDENTLY));
					src = job.getLastSuccessfulBuild();
				}

				return src;
			}
		}
		// cannot find the build job
		return null;
	}

	public static HttpGet addProxyInformation (HttpGet instance) {
            /* Add proxy to request if proxy settings in Jenkins UI are set. */
		ProxyConfiguration proxyConfig = Jenkins.getInstance().proxy;
		if(proxyConfig != null){
			if((!isNullOrEmpty(proxyConfig.name)) && proxyConfig.port != 0) {
				HttpHost proxy = new HttpHost(proxyConfig.name, proxyConfig.port, "http");
				RequestConfig config = RequestConfig.custom().setProxy(proxy).build();
				instance.setConfig(config);
			}
		}
		return instance;
	}

	public static HttpPost addProxyInformation (HttpPost instance) {
            /* Add proxy to request if proxy settings in Jenkins UI are set. */
		ProxyConfiguration proxyConfig = Jenkins.getInstance().proxy;
		if(proxyConfig != null){
			if((!isNullOrEmpty(proxyConfig.name)) && proxyConfig.port != 0) {
				HttpHost proxy = new HttpHost(proxyConfig.name, proxyConfig.port, "http");
				RequestConfig config = RequestConfig.custom().setProxy(proxy).build();
				instance.setConfig(config);
			}
		}
		return instance;
	}

	/**
	 * build proxy for cloud foundry http connection
	 * @param targetURL - target API URL
	 * @return the full target URL
	 */
	public static HttpProxyConfiguration buildProxyConfiguration(URL targetURL) {
		ProxyConfiguration proxyConfig = Jenkins.getInstance().proxy;
		if (proxyConfig == null) {
			return null;
		}

		String host = targetURL.getHost();
		for (Pattern p : proxyConfig.getNoProxyHostPatterns()) {
			if (p.matcher(host).matches()) {
				return null;
			}
		}
		return new HttpProxyConfiguration(proxyConfig.name, proxyConfig.port);
	}

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

    public static boolean allNotNullOrEmpty(HashMap<String, String> vars, PrintStream printStream) {
        for (Map.Entry<String, String> e : vars.entrySet()) {
            if (isNullOrEmpty(e.getValue())) {
				if (e.getKey().contains("IBM"))
					printStream.println("[IBM Cloud DevOps] Missing environment variables \"" + e.getKey() + "\" configurations");
				else
					printStream.println("[IBM Cloud DevOps] Missing required parameters, \"" + e.getKey() + "\"");
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
