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
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.TimeZone;

import static com.ibm.devops.dra.UIMessages.*;

public class PublishBuild extends AbstractDevOpsAction implements SimpleBuildStep {

    private static String BUILD_API_URL = "/toolchainids/{toolchain_id}/buildartifacts/{build_artifact}/builds";
    private final static String CONTENT_TYPE_JSON = "application/json";
    private final static String CONTENT_TYPE_XML = "application/xml";
    private final static String BUILD_STATUS_PART = "deploymentrisk?toolchainId=";

    // form fields from UI
    private String applicationName;
    private String orgName;
    private String credentialsId;
    private String toolchainName;
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
    private String apikey;
    // optional customized build number
    private String buildNumber;


    @DataBoundConstructor
    public PublishBuild(String applicationName, String credentialsId, String toolchainName, OptionalBuildInfo additionalBuildInfo) {
        this.credentialsId = credentialsId;
        this.applicationName = applicationName;
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
        this.toolchainName = envVarsMap.get(TOOLCHAIN_ID);

        if (Util.isNullOrEmpty(envVarsMap.get(API_KEY))) {
            this.username = envVarsMap.get(USERNAME);
            this.password = envVarsMap.get(PASSWORD);
        } else {
            this.apikey = envVarsMap.get(API_KEY);
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

        // Get the project name and build id from environment and expand the vars
        EnvVars envVars = build.getEnvironment(listener);
        this.applicationName = envVars.expand(this.applicationName);
        this.toolchainName = envVars.expand(this.toolchainName);

        // Check required parameters
        if (Util.isNullOrEmpty(applicationName) || Util.isNullOrEmpty(toolchainName)) {
            printStream.println(getMessageWithPrefix(MISS_CONFIGURATIONS));
            return;
        }

        // get IBM cloud environment and token
        String env = getDescriptor().getEnvironment();
        String bluemixToken;
        try {
            bluemixToken = getIBMCloudToken(this.credentialsId, this.apikey, this.username, this.password,
                    env, build.getParent(), printStream);
            String dlmsUrl = chooseDLMSUrl(env) + BUILD_API_URL;
            String link = chooseControlCenterUrl(env) + BUILD_STATUS_PART + this.toolchainName;

            // upload build info
            uploadBuildInfo(bluemixToken, build, envVars, applicationName, dlmsUrl);
            printStream.println(getMessageWithVar(GO_TO_CONTROL_CENTER, CHECK_BUILD_STATUS, link));
            BuildPublisherAction action = new BuildPublisherAction(link);
            build.addAction(action);
        } catch (Exception e) {
            printStream.println(getMessageWithPrefix(GOT_ERRORS) + e.getMessage());
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
     * @param dlmsUrl
     * @throws IOException
     */
    private void uploadBuildInfo(String bluemixToken, Run build, EnvVars envVars, String applicationName, String dlmsUrl) throws Exception {
        String resStr;

        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            String url = dlmsUrl;
            url = url.replace("{toolchain_id}", URLEncoder.encode(this.toolchainName, "UTF-8").replaceAll("\\+", "%20"));
            url = url.replace("{build_artifact}", URLEncoder.encode(applicationName, "UTF-8").replaceAll("\\+", "%20"));

            String buildNumber;
            if (Util.isNullOrEmpty(this.buildNumber)) {
                buildNumber = constructBuildNumber(envVars.get("JOB_NAME"), build);
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
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                // get 200 response
                printStream.println("[IBM Cloud DevOps] Build Information is uploaded successfully");
            } else if (statusCode == 401 || statusCode == 403) {
                // if gets 401 or 403, it returns html
                throw new Exception(" Failed to upload data, response status " + statusCode
                        + " Please check if you have the access to toolchain " + toolchainName);
            } else {
                JsonParser parser = new JsonParser();
                JsonElement element = parser.parse(resStr);
                JsonObject resJson = element.getAsJsonObject();
                if (resJson != null && resJson.has("message")) {
                    throw new Exception(" Response Status:" + statusCode + ", Reason: " + resJson.get("message"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
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
        public FormValidation doCheckToolchainName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value == null || value.equals("empty")) {
                return FormValidation.errorWithMarkup(getMessageWithPrefix(TOOLCHAIN_ID_IS_REQUIRED));
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckApplicationName(@QueryParameter String value)
                throws IOException, ServletException {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doTestConnection(@AncestorInPath ItemGroup context,
                                               @QueryParameter("credentialsId") final String credentialsId) {
            String environment = getEnvironment();
            String targetAPI = chooseTargetAPI(environment);
            String iamAPI = chooseIAMAPI(environment);
            try {
                if (!credentialsId.equals(preCredentials) || Util.isNullOrEmpty(bluemixToken)) {
                    preCredentials = credentialsId;
                    StandardCredentials credentials = findCredentials(credentialsId, context);
                    bluemixToken = getTokenForTestConnection(credentials, iamAPI, targetAPI);
                }
                return FormValidation.okWithMarkup(getMessage(TEST_CONNECTION_SUCCEED));
            } catch (Exception e) {
                e.printStackTrace();
                return FormValidation.error(getMessage(LOGIN_IN_FAIL));
            }
        }

        /**
         * This method is called to populate the credentials list on the Jenkins config page.
         */
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context,
                                                     @QueryParameter("target") final String target) {
            StandardListBoxModel result = new StandardListBoxModel();
            result.includeEmptyValue();
            result.withMatching(CredentialsMatchers.instanceOf(StandardCredentials.class),
                    CredentialsProvider.lookupCredentials(
                            StandardCredentials.class,
                            context,
                            ACL.SYSTEM,
                            URIRequirementBuilder.fromUri(target).build()
                    )
            );
            return result;
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
    }
}
