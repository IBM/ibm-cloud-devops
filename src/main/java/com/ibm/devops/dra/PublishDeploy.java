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
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import static com.ibm.devops.dra.Util.*;
import static com.ibm.devops.dra.UIMessages.*;

public class PublishDeploy extends AbstractDevOpsAction implements SimpleBuildStep {
	private static final String DEPLOYMENT_API_URL = "/toolchainids/{toolchain_id}/buildartifacts/{build_artifact}/builds/{build_id}/deployments";
	private static final String CONTROL_CENTER_URL_PART = "deploymentrisk?toolchainId=";
	private final static String CONTENT_TYPE_JSON = "application/json";
	private static PrintStream printStream;

	// form fields from UI
	private String applicationName;
	private String toolchainName;
	private String buildJobName;
	private String environmentName;
	private String credentialsId;
	private String applicationUrl;
	private String buildNumber;
	private static String bluemixToken;
	private static String preCredentials;

	//fields to support jenkins pipeline
	private String result;
	private String username;
	private String password;
	private String apikey;

	@DataBoundConstructor
	public PublishDeploy(String applicationName,
						 String toolchainName,
						 String buildJobName,
						 String environmentName,
						 String credentialsId,
						 String applicationUrl,
						 OptionalBuildInfo additionalBuildInfo) {
		this.applicationName = applicationName;
		this.toolchainName = toolchainName;
		this.buildJobName = buildJobName;
		this.environmentName = environmentName;
		this.credentialsId = credentialsId;
		this.applicationUrl = applicationUrl;

		if (additionalBuildInfo == null) {
			this.buildNumber = null;
		} else {
			this.buildNumber = additionalBuildInfo.buildNumber;
		}
	}

	public PublishDeploy(HashMap<String, String> envVarsMap, HashMap<String, String> paramsMap) {
		this.environmentName = paramsMap.get("environment");
		this.result = paramsMap.get("result");
		this.applicationName = envVarsMap.get(APP_NAME);
		this.toolchainName = envVarsMap.get(TOOLCHAIN_ID);

		if (isNullOrEmpty(envVarsMap.get(API_KEY))) {
			this.username = envVarsMap.get(USERNAME);
			this.password = envVarsMap.get(PASSWORD);
		} else {
			this.apikey = envVarsMap.get(API_KEY);
		}
	}

	public void setBuildNumber(String buildNumber) {
		this.buildNumber = buildNumber;
	}

	public void setApplicationUrl(String applicationUrl) {
		this.applicationUrl = applicationUrl;
	}

	/**
	 * We'll use this from the <tt>config.jelly</tt>.
	 */
	public String getApplicationName() {
		return applicationName;
	}

	public String getToolchainName() {
		return toolchainName;
	}

	public String getBuildJobName() {
		return buildJobName;
	}

	public String getEnvironmentName() {
		return environmentName;
	}

	public String getCredentialsId() {
		return credentialsId;
	}

	public String getApplicationUrl() {
		return applicationUrl;
	}

	public String getBuildNumber() {
		return buildNumber;
	}

	public String getResult() {
		return result;
	}

	public static class OptionalBuildInfo {
		private String buildNumber;

		@DataBoundConstructor
		public OptionalBuildInfo(String buildNumber) {
			this.buildNumber = buildNumber;
		}
	}

