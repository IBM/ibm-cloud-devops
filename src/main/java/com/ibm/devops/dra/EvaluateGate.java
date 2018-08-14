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
import com.google.gson.JsonObject;
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
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ibm.devops.dra.UIMessages.*;
import static com.ibm.devops.dra.Util.*;

/**
 * Customized build step to get a gate decision from DRA backend
 */
public class EvaluateGate extends AbstractDevOpsAction implements SimpleBuildStep{
    private static final String REPORT_URL_PART = "decisionreport";
    private final static String TOOLCHAIN_PART = "&toolchainId=";
    private static final String CONTROL_CENTER_URL_PART = "deploymentrisk?toolchainId=";
    private static final String DECISION_API_PART = "/api/v5/toolchainids/{toolchain_id}/buildartifacts/{build_artifact}/builds/{build_id}/policies/{policy_name}/decisions";

    // form fields from UI
    private String policyName;
    private String buildJobName;
    private String applicationName;
    private String toolchainName;
    private String credentialsId;
    private boolean willDisrupt;

    private EnvironmentScope scope;
    private String envName;
    private boolean isDeploy;
    private PrintStream printStream;
    private static String bluemixToken;
    private static String preCredentials;

    //fields to support jenkins pipeline
    private String username;
    private String password;
    private String apikey;
    private String env;
    // optional customized build number
    private String buildNumber;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public EvaluateGate(String policyName,
                        String applicationName,
                        String toolchainName,
                        String buildJobName,
                        String credentialsId,
                        boolean willDisrupt,
                        EnvironmentScope scope,
                        OptionalBuildInfo additionalBuildInfo) {
        this.policyName = policyName;
        this.applicationName = applicationName;
        this.toolchainName = toolchainName;
        this.buildJobName = buildJobName;
        this.credentialsId = credentialsId;
        this.willDisrupt = willDisrupt;
        this.scope = scope;
        this.envName = scope.getEnvName();
        this.isDeploy = scope.isDeploy();
        if (additionalBuildInfo == null) {
            this.buildNumber = null;
        } else {
            this.buildNumber = additionalBuildInfo.buildNumber;
        }
    }

    public EvaluateGate(HashMap<String, String> envVarsMap,
                        String policyName,
                        String environmentName,
                        boolean willDisrupt) {

        this.applicationName = envVarsMap.get(APP_NAME);
        this.toolchainName = envVarsMap.get(TOOLCHAIN_ID);
        if (isNullOrEmpty(envVarsMap.get(API_KEY))) {
            this.username = envVarsMap.get(USERNAME);
            this.password = envVarsMap.get(PASSWORD);
        } else {
            this.apikey = envVarsMap.get(API_KEY);
        }
        this.envName = environmentName;
        this.willDisrupt = willDisrupt;
        this.policyName = policyName;
        this.env = envVarsMap.get(ENV);
    }

