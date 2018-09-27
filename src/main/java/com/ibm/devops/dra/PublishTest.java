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
import org.apache.commons.io.FilenameUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static com.ibm.devops.dra.UIMessages.*;
import static com.ibm.devops.dra.Util.*;

/**
 * Authenticate with Bluemix and then upload the result file to DRA
 */
public class PublishTest extends AbstractDevOpsAction implements SimpleBuildStep {

    private final static String DLMS_API_PART = "/v3/toolchainids/{toolchain_id}/buildartifacts/{build_artifact}/builds/{build_id}/results_multipart";
    private static final String CONTROL_CENTER_URL_PART = "deploymentrisk";
    private final static String TOOLCHAIN_PART = "&toolchainId=";
    private static final String REPORT_URL_PART = "decisionreport?toolchainId=";
    private final static String CONTENT_TYPE_JSON = "application/json";
    private final static String CONTENT_TYPE_XML = "application/xml";
    private final static String CONTENT_TYPE_LCOV = "text/plain";
    private static final String DECISION_API_PART = "/api/v5/toolchainids/{toolchain_id}/buildartifacts/{build_artifact}/builds/{build_id}/policies/{policy_name}/decisions";


    // form fields from UI
    private final String lifecycleStage;
    private String contents;
    private String additionalLifecycleStage;
    private String additionalContents;
    private String buildNumber;
    private String applicationName;
    private String buildJobName;
    private String toolchainName;
    private String credentialsId;
    private String policyName;
    private boolean willDisrupt;

    private EnvironmentScope testEnv;
    private String envName;
    private boolean isDeploy;

    private PrintStream printStream;
    private File root;
    private static String bluemixToken;
    private static String preCredentials;

    //fields to support jenkins pipeline
    private String username;
    private String password;
    private String apikey;
    private String env;

    @DataBoundConstructor
    public PublishTest(String lifecycleStage,
                       String contents,
                       String applicationName,
                       String toolchainName,
                       String buildJobName,
                       String credentialsId,
                       OptionalUploadBlock additionalUpload,
                       OptionalBuildInfo additionalBuildInfo,
                       OptionalGate additionalGate,
                       EnvironmentScope testEnv) {
        this.lifecycleStage = lifecycleStage;
        this.contents = contents;
        this.credentialsId = credentialsId;
        this.applicationName = applicationName;
        this.toolchainName = toolchainName;
        this.buildJobName = buildJobName;
        this.testEnv = testEnv;
        this.envName = testEnv.getEnvName();
        this.isDeploy = testEnv.isDeploy();

        if (additionalUpload == null) {
            this.additionalContents = null;
            this.additionalLifecycleStage = null;
        } else {
            this.additionalLifecycleStage = additionalUpload.additionalLifecycleStage;
            this.additionalContents = additionalUpload.additionalContents;
        }

        if (additionalBuildInfo == null) {
            this.buildNumber = null;
        } else {
            this.buildNumber = additionalBuildInfo.buildNumber;
        }

        if (additionalGate == null) {
            this.policyName = null;
            this.willDisrupt = false;
        } else {
            this.policyName = additionalGate.getPolicyName();
            this.willDisrupt = additionalGate.isWillDisrupt();
        }
    }

