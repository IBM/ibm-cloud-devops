/*
    <notice>

    Copyright (c)2016 IBM Corporation

     Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
    The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

    </notice>
 */
package draplugin.dra;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.gson.*;
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
import net.sf.json.JSONObject;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.TimeZone;

public class UploadDeploymentInfoAction extends AbstractDevOpsAction implements SimpleBuildStep, Serializable {

	private static String DEPLOYMENT_API_URL = "/organizations/{org_name}/toolchainids/{toolchain_id}/buildartifacts/{build_artifact}/builds/{build_id}/deployments";
	private final static String CONTENT_TYPE_JSON = "application/json";

	private PrintStream printStream;

	// form fields from UI
	private String applicationName;
	private String toolchainName;
	private String orgName;
	private String buildJobName;
	private String environmentName;
	private String credentialsId;
	private String applicationUrl;
	private String buildNumber;
	private String buildUrl;
	public static String bluemixToken;
	public static String preCredentials;

	@DataBoundConstructor
	public UploadDeploymentInfoAction(String applicationName,
									  String toolchainName,
									  String orgName,
									  String buildJobName,
									  String environmentName,
									  String credentialsId,
									  String applicationUrl,
									  OptionalBuildInfo additionalBuildInfo) {
		this.applicationName = applicationName;
		this.toolchainName = toolchainName;
		this.orgName = orgName;
		this.buildJobName = buildJobName;
		this.environmentName = environmentName;
		this.credentialsId = credentialsId;
		this.applicationUrl = applicationUrl;

		if (additionalBuildInfo == null) {
			this.buildNumber = null;
			this.buildUrl = null;
		} else {
			this.buildNumber = additionalBuildInfo.buildNumber;
			this.buildUrl = additionalBuildInfo.buildUrl;
		}
	}

	public static class OptionalBuildInfo {
		private String buildNumber;
		private String buildUrl;

		@DataBoundConstructor
		public OptionalBuildInfo(String buildNumber, String buildUrl) {
			this.buildNumber = buildNumber;
			this.buildUrl = buildUrl;
		}
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