    public void setBuildNumber(String buildNumber) {
        this.buildNumber = buildNumber;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */

    public String getPolicyName() {
        return policyName;
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

    public String getCredentialsId() {
        return credentialsId;
    }

    public boolean isWillDisrupt() {
        return willDisrupt;
    }

    public EnvironmentScope getScope() {
        return scope;
    }

    public String getBuildNumber() {
        return buildNumber;
    }

    public String getEnvName() {
        return envName;
    }

    public boolean isDeploy() {
        return isDeploy;
    }

    public static class OptionalBuildInfo {
        private String buildNumber;

        @DataBoundConstructor
        public OptionalBuildInfo(String buildNumber, String buildUrl) {
            this.buildNumber = buildNumber;
        }
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
     * @throws AbortException
     */
    @Override
    public void perform(@Nonnull Run<?, ?> build, @Nonnull FilePath filePath, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws AbortException {
        // This is where you 'build' the project.
        printStream = listener.getLogger();
        printPluginVersion(this.getClass().getClassLoader(), printStream);

        try {
            EnvVars envVars = build.getEnvironment(listener);
            // Get the project name and build id from environment
            String applicationName = expandVariable(this.applicationName, envVars, true);
            String toolchainId = expandVariable(this.toolchainName, envVars, true);
            String policyName = expandVariable(this.policyName, envVars, true);
            String environmentName = "";
            if (this.isDeploy || !isNullOrEmpty(this.envName)) {
                environmentName = envVars.expand(this.envName);
            }

            String buildNumber = isNullOrEmpty(this.buildNumber) ?
                    getBuildNumber(envVars, buildJobName, build, printStream) : envVars.expand(this.buildNumber);
            String bluemixToken = getIBMCloudToken(this.credentialsId, this.apikey, this.username, this.password,
                    env, build.getParent(), printStream);

            String OTCbrokerUrl = getOTCBrokerServer(this.env);
            Map<String, String> endpoints = getAllEndpoints(OTCbrokerUrl, bluemixToken, toolchainId);;
            String draUrl = endpoints.get(GATE_SERVICE) + DECISION_API_PART;
            draUrl = setGateServiceUrl(draUrl, toolchainId, applicationName, buildNumber, policyName, environmentName);
            String ccUrl = endpoints.get(CONTROL_CENTER);
            String reportUrl =  ccUrl.replace("overview", REPORT_URL_PART) + TOOLCHAIN_PART + toolchainId + "&reportId=";
            String link = ccUrl.replace("overview", CONTROL_CENTER_URL_PART) + TOOLCHAIN_PART + toolchainId;

            JsonObject decisionJson = getDecisionFromDRA(bluemixToken, buildNumber, applicationName, toolchainId,
                    environmentName, draUrl, policyName, printStream);
            if (decisionJson == null) {
                printStream.println(getMessageWithPrefix(NO_DECISION_FOUND));
                return;
            }
            publishDecision(decisionJson, build, reportUrl, link, policyName, willDisrupt, printStream);
        } catch (Exception e) {
            if (e instanceof AbortException) {
                throw new AbortException();
            }
            printStream.println(getMessageWithPrefix(GOT_ERRORS) + e.getMessage());
            return;
        }
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public EvaluateGateImpl getDescriptor() {
        return (EvaluateGateImpl)super.getDescriptor();
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
                return FormValidation.errorWithMarkup(getMessageWithPrefix(TOOLCHAIN_ID_IS_REQUIRED));
            }
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckPolicyName(@QueryParameter String value) {
            if (value == null || value.equals("empty")) {
                return FormValidation.errorWithMarkup(getMessage(FAIL_TO_GET_POLICY_LIST));
            }
            return FormValidation.ok();
        }

        public FormValidation doTestConnection(@AncestorInPath ItemGroup context,
                                               @QueryParameter("credentialsId") final String credentialsId) {
            String environment = "prod";
            String targetAPI = chooseTargetAPI(environment);
            String iamAPI = chooseIAMAPI(environment);
            try {
                if (!credentialsId.equals(preCredentials) || isNullOrEmpty(bluemixToken)) {
                    preCredentials = credentialsId;
                    StandardCredentials credentials = findCredentials(credentialsId, context);
                    bluemixToken = getTokenForFreeStyleJob(credentials, iamAPI, targetAPI, null);
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
         * @param credentialsId
         * @return
         */
        public ListBoxModel doFillPolicyNameItems(@AncestorInPath ItemGroup context,
                                                  @QueryParameter final String toolchainName,
                                                  @QueryParameter final String credentialsId) {
            String environment = "prod";
            String targetAPI = chooseTargetAPI(environment);
            String iamAPI = chooseIAMAPI(environment);
            try {
                // if user changes to a different credential, need to get a new token
                if (!credentialsId.equals(preCredentials) || isNullOrEmpty(bluemixToken)) {
                    StandardCredentials credentials = findCredentials(credentialsId, context);
                    bluemixToken = getTokenForFreeStyleJob(credentials, iamAPI, targetAPI, null);
                    preCredentials = credentialsId;
                }
            } catch (Exception e) {
                return new ListBoxModel();
            }

            return getPolicyList(bluemixToken, toolchainName, environment);
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
    }
}
