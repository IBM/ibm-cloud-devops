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
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.gson.*;
import hudson.*;
import hudson.model.*;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.kohsuke.stapler.*;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.TimeZone;
import java.net.URLEncoder;

public class PublishBuild extends AbstractDevOpsAction implements SimpleBuildStep {

    private static String BUILD_API_URL = "/organizations/{org_name}/toolchainids/{toolchain_id}/buildartifacts/{build_artifact}/builds";
    private final static String CONTENT_TYPE_JSON = "application/json";
    private final static String CONTENT_TYPE_XML = "application/xml";

    // form fields from UI
    private String applicationName;
    private String orgName;
    private String credentialsId;
    private String toolchainName;

    private String dlmsUrl;
    private PrintStream printStream;
    private File root;
    private static String bluemixToken;
    private static String preCredentials;

    // fields to support jenkins pipeline
    private String result;
    private String gitRepo;
    private String gitBranch;
    private String gitCommit;
    private String username;
    private String password;
    // optional customized build number
    private String buildNumber;


    @DataBoundConstructor
    public PublishBuild(String applicationName, String orgName, String credentialsId, String toolchainName, OptionalBuildInfo additionalBuildInfo) {
        this.credentialsId = credentialsId;
        this.applicationName = applicationName;
        this.orgName = orgName;
        this.toolchainName = toolchainName;
        if (additionalBuildInfo == null) {
            this.buildNumber = null;
        } else {
            this.buildNumber = additionalBuildInfo.buildNumber;
        }
    }

    public PublishBuild(HashMap<String, String> envVarsMap, HashMap<String, String> paramsMap) {
        this.gitRepo = paramsMap.get("gitRepo");
        this.gitBranch = paramsMap.get("gitBranch");
        this.gitCommit = paramsMap.get("gitCommit");
        this.result = paramsMap.get("result");
        this.applicationName = envVarsMap.get(APP_NAME);
        this.orgName = envVarsMap.get(ORG_NAME);
        this.toolchainName = envVarsMap.get(TOOLCHAIN_ID);

        if (Util.isNullOrEmpty(envVarsMap.get(API_KEY))) {
            this.username = envVarsMap.get(USERNAME);
            this.password = envVarsMap.get(PASSWORD);
        } else {
            this.username = "apikey";
            this.password = envVarsMap.get(API_KEY);
        }
    }