	public String getOrgName() {
		return orgName;
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

	public String getBuildUrl() {
		return buildUrl;
	}

	@Override
	public void perform(@Nonnull Run build, @Nonnull FilePath workspace, @Nonnull Launcher launcher,
			@Nonnull TaskListener listener) throws InterruptedException, IOException {

		printStream = listener.getLogger();
		printPluginVersion(this.getClass().getClassLoader(), printStream);

		// Get the project name and build id from environment
		EnvVars envVars = build.getEnvironment(listener);

		if (Util.isNullOrEmpty(Jenkins.getInstance().getRootUrl())) {
			printStream.println(
					"[DevOps Insight Plugin] The Jenkins global root url is not set. Please set it to used this postbuild Action.  \"Manage Jenkins > Configure System > Jenkins URL\"");
			printStream.println("[DevOps Insight Plugin] Error: Failed to upload Deployment Info.");
			return;
		}

		// expand to support env vars
		this.orgName = envVars.expand(this.orgName);
		this.toolchainName = envVars.expand(this.toolchainName);
		this.applicationName = envVars.expand(this.applicationName);
		this.environmentName = envVars.expand(this.environmentName);
		this.applicationUrl = envVars.expand(this.applicationUrl);

		String buildNumber, buildUrl;
		// if user does not specify the build number
		if (Util.isNullOrEmpty(this.buildNumber)) {
			// locate the build job that triggers current build
			Run triggeredBuild = getTriggeredBuild(build, buildJobName, envVars, printStream);
			if (triggeredBuild == null) {
				//failed to find the build job
				return;
			} else {
				buildNumber = getBuildNumber(buildJobName, triggeredBuild);
				String rootUrl = Jenkins.getInstance().getRootUrl();
				buildUrl = rootUrl + triggeredBuild.getUrl();
			}
		} else {
			buildNumber = envVars.expand(this.buildNumber);
			buildUrl = envVars.expand(this.buildUrl);
		}

		if (Util.isNullOrEmpty(orgName) || Util.isNullOrEmpty(applicationName) || Util.isNullOrEmpty(environmentName) || Util.isNullOrEmpty(toolchainName)) {
			printStream.println("[DevOps Insight Plugin] Missing few required configurations");
			printStream.println("[DevOps Insight Plugin] Error: Failed to upload Deployment Info.");
			return;
		}

		// verify if user chooses advanced option to input customized DLMS
		String env = getDescriptor().getEnvironment();
		String dlmsUrl = chooseDLMSUrl(env) + DEPLOYMENT_API_URL;
		dlmsUrl = dlmsUrl.replace("{org_name}", orgName);
		dlmsUrl = dlmsUrl.replace("{toolchain_id}", URLEncoder.encode(toolchainName, "UTF-8").replaceAll("\\+", "%20"));
		dlmsUrl = dlmsUrl.replace("{build_artifact}", URLEncoder.encode(applicationName, "UTF-8").replaceAll("\\+", "%20"));
		dlmsUrl = dlmsUrl.replace("{build_id}", buildNumber);

		String link = chooseControlCenterUrl(env) + "deploymentrisk?orgName=" + this.orgName + "&toolchainId=" + this.toolchainName;

		// get the Bluemix token
		String targetAPI = chooseTargetAPI(env);
		try {
			bluemixToken = GetBluemixToken(build.getParent(), this.credentialsId, targetAPI);
			printStream.println("[DevOps Insight Plugin] Log in successfully, get the Bluemix token");
		} catch (Exception e) {
			printStream.println("[DevOps Insight Plugin] Username/Password is not correct, fail to authenticate with Bluemix");
			printStream.println("[DevOps Insight Plugin]" + e.toString());
			return;
		}

		if (uploadDeploymentInfo(bluemixToken, dlmsUrl, build, buildUrl)) {
			printStream.println("[DevOps Insight Plugin] Go to Control Center (" + link + ") to check your build status");

			BuildPublisherAction action = new BuildPublisherAction(link);
			build.addAction(action);
		}
	}

	private boolean uploadDeploymentInfo(String token, String dlmsUrl, Run build, String buildUrl) throws IOException {

		CloseableHttpClient httpClient = HttpClients.createDefault();
		HttpPost postMethod = new HttpPost(dlmsUrl);

		postMethod.setHeader("Authorization", token);
		postMethod.setHeader("Content-Type", CONTENT_TYPE_JSON);

		String buildStatus;
		if (build.getResult().equals(Result.SUCCESS)) {
			buildStatus = "pass";
		} else {
			buildStatus = "fail";
		}
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		TimeZone utc = TimeZone.getTimeZone("UTC");
		dateFormat.setTimeZone(utc);
		String timestamp = dateFormat.format(build.getTime());

		// build up the json body
		Gson gson = new Gson();
		DeploymentInfoModel deploymentInfo = new DeploymentInfoModel(applicationUrl, environmentName, buildUrl, buildStatus,
				timestamp);

		String json = gson.toJson(deploymentInfo);
		StringEntity data = new StringEntity(json);
		postMethod.setEntity(data);
		CloseableHttpResponse response = httpClient.execute(postMethod);
		String resStr = EntityUtils.toString(response.getEntity());

		try {
			if (response.getStatusLine().toString().contains("200")) {
				// get 200 response
				printStream.println("[DevOps Insight Plugin] Deployment Info uploaded successfully");
				return true;

			} else {
				// if gets error status
				printStream.println("[DevOps Insight Plugin] Error: Failed to upload Deployment Info, response status "
						+ response.getStatusLine());

				JsonParser parser = new JsonParser();
				JsonElement element = parser.parse(resStr);
				JsonObject resJson = element.getAsJsonObject();
				if (resJson != null && resJson.has("user_error")) {
					printStream.println("[DevOps Insight Plugin] Reason: " + resJson.get("user_error"));
				}
			}
		} catch (JsonSyntaxException e) {
			printStream.println("[DevOps Insight Plugin] Invalid Json response, response: " + resStr);
		} catch (IllegalStateException e) {
			// will be triggered when 403 Forbidden
			printStream.println("[DevOps Insight Plugin] Please check if you have the access to " + this.orgName + " org");
		}

		return false;
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	// Overridden for better type safety.
	// If your plugin doesn't really define any property on Descriptor,
	// you don't have to do this.
	@Override
	public UploadDeploymentInfoAction.UploadDeploymentInfoActionImpl getDescriptor() {
		return (UploadDeploymentInfoAction.UploadDeploymentInfoActionImpl) super.getDescriptor();
	}

	/**
	 * Descriptor for {@link UploadBuildInfoAction}. Used as a singleton. The
	 * class is marked as public so that it can be accessed from views.
	 *
	 * <p>
	 * See
	 * <tt>src/main/resources/draplugin/dra/UploadBuildInfoAction/*.jelly</tt>
	 * for the actual HTML fragment for the configuration screen.
	 */
	@Extension // This indicates to Jenkins that this is an implementation of an
				// extension point.
	public static final class UploadDeploymentInfoActionImpl extends BuildStepDescriptor<Publisher> {
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
		public UploadDeploymentInfoActionImpl() {
			super(UploadDeploymentInfoAction.class);
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

		private String environment;
		private boolean debug_mode;

		public FormValidation doCheckOrgName(@QueryParameter String value) throws IOException, ServletException {
			return FormValidation.validateRequired(value);
		}

		public FormValidation doCheckApplicationName(@QueryParameter String value)
				throws IOException, ServletException {
			return FormValidation.validateRequired(value);
		}

		public FormValidation doCheckToolchainName(@QueryParameter String value)
				throws IOException, ServletException {
			if (value == null || value.equals("empty")) {
				return FormValidation.errorWithMarkup("Could not retrieve list of toolchains. Please check your username and password. If you have not created a toolchain, create one <a target='_blank' href='https://console.ng.bluemix.net/devops/create'>here</a>.");
			}
			return FormValidation.ok();
		}

		public FormValidation doCheckEnvironmentName(@QueryParameter String value)
				throws IOException, ServletException {
			return FormValidation.validateRequired(value);
		}

		public FormValidation doTestConnection(@AncestorInPath ItemGroup context,
				@QueryParameter("credentialsId") final String credentialsId) {
			String targetAPI = chooseTargetAPI(environment);
					try {
						String bluemixToken = GetBluemixToken(context, credentialsId, targetAPI);
						if (Util.isNullOrEmpty(bluemixToken)) {
							return FormValidation.warning("<b>Got empty token</b>");
						} else {
							return FormValidation.okWithMarkup("<b>Connection successful</b>");
						}
					} catch (Exception e) {
				return FormValidation.error("Failed to log in to Bluemix, please check your username/password");
			}
		}

		/**
		 * Autocompletion for build job name field
		 * 
		 * @param value
		 *            - user input for the build job name field
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
			result.withMatching(CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
					CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, context, ACL.SYSTEM,
							URIRequirementBuilder.fromUri(target).build()));
			return result;
		}

		/**
		 * This method is called to populate the toolchain list on the Jenkins config page.
		 * @param context
		 * @param orgName
		 * @param credentialsId
		 * @return
		 */
		public ListBoxModel doFillToolchainNameItems(@AncestorInPath ItemGroup context,
													 @QueryParameter("credentialsId") final String credentialsId,
													 @QueryParameter("orgName") final String orgName) {
			String targetAPI = chooseTargetAPI(environment);
			try {
				// if user changes to a different credential, need to get a new token
				if (!credentialsId.equals(preCredentials) || Util.isNullOrEmpty(bluemixToken)) {
					bluemixToken = GetBluemixToken(context, credentialsId, targetAPI);
					preCredentials = credentialsId;
				}
			} catch (Exception e) {
				return new ListBoxModel();
			}
			ListBoxModel toolChainListBox = getToolchainList(bluemixToken, orgName, environment, debug_mode);
			return toolChainListBox;
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
			return "Publish deployment information to DevOps Insights";
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
			// To persist global configuration information,
			// set that to properties and call save().
			environment = formData.getString("environment");
			debug_mode = Boolean.parseBoolean(formData.getString("debug_mode"));
			save();
			return super.configure(req, formData);
		}

		public String getEnvironment() {
			return environment;
		}
		public boolean getDebugMode() {
			return debug_mode;
		}
	}
}
