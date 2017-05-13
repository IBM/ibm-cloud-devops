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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
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
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
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
import java.net.URLEncoder;
import java.util.List;


/**
 * Customized build step to get a gate decision from DRA backend
 */

public class EvaluateGate extends AbstractDevOpsAction implements SimpleBuildStep{

    private final static String CONTENT_TYPE = "application/json";

    // form fields from UI
    private final String policyName;
    private String orgName;
    private String buildJobName;
    private String applicationName;
    private String toolchainName;
    private String environmentName;
    private String credentialsId;
    private boolean willDisrupt;

    private EnvironmentScope scope;
    private String envName;
    private boolean isDeploy;

    private String draUrl;
    private PrintStream printStream;
    private static String bluemixToken;
    private static String preCredentials;

    //fields to support jenkins pipeline
    private String username;
    private String password;


    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public EvaluateGate(String policyName,
                        String orgName,
                        String applicationName,
                        String toolchainName,
                        String environmentName,
                        String buildJobName,
                        String credentialsId,
                        boolean willDisrupt,
                        EnvironmentScope scope) {
        this.policyName = policyName;
        this.orgName = orgName;
        this.applicationName = applicationName;
        this.toolchainName = toolchainName;
        this.environmentName = environmentName;
        this.buildJobName = buildJobName;
        this.credentialsId = credentialsId;
        this.willDisrupt = willDisrupt;
        this.scope = scope;
        this.envName = scope.getEnvName();
        this.isDeploy = scope.isDeploy();
    }