	@Override
	public void perform(@Nonnull Run build, @Nonnull FilePath workspace, @Nonnull Launcher launcher,
			@Nonnull TaskListener listener) throws InterruptedException, IOException {
		printStream = listener.getLogger();
		printPluginVersion(this.getClass().getClassLoader(), printStream);
		EnvVars envVars = build.getEnvironment(listener);
		String env = getDescriptor().getEnvironment();
		try {
			// Get the project name and build id from environment and expand the vars
			String applicationName = expandVariable(this.applicationName, envVars, true);
			String environmentName = expandVariable(this.environmentName, envVars, true);
			String toolchainId = expandVariable(this.toolchainName, envVars, true);
			String applicationUrl = expandVariable(this.applicationUrl, envVars, false);

			// get IBM cloud environment and token
			String buildNumber = isNullOrEmpty(this.buildNumber) ?
					getBuildNumber(envVars, buildJobName, build, printStream) : envVars.expand(this.buildNumber);
			String bluemixToken = getIBMCloudToken(this.credentialsId, this.apikey, this.username, this.password,
					env, build.getParent(), printStream);

			String baseUrl = chooseDLMSUrl(env) + DEPLOYMENT_API_URL;
			String dlmsUrl = setDLMSUrl(baseUrl, toolchainId, applicationName, buildNumber);
			String link = chooseControlCenterUrl(env) + CONTROL_CENTER_URL_PART + this.toolchainName;

			String deployStatus = getJobResult(build, this.result);
			uploadDeploymentInfo(bluemixToken, dlmsUrl, build, applicationUrl, environmentName, toolchainId, deployStatus);
			printStream.println(getMessageWithVar(CHECK_DEPLOY_STATUS, link));
		} catch (Exception e) {
			printStream.println(getMessageWithPrefix(GOT_ERRORS) + e.getMessage());
		}
	}