    @DataBoundSetter
    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    @DataBoundSetter
    public void setOrgName(String orgName) {
        this.orgName = orgName;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    @DataBoundSetter
    public void setToolchainName(String toolchainName) {
        this.toolchainName = toolchainName;
    }

    public void setBuildNumber(String buildNumber) {
        this.buildNumber = buildNumber;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getApplicationName() {
        return applicationName;
    }

    public String getOrgName() {
        return orgName;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getToolchainName() {
        return toolchainName;
    }

    public String getBuildNumber() {
        return buildNumber;
    }

    public static class OptionalBuildInfo {
        private String buildNumber;

        @DataBoundConstructor
        public OptionalBuildInfo(String buildNumber, String buildUrl) {
            this.buildNumber = buildNumber;
        }
    }

    @Override
    public void perform(@Nonnull Run build, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {

        printStream = listener.getLogger();
        printPluginVersion(this.getClass().getClassLoader(), printStream);

        // create root dir for storing test result
        root = new File(build.getRootDir(), "DRA_TestResults");

        // Get the project name and build id from environment
        EnvVars envVars = build.getEnvironment(listener);

        // verify if user chooses advanced option to input customized DLMS
        String env = getDescriptor().getEnvironment();
        this.dlmsUrl = chooseDLMSUrl(env) + BUILD_API_URL;
        String targetAPI = chooseTargetAPI(env);

        //expand the variables
        String orgName = envVars.expand(this.orgName);
        String applicationName = envVars.expand(this.applicationName);
        this.toolchainName = envVars.expand(this.toolchainName);

        // Check required parameters
        if (Util.isNullOrEmpty(orgName) || Util.isNullOrEmpty(applicationName) || Util.isNullOrEmpty(toolchainName)) {
            printStream.println("[IBM Cloud DevOps] Missing few required configurations");
            printStream.println("[IBM Cloud DevOps] Error: Failed to upload Build Info.");
            return;
        }

        String bluemixToken;
        // get the Bluemix token
        try {
            if (Util.isNullOrEmpty(this.credentialsId)) {
                bluemixToken = getBluemixToken(username, password, targetAPI);
            } else {
                bluemixToken = getBluemixToken(build.getParent(), this.credentialsId, targetAPI);
            }

            printStream.println("[IBM Cloud DevOps] Log in successfully, get the Bluemix token");
        } catch (Exception e) {
            printStream.println("[IBM Cloud DevOps] Username/Password is not correct, fail to authenticate with Bluemix");
            printStream.println("[IBM Cloud DevOps]" + e.toString());
            return;
        }

        String link = chooseControlCenterUrl(env) + "deploymentrisk?orgName=" + URLEncoder.encode(this.orgName, "UTF-8") + "&toolchainId=" + this.toolchainName;
        if (uploadBuildInfo(bluemixToken, build, envVars, orgName, applicationName)) {
            printStream.println("[IBM Cloud DevOps] Go to Control Center (" + link + ") to check your build status");
            BuildPublisherAction action = new BuildPublisherAction(link);
            build.addAction(action);
        }
    }

    /**
     * Construct the Git data model
     * @param envVars
     * @return
     */
    public BuildInfoModel.Repo buildGitRepo(EnvVars envVars) {
        String repoUrl = envVars.get("GIT_URL");
        String branch = envVars.get("GIT_BRANCH");
        String commitId = envVars.get("GIT_COMMIT");

        repoUrl = Util.isNullOrEmpty(repoUrl) ? this.gitRepo : repoUrl;
        branch = Util.isNullOrEmpty(branch) ? this.gitBranch : branch;
        commitId = Util.isNullOrEmpty(commitId) ? this.gitCommit : commitId;
        if (!Util.isNullOrEmpty(branch)) {
            String[] parts = branch.split("/");
            branch = parts[parts.length - 1];
        }

        BuildInfoModel.Repo repo = new BuildInfoModel.Repo(repoUrl, branch, commitId);
        return repo;
    }

    /**
     * Upload the build information to DLMS - API V2.
     * @param bluemixToken
     * @param build
     * @param envVars
     * @throws IOException
     */
    private boolean uploadBuildInfo(String bluemixToken, Run build, EnvVars envVars, String orgName, String applicationName) {
        String resStr = "";

        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            String url = this.dlmsUrl;
            url = url.replace("{org_name}", URLEncoder.encode(orgName, "UTF-8").replaceAll("\\+", "%20"));
            url = url.replace("{toolchain_id}", URLEncoder.encode(this.toolchainName, "UTF-8").replaceAll("\\+", "%20"));
            url = url.replace("{build_artifact}", URLEncoder.encode(applicationName, "UTF-8").replaceAll("\\+", "%20"));

            String buildNumber;
            if (Util.isNullOrEmpty(this.buildNumber)) {
                buildNumber = getBuildNumber(envVars.get("JOB_NAME"), build);
            } else {
                buildNumber = envVars.expand(this.buildNumber);
            }

            String buildUrl;
            if (checkRootUrl(printStream)) {
                buildUrl = Jenkins.getInstance().getRootUrl() + build.getUrl();
            } else {
                buildUrl = build.getAbsoluteUrl();
            }
            HttpPost postMethod = new HttpPost(url);
            postMethod = addProxyInformation(postMethod);
            postMethod.setHeader("Authorization", bluemixToken);
            postMethod.setHeader("Content-Type", CONTENT_TYPE_JSON);

            String buildStatus;
            Result result = build.getResult();
            if ((result != null && result.equals(Result.SUCCESS))
                    || (this.result != null && this.result.equals(RESULT_SUCCESS))) {
                buildStatus = "pass";
            } else {
                buildStatus = "fail";
            }

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            TimeZone utc = TimeZone.getTimeZone("UTC");
            dateFormat.setTimeZone(utc);
            String timestamp = dateFormat.format(System.currentTimeMillis());

            // build up the json body
            Gson gson = new Gson();
            BuildInfoModel.Repo repo = buildGitRepo(envVars);
            BuildInfoModel buildInfo = new BuildInfoModel(buildNumber, buildUrl, buildStatus, timestamp, repo);

            String json = gson.toJson(buildInfo);
            StringEntity data = new StringEntity(json, "UTF-8");
            postMethod.setEntity(data);

            CloseableHttpResponse response = httpClient.execute(postMethod);
            resStr = EntityUtils.toString(response.getEntity());
            if (response.getStatusLine().toString().contains("200")) {
                // get 200 response
                printStream.println("[IBM Cloud DevOps] Upload Build Information successfully");
                return true;

            } else {
                // if gets error status
                printStream.println("[IBM Cloud DevOps] Error: Failed to upload, response status " + response.getStatusLine());

                JsonParser parser = new JsonParser();
                JsonElement element = parser.parse(resStr);
                JsonObject resJson = element.getAsJsonObject();
                if (resJson != null && resJson.has("user_error")) {
                    printStream.println("[IBM Cloud DevOps] Reason: " + resJson.get("user_error"));
                }
            }
        } catch (JsonSyntaxException e) {
            printStream.println("[IBM Cloud DevOps] Invalid Json response, response: " + resStr);
        } catch (IllegalStateException e) {
            // will be triggered when 403 Forbidden
            try {
                printStream.println("[IBM Cloud DevOps] Please check if you have the access to " + URLEncoder.encode(this.orgName, "UTF-8") + " org");
            } catch (UnsupportedEncodingException e1) {
                e1.printStackTrace();
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
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
    public PublishBuildActionImpl getDescriptor() {
        return (PublishBuildActionImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link PublishBuild}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/com/ibm/devops/dra/PublishBuild/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class PublishBuildActionImpl extends BuildStepDescriptor<Publisher> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */

        /**
         * In order to load the persisted global configuration, you have to
         * call load() in the constructor.
         */
        public PublishBuildActionImpl() {
            super(PublishBuild.class);
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'credentialId'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         *      <p>
         *      Note that returning {@link FormValidation#error(String)} does not
         *      prevent the form from being saved. It just means that a message
         *      will be displayed to the user.
         */

        public FormValidation doCheckOrgName(@QueryParameter String value)
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

        public FormValidation doCheckApplicationName(@QueryParameter String value)
                throws IOException, ServletException {
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
            if (!credentialsId.equals(preCredentials) || Util.isNullOrEmpty(bluemixToken)) {
                preCredentials = credentialsId;
                try {
                    String newToken = getBluemixToken(context, credentialsId, targetAPI);
                    if (Util.isNullOrEmpty(newToken)) {
                        bluemixToken = newToken;
                        return FormValidation.warning("<b>Got empty token</b>");
                    } else {
                        return FormValidation.okWithMarkup("<b>Connection successful</b>");
                    }
                } catch (Exception e) {
                    return FormValidation.error("Failed to log in to Bluemix, please check your username/password");
                }
            } else {
                return FormValidation.okWithMarkup("<b>Connection successful</b>");
            }
        }

        /**
         * This method is called to populate the credentials list on the Jenkins config page.
         */
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context,
                                                     @QueryParameter("target") final String target) {
            StandardListBoxModel result = new StandardListBoxModel();
            result.includeEmptyValue();
            result.withMatching(CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
                    CredentialsProvider.lookupCredentials(
                            StandardUsernameCredentials.class,
                            context,
                            ACL.SYSTEM,
                            URIRequirementBuilder.fromUri(target).build()
                    )
            );
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
            String environment = getEnvironment();
            String targetAPI = chooseTargetAPI(environment);
            try {
                bluemixToken = getBluemixToken(context, credentialsId, targetAPI);
            } catch (Exception e) {
                return new ListBoxModel();
            }
            if(isDebug_mode()){
                LOGGER.info("#######UPLOAD BUILD INFO : calling getToolchainList#######");
            }
            ListBoxModel toolChainListBox = getToolchainList(bluemixToken, orgName, environment, isDebug_mode());
            return toolChainListBox;

        }

        /**
         * Required Method
         * This is used to determine if this build step is applicable for your chosen project type. (FreeStyle, MultiConfiguration, Maven)
         * Some plugin build steps might be made to be only available to MultiConfiguration projects.
         *
         * @param aClass The current project
         * @return a boolean indicating whether this build step can be chose given the project type
         */
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            // return FreeStyleProject.class.isAssignableFrom(aClass);
            return true;
        }

        /**
         * Required Method
         * @return The text to be displayed when selecting your build in the project
         */
        public String getDisplayName() {
            return "Publish build information to IBM Cloud DevOps";
        }

        public String getEnvironment() {
            return getEnv(Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getConsoleUrl());
        }

        public boolean isDebug_mode() {
            return Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).isDebug_mode();
        }
    }
}