    public EvaluateGate(String policyName,
                        String orgName,
                        String applicationName,
                        String toolchainName,
                        String environmentName,
                        String username,
                        String password,
                        boolean willDisrupt) {
        this.policyName = policyName;
        this.orgName = orgName;
        this.applicationName = applicationName;
        this.toolchainName = toolchainName;
        this.envName = environmentName;
        this.willDisrupt = willDisrupt;
        this.username = username;
        this.password = password;
        this.willDisrupt = willDisrupt;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */

    public String getPolicyName() {
        return policyName;
    }

    public String getOrgName() {
        return orgName;
    }

    public String getBuildJobName() {
        return buildJobName;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getToolchainName() {
        return toolchainName;
    }

    public String getEnvironmentName() {
        return environmentName;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public boolean isWillDisrupt() {
        return willDisrupt;
    }

    public EnvironmentScope getScope() {
        return scope;
    }


    public String getEnvName() {
        return envName;
    }

    public boolean isDeploy() {
        return isDeploy;
    }

    /**
     * Override this method to get your operation done in the build step. When invoked, it is up to you, as a plugin developer
     * to add your actions, and/or perform the operations required by your plugin in this build step. Equally, it is up
     * to the developer to make the code run on the slave(master or an actual remote). This must be done given the builds
     * workspace, as in build.getWorkspace(). The workspace is the link to the slave, as it is the representation of the
     * remote file system.
     *
     * Build steps as you add them to your job configuration are executed sequentially, and the return value for your
     * builder should indicate whether to execute the next build step in the list.
     * @param build - the current build
     * @param launcher - the launcher
     * @param listener - the build listener
     * @throws InterruptedException
     * @throws IOException
     */
    @Override
    public void perform(@Nonnull Run<?, ?> build, @Nonnull FilePath filePath, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        // This is where you 'build' the project.
        printStream = listener.getLogger();
        printPluginVersion(this.getClass().getClassLoader(), printStream);

        // Get the project name and build id from environment
        EnvVars envVars = build.getEnvironment(listener);
        this.orgName = envVars.expand(this.orgName);
        this.applicationName = envVars.expand(this.applicationName);
        this.toolchainName = envVars.expand(this.toolchainName);

        if (!checkRootUrl(printStream)) {
            return;
        }

        if (this.isDeploy || !Util.isNullOrEmpty(this.envName)) {
            this.environmentName = envVars.expand(this.envName);
        }

        // verify if user chooses advanced option to input customized DRA
        String env = getDescriptor().getEnvironment();
        this.draUrl = chooseDRAUrl(env);
        String targetAPI = chooseTargetAPI(env);
        String reportUrl = chooseReportUrl(env);

        String buildNumber;

        // locate the build job that triggers current build
        Run triggeredBuild = getTriggeredBuild(build, buildJobName, envVars, printStream);
        if (triggeredBuild == null) {
            //failed to find the build job
            return;
        } else {
            if (Util.isNullOrEmpty(this.buildJobName)) {
                // handle the case which the build job name left empty, and the pipeline case
                this.buildJobName = envVars.get("JOB_NAME");
            }
            buildNumber = getBuildNumber(buildJobName, triggeredBuild);
        }


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

        // get decision response from DRA
        try {
            JsonObject decisionJson = getDecisionFromDRA(bluemixToken, buildNumber);
            if (decisionJson == null) {
                printStream.println("[IBM Cloud DevOps] get empty decision");
                return;
            }

            // retrieve the decision id to compose the report link
            String decisionId = String.valueOf(decisionJson.get("decision_id"));
            // remove the double quotes
            decisionId = decisionId.replace("\"","");

            // Show Proceed or Failed based on the decision
            String decision = String.valueOf(decisionJson.get("contents").getAsJsonObject().get("proceed"));
            if (decision.equals("true")) {
                decision = "Succeed";
            } else {
                decision = "Failed";
            }

            String cclink = chooseControlCenterUrl(env) + "deploymentrisk?orgName=" + URLEncoder.encode(this.orgName, "UTF-8") + "&toolchainId=" + this.toolchainName;

            GatePublisherAction action = new GatePublisherAction(reportUrl + decisionId, cclink, decision, this.policyName, build);
            build.addAction(action);

            // console output for a "fail" decision
            if (decision.equals("Failed")) {
                printStream.println("************************************");
                printStream.println("Check IBM Cloud DevOps Gate Evaluation report here -" + reportUrl + decisionId);
                printStream.println("Check IBM Cloud DevOps Deployment Risk Dashboard here -" + cclink);
                printStream.println("IBM Cloud DevOps decision to proceed is:  false");
                printStream.println("************************************");
                if (willDisrupt) {
                    Result result = Result.FAILURE;
                    build.setResult(result);
                    throw new AbortException("Decision is fail");
                }
                return;
            }

            // console output for a "proceed" decision
            printStream.println("************************************");
            printStream.println("Check IBM Cloud DevOps Gate Evaluation report here -" + reportUrl + decisionId);
            printStream.println("Check IBM Cloud DevOps Deployment Risk Dashboard here -" + cclink);
            printStream.println("IBM Cloud DevOps decision to proceed is:  true");
            printStream.println("************************************");
            return;

        } catch (IOException e) {
            if (e instanceof AbortException) {
                throw new AbortException("Decision is fail");
            } else {
                printStream.print("[IBM Cloud DevOps] Error: " + e.getMessage());
            }
        }
    }

//    public boolean perform(Run build, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
//
//    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /**
     * Send a request to DRA backend to get a decision
     * @param buildId - build ID, get from Jenkins environment
     * @return - the response decision Json file
     */
    private JsonObject getDecisionFromDRA(String bluemixToken, String buildId) throws IOException {
        // create http client and post method
        CloseableHttpClient httpClient = HttpClients.createDefault();
        String url = this.draUrl;
        url = url + "/organizations/" + orgName +
                "/toolchainids/" + toolchainName +
                "/buildartifacts/" + URLEncoder.encode(applicationName, "UTF-8").replaceAll("\\+", "%20") +
                "/builds/" + buildId +
                "/policies/" + URLEncoder.encode(policyName, "UTF-8").replaceAll("\\+", "%20") +
                "/decisions";
        if (!Util.isNullOrEmpty(this.environmentName)) {
            url = url.concat("?environment_name=" + environmentName);
        }

        HttpPost postMethod = new HttpPost(url);

        postMethod = addProxyInformation(postMethod);
        postMethod.setHeader("Authorization", bluemixToken);
        postMethod.setHeader("Content-Type", CONTENT_TYPE);

        CloseableHttpResponse response = httpClient.execute(postMethod);
        String resStr = EntityUtils.toString(response.getEntity());

        try {
            if (response.getStatusLine().toString().contains("200")) {
                // get 200 response
                JsonParser parser = new JsonParser();
                JsonElement element = parser.parse(resStr);
                JsonObject resJson = element.getAsJsonObject();
                printStream.println("[IBM Cloud DevOps] Get decision successfully");
                return resJson;
            } else {
                // if gets error status
                printStream.println("[IBM Cloud DevOps] Error: Failed to get a decision, response status " + response.getStatusLine());

                JsonParser parser = new JsonParser();
                JsonElement element = parser.parse(resStr);
                JsonObject resJson = element.getAsJsonObject();
                if (resJson != null && resJson.has("message")) {
                    printStream.println("[IBM Cloud DevOps] Reason: " + resJson.get("message"));
                }
            }
        } catch (JsonSyntaxException e) {
            printStream.println("[IBM Cloud DevOps] Invalid Json response, response: " + resStr);
        }

        return null;
    }


    @Override
    public EvaluateGate.EvaluateGateImpl getDescriptor() {
        return (EvaluateGate.EvaluateGateImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link EvaluateGate}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class EvaluateGateImpl extends BuildStepDescriptor<Publisher> {

        private String environment;
        private boolean debug_mode;

        /**
         * In order to load the persisted global configuration, you have to
         * call load() in the constructor.
         */
        public EvaluateGateImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'name'.
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

        public FormValidation doCheckApplicationName(@QueryParameter String value)
                throws IOException, ServletException {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckEnvironmentName(@QueryParameter String value)
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

        public FormValidation doCheckPolicyName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value == null || value.equals("empty")) {
               return FormValidation.errorWithMarkup("Fail to get the policies, please check your username/password or org name and make sure you have created policies for this org and toolchain.");
            }
            return FormValidation.ok();
        }

        public FormValidation doTestConnection(@AncestorInPath ItemGroup context,
                                               @QueryParameter("credentialsId") final String credentialsId) {
            String targetAPI = chooseTargetAPI(environment);
            if (!credentialsId.equals(preCredentials) || Util.isNullOrEmpty(bluemixToken)) {
                preCredentials = credentialsId;
                try {
                    String bluemixToken = getBluemixToken(context, credentialsId, targetAPI);
                    if (Util.isNullOrEmpty(bluemixToken)) {
                        EvaluateGate.bluemixToken = bluemixToken;
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
         * Autocompletion for build job name field
         * @param value
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
         * This method is called to populate the policy list on the Jenkins config page.
         * @param context
         * @param orgName
         * @param credentialsId
         * @return
         */
        public ListBoxModel doFillPolicyNameItems(@AncestorInPath ItemGroup context,
                                                  @QueryParameter final String orgName,
                                                  @QueryParameter final String toolchainName,
                                                  @QueryParameter final String credentialsId) {
            String targetAPI = chooseTargetAPI(environment);
            try {
                // if user changes to a different credential, need to get a new token
                if (!credentialsId.equals(preCredentials) || Util.isNullOrEmpty(bluemixToken)) {
                    bluemixToken = getBluemixToken(context, credentialsId, targetAPI);
                    preCredentials = credentialsId;
                }
            } catch (Exception e) {
                return new ListBoxModel();
            }
            if(debug_mode){
                LOGGER.info("#######GATE : calling getPolicyList#######");
            }
            return getPolicyList(bluemixToken, orgName, toolchainName, environment, debug_mode);

        }

        /**
         * This method is called to populate the toolchain list on the Jenkins config page.
         * @param context
         * @param orgName
         * @param credentialsId
         * @return
         */
        public ListBoxModel doFillToolchainNameItems(@AncestorInPath ItemGroup context,
                                                  @QueryParameter final String orgName,
                                                  @QueryParameter final String credentialsId) {
            String targetAPI = chooseTargetAPI(environment);
            try {
                bluemixToken = getBluemixToken(context, credentialsId, targetAPI);
            } catch (Exception e) {
                return new ListBoxModel();
            }
            if(debug_mode){
                LOGGER.info("#######GATE : calling getToolchainList#######");
            }
            return getToolchainList(bluemixToken, orgName, environment, debug_mode);
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
            return "IBM Cloud DevOps Gate";
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