	private void uploadDeploymentInfo(String token, String dlmsUrl, Run build, String applicationUrl,
									  String environmentName, String toolchainName, String deployStatsus) throws Exception {
		String resStr;
		CloseableHttpClient httpClient = HttpClients.createDefault();
		HttpPost postMethod = new HttpPost(dlmsUrl);
		postMethod = addProxyInformation(postMethod);
		postMethod.setHeader("Authorization", token);
		postMethod.setHeader("Content-Type", CONTENT_TYPE_JSON);

		String jobUrl = getJobUrl(build, printStream);
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		TimeZone utc = TimeZone.getTimeZone("UTC");
		dateFormat.setTimeZone(utc);
		String timestamp = dateFormat.format(System.currentTimeMillis());

		// build up the json body
		Gson gson = new Gson();
		DeploymentInfoModel deploymentInfo = new DeploymentInfoModel(applicationUrl, environmentName, jobUrl, deployStatsus,
				timestamp);
		String json = gson.toJson(deploymentInfo);
		StringEntity data = new StringEntity(json, "UTF-8");
		postMethod.setEntity(data);
		CloseableHttpResponse response = httpClient.execute(postMethod);
		resStr = EntityUtils.toString(response.getEntity());

		int statusCode = response.getStatusLine().getStatusCode();
		if (statusCode == 200) {
			printStream.println(getMessageWithPrefix(UPLOAD_DEPLOY_SUCCESS));
		} else if (statusCode == 401 || statusCode == 403) {
			// if gets 401 or 403, it returns html
			throw new Exception(getMessageWithVar(FAIL_TO_UPLOAD_DATA, String.valueOf(statusCode), toolchainName));
		} else {
			JsonParser parser = new JsonParser();
			JsonElement element = parser.parse(resStr);
			JsonObject resJson = element.getAsJsonObject();
			if (resJson != null && resJson.has("message")) {
				throw new Exception(getMessageWithVar(FAIL_TO_UPLOAD_DATA_WITH_REASON, String.valueOf(statusCode), resJson.get("message").getAsString()));
			} else {
				throw new Exception(getMessageWithVar(FAIL_TO_UPLOAD_DATA_WITH_REASON, String.valueOf(statusCode), resJson.toString()));
			}
		}
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	// Overridden for better type safety.
	// If your plugin doesn't really define any property on Descriptor,
	// you don't have to do this.
	@Override
	public PublishDeployImpl getDescriptor() {
		return (PublishDeployImpl) super.getDescriptor();
	}

	/**
	 * Descriptor for {@link PublishBuild}. Used as a singleton. The
	 * class is marked as public so that it can be accessed from views.
	 *
	 * <p>
	 * See
	 * <tt>src/main/resources/com/ibm/devops/dra/PublishBuild/*.jelly</tt>
	 * for the actual HTML fragment for the configuration screen.
	 */
	@Extension // This indicates to Jenkins that this is an implementation of an
				// extension point.
	public static final class PublishDeployImpl extends BuildStepDescriptor<Publisher> {
		/**
		 * To persist global configuration information, simply store it in a
		 * field and call save().
		 *
		 * <p>
		 * If you don't want fields to be persisted, use <tt>transient</tt>.
		 */

		/**
		 * In order to load the persisted global configuration, you have to call
		 * load() in the constructor.
		 */
		public PublishDeployImpl() {
			super(PublishDeploy.class);
			load();
		}

		/**
		 * Performs on-the-fly validation of the form field 'credentialId'.
		 *
		 * @param value
		 *            This parameter receives the value that the user has typed.
		 * @return Indicates the outcome of the validation. This is sent to the
		 *         browser.
		 *         <p>
		 *         Note that returning {@link FormValidation#error(String)} does
		 *         not prevent the form from being saved. It just means that a
		 *         message will be displayed to the user.
		 */
		public FormValidation doCheckApplicationName(@QueryParameter String value)
				throws IOException, ServletException {
			return FormValidation.validateRequired(value);
		}

		public FormValidation doCheckToolchainName(@QueryParameter String value)
				throws IOException, ServletException {
			if (value == null || value.equals("empty")) {
				return FormValidation.errorWithMarkup(getMessageWithPrefix(TOOLCHAIN_ID_IS_REQUIRED));
			}
			return FormValidation.validateRequired(value);
		}

		public FormValidation doCheckEnvironmentName(@QueryParameter String value)
				throws IOException, ServletException {
			return FormValidation.validateRequired(value);
		}

		public FormValidation doTestConnection(@AncestorInPath ItemGroup context,
											   @QueryParameter("credentialsId") final String credentialsId) {
			String environment = getEnvironment();
			String targetAPI = chooseTargetAPI(environment);
			String iamAPI = chooseIAMAPI(environment);
			try {
				if (!credentialsId.equals(preCredentials) || isNullOrEmpty(bluemixToken)) {
					preCredentials = credentialsId;
					StandardCredentials credentials = findCredentials(credentialsId, context);
					bluemixToken = getTokenForFreeStyleJob(credentials, iamAPI, targetAPI, printStream);
				}
				return FormValidation.okWithMarkup(getMessage(TEST_CONNECTION_SUCCEED));
			} catch (Exception e) {
				e.printStackTrace();
				return FormValidation.error(getMessage(LOGIN_IN_FAIL));
			}
		}

		/**
		 * Autocompletion for build job name field
		 * @param value - user input for the build job name field
		 * @return
		 */
		public AutoCompletionCandidates doAutoCompleteBuildJobName(@QueryParameter String value) {
			AutoCompletionCandidates auto = new AutoCompletionCandidates();
			// get all jenkins job
			List<Job> jobs = Jenkins.getInstance().getAllItems(Job.class);
			for (int i = 0; i < jobs.size(); i++) {
				String jobName = jobs.get(i).getName();
				if (jobName.toLowerCase().startsWith(value.toLowerCase())) {
					auto.add(jobName);
				}
			}
			return auto;
		}

		/**
		 * This method is called to populate the credentials list on the Jenkins
		 * config page.
		 */
		public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context,
				@QueryParameter("target") final String target) {
			StandardListBoxModel result = new StandardListBoxModel();
			result.includeEmptyValue();
			result.withMatching(CredentialsMatchers.instanceOf(StandardCredentials.class),
					CredentialsProvider.lookupCredentials(StandardCredentials.class, context, ACL.SYSTEM,
							URIRequirementBuilder.fromUri(target).build()));
			return result;
		}

		/**
		 * Required Method This is used to determine if this build step is
		 * applicable for your chosen project type. (FreeStyle,
		 * MultiConfiguration, Maven) Some plugin build steps might be made to
		 * be only available to MultiConfiguration projects.
		 *
		 * @param aClass
		 *            The current project
		 * @return a boolean indicating whether this build step can be chose
		 *         given the project type
		 */
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			// Indicates that this builder can be used with all kinds of project
			// types
			// return FreeStyleProject.class.isAssignableFrom(aClass);
			return true;
		}

		/**
		 * Required Method
		 *
		 * @return The text to be displayed when selecting your build in the
		 *         project
		 */
		public String getDisplayName() {
			return getMessage(PUBLISH_DEPLOY_DISPLAY);
		}

		public String getEnvironment() {
			return getEnv(Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getConsoleUrl());
		}

		public boolean isDebug_mode() {
			return Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).isDebug_mode();
		}
	}
}
