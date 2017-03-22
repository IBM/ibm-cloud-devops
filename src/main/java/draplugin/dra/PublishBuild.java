/*
    <notice>

    Copyright 2016, 2017 IBM Corporation

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
import net.sf.json.JSONObject;
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
import java.util.TimeZone;
import java.net.URLEncoder;

/**
 * Created by lix on 8/24/16.
 */
public class PublishBuild extends AbstractDevOpsAction implements SimpleBuildStep, Serializable {

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
    public static String bluemixToken;
    public static String preCredentials;

    // fields to support jenkins pipeline
    private String result;
    private String gitRepo;
    private String gitBranch;
    private String gitCommit;
    private String username;
    private String password;


    @DataBoundConstructor
    public PublishBuild(String applicationName, String orgName, String credentialsId, String toolchainName) {
        this.credentialsId = credentialsId;
        this.applicationName = applicationName;
        this.orgName = orgName;
        this.toolchainName = toolchainName;
    }

    public PublishBuild(String result, String gitRepo, String gitBranch, String gitCommit, String orgName, String applicationName, String toolchainName, String username, String password) {
        this.gitRepo = gitRepo;
        this.gitBranch = gitBranch;
        this.gitCommit = gitCommit;
        this.result = result;
        this.applicationName = applicationName;
        this.orgName = orgName;
        this.toolchainName = toolchainName;
        this.username = username;
        this.password = password;
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


    @Override
    public void perform(@Nonnull Run build, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {

        printStream = listener.getLogger();
        printPluginVersion(this.getClass().getClassLoader(), printStream);

        // create root dir for storing test result
        root = new File(build.getRootDir(), "DRA_TestResults");

        // Get the project name and build id from environment
        EnvVars envVars = build.getEnvironment(listener);

        if (!checkRootUrl(printStream)) {
            return;
        }

        // verify if user chooses advanced option to input customized DLMS
        String env = getDescriptor().getEnvironment();
        this.dlmsUrl = chooseDLMSUrl(env) + BUILD_API_URL;
        String targetAPI = chooseTargetAPI(env);

        //expand the variables
        this.orgName = envVars.expand(this.orgName);
        this.applicationName = envVars.expand(this.applicationName);
        this.toolchainName = envVars.expand(this.toolchainName);

        // Check required parameters
        if (Util.isNullOrEmpty(orgName) || Util.isNullOrEmpty(applicationName) || Util.isNullOrEmpty(toolchainName)) {
            printStream.println("[IBM Cloud DevOps] Missing few required configurations");
            printStream.println("[IBM Cloud DevOps] Error: Failed to upload Build Info.");
            return;
        }

        // get the Bluemix token
        try {
            if (Util.isNullOrEmpty(this.credentialsId)) {
                bluemixToken = GetBluemixToken(username, password, targetAPI);
            } else {
                bluemixToken = GetBluemixToken(build.getParent(), this.credentialsId, targetAPI);
            }

            printStream.println("[IBM Cloud DevOps] Log in successfully, get the Bluemix token");
        } catch (Exception e) {
            printStream.println("[IBM Cloud DevOps] Username/Password is not correct, fail to authenticate with Bluemix");
            printStream.println("[IBM Cloud DevOps]" + e.toString());
            return;
        }

        String link = chooseControlCenterUrl(env) + "deploymentrisk?orgName=" + this.orgName + "&toolchainId=" + this.toolchainName;
        System.out.println("link is " + link);
        if (uploadBuildInfo(bluemixToken, build, envVars)) {
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

        System.out.println("Getting GIT info: " + repoUrl + branch + commitId);
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
    private boolean uploadBuildInfo(String bluemixToken, Run build, EnvVars envVars) {
        String resStr = "";

        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            String url = this.dlmsUrl;
            url = url.replace("{org_name}", this.orgName);
            url = url.replace("{toolchain_id}", this.toolchainName);
            url = url.replace("{build_artifact}", URLEncoder.encode(this.applicationName, "UTF-8").replaceAll("\\+", "%20"));

            String buildNumber = getBuildNumber(envVars.get("JOB_NAME"),build);

            System.out.println("build number is " + buildNumber);
            String buildUrl = Jenkins.getInstance().getRootUrl() + build.getUrl();
            System.out.println("build url is " + buildUrl);

            HttpPost postMethod = new HttpPost(url);
            postMethod = addProxyInformation(postMethod);
            postMethod.setHeader("Authorization", bluemixToken);
            postMethod.setHeader("Content-Type", CONTENT_TYPE_JSON);

            String buildStatus;

            if ((build.getResult() != null && build.getResult().equals(Result.SUCCESS))
                    || (this.result != null && this.result.equals("SUCCESS"))) {
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
            BuildInfoModel.Repo repo = buildGitRepo(envVars);
            BuildInfoModel buildInfo = new BuildInfoModel(buildNumber, buildUrl, buildStatus, timestamp, repo);

            String json = gson.toJson(buildInfo);
            StringEntity data = new StringEntity(json);
            postMethod.setEntity(data);
            CloseableHttpResponse response = httpClient.execute(postMethod);
            resStr = EntityUtils.toString(response.getEntity());
            System.out.println("Request sent");
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
            printStream.println("[IBM Cloud DevOps] Please check if you have the access to " + this.orgName + " org");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
            System.out.println(e.toString());
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println(e.toString());
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
    public PublishBuild.PublishBuildActionImpl getDescriptor() {
        return (PublishBuild.PublishBuildActionImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link PublishBuild}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/draplugin/dra/PublishBuild/*.jelly</tt>
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

        private String environment;
        private boolean debug_mode;

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
            String targetAPI = chooseTargetAPI(environment);
            if (!credentialsId.equals(preCredentials) || Util.isNullOrEmpty(bluemixToken)) {
                preCredentials = credentialsId;
                try {
                    String newToken = GetBluemixToken(context, credentialsId, targetAPI);
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
            String targetAPI = chooseTargetAPI(environment);
            try {
                bluemixToken = GetBluemixToken(context, credentialsId, targetAPI);
            } catch (Exception e) {
                return new ListBoxModel();
            }
            if(debug_mode){
                LOGGER.info("#######UPLOAD BUILD INFO : calling getToolchainList#######");
            }
            ListBoxModel toolChainListBox = getToolchainList(bluemixToken, orgName, environment, debug_mode);
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

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            environment = formData.getString("environment");
            debug_mode = Boolean.parseBoolean(formData.getString("debug_mode"));
            save();
            return super.configure(req,formData);
        }

        public String getEnvironment() {
            return environment;
        }
        public boolean getDebugMode() {
            return debug_mode;
        }
    }
}