    public PublishTest(HashMap<String, String> envVarsMap, HashMap<String, String> paramsMap) {
        this.lifecycleStage = paramsMap.get("type");
        this.contents = paramsMap.get("fileLocation");

        this.applicationName = envVarsMap.get(APP_NAME);
        this.toolchainName = envVarsMap.get(TOOLCHAIN_ID);
        this.env = envVarsMap.get(ENV);

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

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getLifecycleStage() {
        return lifecycleStage;
    }

    public String getContents() {
        return contents;
    }

    public String getAdditionalLifecycleStage() {
        return additionalLifecycleStage;
    }

    public String getAdditionalContents() {
        return additionalContents;
    }

    public String getBuildNumber() {
        return buildNumber;
    }

    public String getPolicyName() {
        return policyName;
    }

    public boolean isWillDisrupt() {
        return willDisrupt;
    }

    public EnvironmentScope getTestEnv() {
        return testEnv;
    }

    public String getEnvName() {
        return envName;
    }

    public void setEnvName(String envName) {
        this.envName = envName;
    }

    public boolean isDeploy() {
        return isDeploy;
    }

    /**
     * Sub class for Optional Upload Block
     */
    public static class OptionalUploadBlock {
        private String additionalLifecycleStage;
        private String additionalContents;

        @DataBoundConstructor
        public OptionalUploadBlock(String additionalLifecycleStage, String additionalContents) {
            this.additionalLifecycleStage = additionalLifecycleStage;
            this.additionalContents = additionalContents;
        }
    }

    public static class OptionalBuildInfo {
        private String buildNumber;

        @DataBoundConstructor
        public OptionalBuildInfo(String buildNumber) {
            this.buildNumber = buildNumber;
        }
    }

    public static class OptionalGate {
        private String policyName;
        private boolean willDisrupt;

        @DataBoundConstructor
        public OptionalGate(String policyName, boolean willDisrupt) {
            this.policyName = policyName;
            setWillDisrupt(willDisrupt);
        }

        public String getPolicyName() {
            return policyName;
        }

        public boolean isWillDisrupt() {
            return willDisrupt;
        }

        @DataBoundSetter
        public void setWillDisrupt(boolean willDisrupt) {
            this.willDisrupt = willDisrupt;
        }
    }

    @Override
    public void perform(@Nonnull Run build, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        printStream = listener.getLogger();
        printPluginVersion(this.getClass().getClassLoader(), printStream);

        // create root dir for storing test result
        root = new File(build.getRootDir(), "DRA_TestResults");
        EnvVars envVars = build.getEnvironment(listener);
        String env = isNullOrEmpty(this.env) ? DEFAULT_ENV : this.env;

        try {
            // Get the project name and build id from environment and expand the vars
            String applicationName = expandVariable(this.applicationName, envVars, true);
            String toolchainId = expandVariable(this.toolchainName, envVars, true);
            String contents = expandVariable(this.contents, envVars, true);
            String environmentName = "";
            if (this.isDeploy || !isNullOrEmpty(this.envName)) {
                environmentName = envVars.expand(this.envName);
            }

            // get IBM cloud environment and token
            String buildNumber = isNullOrEmpty(this.buildNumber) ?
                    getBuildNumber(envVars, buildJobName, build, printStream) : envVars.expand(this.buildNumber);
            String bluemixToken = getIBMCloudToken(this.credentialsId, this.apikey, this.username, this.password,
                    env, build.getParent(), printStream);

            String OTCbrokerUrl = getOTCBrokerServer(env);
            Map<String, String> endpoints = getAllEndpoints(OTCbrokerUrl, bluemixToken, toolchainId);;
            String dlmsUrl = endpoints.get(DLMS) + DLMS_API_PART;
            dlmsUrl = setDLMSUrl(dlmsUrl, toolchainId, applicationName, buildNumber);
            String ccUrl = endpoints.get(CONTROL_CENTER);
            String link = ccUrl.replace("overview", CONTROL_CENTER_URL_PART) + TOOLCHAIN_PART + toolchainId;
            scanAndUpload(build, workspace, contents, lifecycleStage, toolchainId, bluemixToken, environmentName, dlmsUrl);

            // check to see if we need to upload additional result file
            if (!isNullOrEmpty(additionalContents) && !isNullOrEmpty(additionalLifecycleStage)) {
                String additionalContents = envVars.expand(this.additionalContents);
                String additionalLifecycleStage = envVars.expand(this.additionalLifecycleStage);
                scanAndUpload(build, workspace, additionalContents, additionalLifecycleStage, toolchainId, bluemixToken, environmentName, dlmsUrl);
            }
            printStream.println(getMessageWithVarAndPrefix(CHECK_TEST_RESULT, link));
            // verify if user chooses advanced option to input customized DRA, just for freestyle job
            if (isNullOrEmpty(policyName)) {
                return;
            }

            String policyName = envVars.expand(this.policyName);
            String draUrl = endpoints.get(GATE_SERVICE) + DECISION_API_PART;
            draUrl = setGateServiceUrl(draUrl, toolchainId, applicationName, buildNumber, policyName, environmentName);
            String reportUrl =  ccUrl.replace("overview", REPORT_URL_PART) + TOOLCHAIN_PART + toolchainId + "&reportId=";
            JsonObject decisionJson = getDecisionFromDRA(bluemixToken, toolchainId,
                    draUrl, printStream, getDescriptor().isDebugMode());
            if (decisionJson == null) {
                printStream.println(getMessageWithPrefix(NO_DECISION_FOUND));
                return;
            }
            publishDecision(decisionJson, build, reportUrl, link, policyName, willDisrupt, printStream);
        } catch (Exception e) {
            printStream.println(getMessageWithPrefix(GOT_ERRORS) + e.getMessage());
            return;
        }
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /**
     * Support wildcard for the result file path, scan the path and upload each matching result file to the DLMS
     * @param build
     * @param workspace
     * @param path
     * @param lifecycleStage
     * @param bluemixToken
     * @param environmentName
     * @param dlmsUrl
     * @throws Exception
     */
    public void scanAndUpload(Run build, FilePath workspace, String path, String lifecycleStage, String toolchainId, String bluemixToken, String environmentName, String dlmsUrl) throws Exception {
        FilePath[] filePaths = null;

        if (isNullOrEmpty(path)) {
            // if no result file specified, create dummy result based on the build status
            filePaths = new FilePath[]{createDummyFile(build, workspace)};
        } else {
            // remove "./" prefix of the path if it exists
            if (path.startsWith("./")) {
                path = path.substring(2);
            }
            filePaths = workspace.list(path);
        }

        if (filePaths == null || filePaths.length < 1) {
            throw new Exception(getMessageWithVar(FAIL_TO_FIND_FILE));
        } else {
            for (FilePath fp : filePaths) {
                // make sure the file path is for file, and copy to the master build folder
                if (!fp.isDirectory()) {
                    FilePath resultFileLocation = new FilePath(new File(root, fp.getName()));
                    fp.copyTo(resultFileLocation);
                }

                //get timestamp
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                TimeZone utc = TimeZone.getTimeZone("UTC");
                dateFormat.setTimeZone(utc);
                String timestamp = dateFormat.format(System.currentTimeMillis());
                String jobUrl;
                if (checkRootUrl(printStream)) {
                    jobUrl = Jenkins.getInstance().getRootUrl() + build.getUrl();
                } else {
                    jobUrl = build.getAbsoluteUrl();
                }

                // upload the result file to DLMS
                sendFormToDLMS(bluemixToken, fp, lifecycleStage, toolchainId, jobUrl, timestamp, environmentName, dlmsUrl);
            }
        }
    }

    /**
     * create a dummy result file following mocha format for some testing which does not generate test report
     * @param build - current build
     * @param workspace - current workspace, if it runs on slave, then it will be the path on slave
     * @return simple test result file
     */
    private FilePath createDummyFile(Run build, FilePath workspace) throws Exception {
        Gson gson = new Gson();
        //set the passes and failures based on the test status
        int passes, failures;
        Result result = build.getResult();
        if (result != null) {
            if (!result.equals(Result.SUCCESS)) {
                passes = 0;
                failures = 1;
            } else {
                passes = 1;
                failures = 0;
            }
        } else {
            throw new Exception(getMessage(FAIL_TO_GET_JOB_RESULT));
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        TimeZone utc = TimeZone.getTimeZone("UTC");
        dateFormat.setTimeZone(utc);
        String start = dateFormat.format(build.getStartTimeInMillis());
        long duration = build.getDuration();
        String end = dateFormat.format(build.getStartTimeInMillis() + duration);

        TestResultModel.Stats stats = new TestResultModel.Stats(1, 1, passes, 0, failures, start, end, duration);
        TestResultModel.Test test = new TestResultModel.Test("unknown test", "unknown test", duration, 0, null);
        TestResultModel.Test[] tests = {test};
        String[] emptyArray = {};
        TestResultModel testResultModel = new TestResultModel(stats, tests, emptyArray, emptyArray, emptyArray);

        // create new dummy file
        try {
            FilePath filePath = workspace.child("simpleTest.json");
            filePath.write(gson.toJson(testResultModel), "UTF8");
            return filePath;
        } catch (IOException e) {
            e.printStackTrace();
            printStream.println(getMessageWithVar(FAIL_TO_CREATE_FILE, e.getMessage()));
        }
        return null;
    }

    /**
     * Send POST request to DLMS back end with the result file
     * @param bluemixToken
     * @param contents
     * @param lifecycleStage
     * @param jobUrl
     * @param timestamp
     * @param environmentName
     * @param dlmsUrl
     * @throws Exception
     */
    public void sendFormToDLMS(String bluemixToken, FilePath contents, String lifecycleStage, String toolchainId, String jobUrl, String timestamp, String environmentName, String dlmsUrl) throws Exception {
        // create http client and post method
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost postMethod = new HttpPost(dlmsUrl);
        postMethod = addProxyInformation(postMethod);
        // build up multi-part forms
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        if (contents == null) {
            throw new Exception(getMessage(FAIL_TO_FIND_FILE));
        } else {
            File file = new File(root, contents.getName());
            FileBody fileBody = new FileBody(file);
            builder.addPart("contents", fileBody);
            builder.addTextBody("test_artifact", file.getName());
            if (this.isDeploy) {
                builder.addTextBody("environment_name", environmentName);
            }

            builder.addTextBody("lifecycle_stage", lifecycleStage);
            builder.addTextBody("url", jobUrl);
            builder.addTextBody("timestamp", timestamp);
            String fileExt = FilenameUtils.getExtension(contents.getName());
            String contentType;
            switch (fileExt) {
                case "json":
                    contentType = CONTENT_TYPE_JSON;
                    break;
                case "xml":
                    contentType = CONTENT_TYPE_XML;
                    break;
                case "info":
                    contentType = CONTENT_TYPE_LCOV;
                    break;
                default:
                    throw new Exception(getMessageWithVar(UNSUPPORTED_RESULT_FILE, contents.toString()));
            }

            builder.addTextBody("contents_type", contentType);
            HttpEntity entity = builder.build();
            postMethod.setEntity(entity);
            postMethod.setHeader("Authorization", bluemixToken);
        }

        CloseableHttpResponse response = null;
        try {
            response = httpClient.execute(postMethod);
            // parse the response json body to display detailed info
            String resStr = EntityUtils.toString(response.getEntity());
            int statusCode = response.getStatusLine().getStatusCode();

            if (getDescriptor().isDebugMode()) {
                printDebugLog(printStream, postMethod, response.getStatusLine().toString(), resStr);
            }

            if (statusCode == 200) {
                printStream.println(getMessageWithVarAndPrefix(UPLOAD_FILE_SUCCESS, contents.toString()));
            } else if (statusCode == 401 || statusCode == 403) {
                // if gets 401 or 403, it returns html
                throw new Exception(getMessageWithVar(FAIL_TO_UPLOAD_DATA, String.valueOf(statusCode), toolchainId));
            } else {
                JsonParser parser = new JsonParser();
                JsonElement element = parser.parse(resStr);
                JsonObject resJson = element.getAsJsonObject();

                if (resJson != null && resJson.has("message")) {
                    throw new Exception(getMessageWithVar(FAIL_TO_UPLOAD_DATA_WITH_REASON, String.valueOf(statusCode), resJson.get("message").getAsString()));
                } else {
                    throw new Exception(getMessageWithVar(FAIL_TO_UPLOAD_DATA_WITH_REASON, String.valueOf(statusCode), resJson == null ? getMessage(FAIL_TO_GET_RESPONSE) : resJson.toString()));
                }
            }
        } catch (IOException e) {
            throw new Exception(e.getMessage());
        }

    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public PublishTestImpl getDescriptor() {
        return (PublishTestImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link PublishTest}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/com/ibm/devops/dra/PublishTest/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class PublishTestImpl extends BuildStepDescriptor<Publisher> {
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
        public PublishTestImpl() {
            super(PublishTest.class);
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

        public FormValidation doCheckPolicyName(@QueryParameter String value) {
            if (value == null || value.equals("empty")) {
                // Todo: optimize the message
                return FormValidation.errorWithMarkup("Fail to get the policies, please check your username/password or org name and make sure you have created policies for this org and toolchain.");
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
         * This method is called to populate the policy list on the Jenkins config page.
         * @param context
         * @param credentialsId
         * @return
         */
        public ListBoxModel doFillPolicyNameItems(@AncestorInPath ItemGroup context,
                                                  @RelativePath("..") @QueryParameter final String toolchainName,
                                                  @RelativePath("..") @QueryParameter final String credentialsId) {
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
                if (isDebugMode()) {
                    LOGGER.info("Fail to get the Bluemix token");
                    e.printStackTrace();
                }
                return new ListBoxModel();
            }

            return getPolicyList(bluemixToken, toolchainName, environment, isDebugMode());
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

        public ListBoxModel doFillLifecycleStageItems(@QueryParameter("lifecycleStage") final String selection) {
            return fillTestType();
        }

        public ListBoxModel doFillAdditionalLifecycleStageItems(@QueryParameter("additionalLifecycleStage") final String selection) {
            return fillTestType();
        }

        /**
         * fill the dropdown list of rule type
         * @return the dropdown list model
         */
        public ListBoxModel fillTestType() {
            ListBoxModel model = new ListBoxModel();
            model.add(getMessage(UNIT_TEST), "unittest");
            model.add(getMessage(FVT), "fvt");
            model.add(getMessage(CODE_COVERAGE), "code");
            model.add(getMessage(STATIC_SCAN), "staticsecurityscan");
            model.add(getMessage(DYNAMIC_SCAN), "dynamicsecurityscan");
            return model;
        }

        /**
         * Required Method
         * @return The text to be displayed when selecting your build in the project
         */
        public String getDisplayName() {
            return "Publish test result to IBM Cloud DevOps";
        }

        public boolean isDebugMode() {
            return Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).isDebugMode();
        }
    }
}
