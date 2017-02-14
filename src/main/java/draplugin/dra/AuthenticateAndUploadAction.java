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
import org.apache.commons.io.FilenameUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.kohsuke.stapler.*;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.TimeZone;

/**
 * Authenticate with Bluemix and then upload the result file to DRA
 * Created by Xunrong Li on 8/3/16.
 */
public class AuthenticateAndUploadAction extends AbstractDevOpsAction implements SimpleBuildStep, Serializable {

    private final static String API_PART = "/organizations/{org_name}/toolchainids/{toolchain_id}/buildartifacts/{build_artifact}/builds/{build_id}/results_multipart";
    private final static String CONTENT_TYPE_JSON = "application/json";
    private final static String CONTENT_TYPE_XML = "application/xml";

    // form fields from UI
    private final String lifecycleStage;
    private String contents;
    private String additionalLifecycleStage;
    private String additionalContents;
    private String buildNumber;
    private String buildUrl;
    private String applicationName;
    private String buildJobName;
    private String orgName;
    private String toolchainName;
    private String environmentName;
    private String credentialsId;
    private String policyName;
    private boolean willDisrupt;

    private EnvironmentScope testEnv;
    private String envName;
    private boolean isDeploy;

    private PrintStream printStream;
    private File root;
    private static String dlmsUrl;
    private static String draUrl;
    public static String bearerToken;
    public static String preCredentials;

    @DataBoundConstructor
    public AuthenticateAndUploadAction(String lifecycleStage,
                                       String contents,
                                       String applicationName,
                                       String orgName,
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
        this.orgName = orgName;
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
            this.buildUrl = null;
        } else {
            this.buildNumber = additionalBuildInfo.buildNumber;
            this.buildUrl = additionalBuildInfo.buildUrl;
        }

        if (additionalGate == null) {
            this.policyName = null;
            this.willDisrupt = false;
        } else {
            this.policyName = additionalGate.getPolicyName();
            this.willDisrupt = additionalGate.isWillDisrupt();
        }
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

    public String getEnvironmentName() {
        return environmentName;
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

    public String getBuildUrl() {
        return buildUrl;
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
        private String buildUrl;

        @DataBoundConstructor
        public OptionalBuildInfo(String buildNumber, String buildUrl) {
            this.buildNumber = buildNumber;
            this.buildUrl = buildUrl;
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

        // Get the project name and build id from environment
        EnvVars envVars = build.getEnvironment(listener);

        // expand to support env vars
        this.orgName = envVars.expand(this.orgName);
        this.applicationName = envVars.expand(this.applicationName);
        this.contents = envVars.expand(this.contents);
        if (this.isDeploy) {
            this.environmentName = envVars.expand(this.envName);
        }

        if (Util.isNullOrEmpty(Jenkins.getInstance().getRootUrl())) {
            printStream.println(
                    "[DevOps Insight Plugin] The Jenkins global root url is not set. Please set it to used this postbuild Action.  \"Manage Jenkins > Configure System > Jenkins URL\"");
            printStream.println("[DevOps Insight Plugin] Error: Failed to upload Deployment Info.");
            return;
        }

        // verify if user chooses advanced option to input customized DLMS
        String env = getDescriptor().getEnvironment();
        String url = chooseDLMSUrl(env) + API_PART;
        url = url.replace("{org_name}", this.orgName);
        url = url.replace("{toolchain_id}", this.toolchainName);
        url = url.replace("{build_artifact}", URLEncoder.encode(this.applicationName, "UTF-8").replaceAll("\\+", "%20"));

        String buildNumber, buildUrl;
        // if user does not specify the build number
        if (Util.isNullOrEmpty(this.buildNumber)) {
            // locate the build job that triggers current build
            Run triggeredBuild = getTriggeredBuild(build, buildJobName, envVars, printStream);
            if (triggeredBuild == null) {
                //failed to find the build job
                return;
            } else {
                buildNumber = getBuildNumber(triggeredBuild, triggeredBuild.getEnvironment(listener));
                String rootUrl = Jenkins.getInstance().getRootUrl();
                buildUrl = rootUrl + triggeredBuild.getUrl();
            }
        } else {
            buildNumber = envVars.expand(this.buildNumber);
            buildUrl = envVars.expand(this.buildUrl);
        }

        url = url.replace("{build_id}", buildNumber);
        this.dlmsUrl = url;
        String link = chooseControlCenterUrl(env) + "buildverification?orgName=" + this.orgName + "&toolchainId=" + this.toolchainName;

        // get the Bluemix token
        // if it is in prod env, check if already got the token from the "Test connection" or "policy name"
        // if it is in stage1/dev/new, need to get new token
        if (Util.isNullOrEmpty(bearerToken) || this.dlmsUrl.contains("stage1")) {
            String targetAPI = chooseTargetAPI(env);
            try {
                bearerToken = GetBluemixToken(build.getParent(), this.credentialsId, targetAPI);
                printStream.println("[DevOps Insight Plugin] Log in successfully, get the Bluemix token");
            } catch (Exception e) {
                printStream.println("[DevOps Insight Plugin] Username/Password is not correct, fail to authenticate with Bluemix");
                printStream.println("[DevOps Insight Plugin] " + e.toString());
                return;
            }
        }

        // parse the wildcard result files
        try {
            if(!scanAndUpload(build, workspace, contents, lifecycleStage, bearerToken, buildNumber, buildUrl)){
                // if there is any error when scanning and uploading
                return;
            }

            // check to see if we need to upload additional result file
            if (!Util.isNullOrEmpty(additionalContents) && !Util.isNullOrEmpty(additionalLifecycleStage)) {
                if(!scanAndUpload(build, workspace, additionalContents, additionalLifecycleStage,
                        bearerToken, buildNumber, buildUrl)) {
                    return;
                }
            }
        } catch (Exception e) {
            printStream.print("[DevOps Insight Plugin] Got Exception: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        printStream.println("[DevOps Insight Plugin] Go to Control Center (" + link + ") to check your build status");


        // Gate
        // verify if user chooses advanced option to input customized DRA
        if (Util.isNullOrEmpty(policyName)) {
            return;
        }

        this.draUrl = chooseDRAUrl(env);
        String reportUrl = chooseReportUrl(env);

        // get decision response from DRA
        try {
            JsonObject decisionJson = getDecisionFromDRA(bearerToken, buildNumber);
            if (decisionJson == null) {
                printStream.println("[DevOps Insight Plugin] get empty decision");
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

            GatePublisherAction action = new GatePublisherAction(reportUrl + decisionId, decision, this.policyName, build);
            build.addAction(action);

            // console output for a "fail" decision
            if (decision.equals("Failed")) {
                printStream.println("************************************");
                printStream.println("Check DevOps Insights report here -" + reportUrl + decisionId);
                printStream.println("DevOps Insights decision to proceed is:  false");
                printStream.println("************************************");
                if (willDisrupt) {
                    Result result = Result.FAILURE;
                    build.setResult(result);
                }
                return;
            }

            // console output for a "proceed" decision
            printStream.println("************************************");
            printStream.println("Check DevOps Insights report here -" + reportUrl + decisionId);
            printStream.println("DevOps Insights decision to proceed is:  true");
            printStream.println("************************************");
            return;

        } catch (IOException e) {
            printStream.print("[DevOps Insight Plugin] Error: " + e.getMessage());
        }

    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /**
     * Support wildcard for the result file path, scan the path and upload each matching result file to the DLMS
     * @param build - the current build
     * @param bluemixToken - the Bluemix toekn
     * @param buildNumber - the build number
     * @param buildUrl - the url to build job in Jenkins
     * @return false if there is any error when scan and upload the file
     */
    public boolean scanAndUpload(Run build, FilePath workspace, String path, String lifecycleStage, String bluemixToken, String buildNumber, String buildUrl) throws InterruptedException, IOException {
        boolean errorFlag = true;
        FilePath[] filePaths = null;

        if (Util.isNullOrEmpty(path)) {
            // if no result file specified, create dummy result based on the build status
            filePaths = new FilePath[]{createDummyFile(build, workspace)};
        } else {

            // remove "./" prefix of the path if it exists
            if (path.startsWith("./")) {
                path = path.substring(2);
            }

            try {
                filePaths = workspace.list(path);
            } catch(InterruptedException ie) {
                printStream.println("[DevOps Insight Plugin] catching interrupt" + ie.getMessage());
                ie.printStackTrace();
                throw ie;
            } catch (IOException e) {
                printStream.println("[DevOps Insight Plugin] catching act" + e.getMessage());
                e.printStackTrace();
                throw e;
            }
        }

        if (filePaths == null || filePaths.length < 1) {
            printStream.println("[DevOps Insight Plugin] Error: Fail to find the file, please check the path");
            return false;
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
                String timestamp = dateFormat.format(build.getTime());

                // upload the result file to DLMS
                String res = sendFormToDLMS(bluemixToken, fp, lifecycleStage, buildNumber, buildUrl, timestamp);
                if(!printUploadMessage(res, fp.getName())) {
                    errorFlag = false;
                }
            }
        }

        return errorFlag;
    }

    /**
     * create a dummy result file following mocha format for some testing which does not generate test report
     * @param build - current build
     * @param workspace - current workspace, if it runs on slave, then it will be the path on slave
     * @return simple test result file
     */
    private FilePath createDummyFile(Run build, FilePath workspace) throws InterruptedException {

        // if user did not specify the result file location, upload the dummy json file
        Gson gson = new Gson();

        //set the passes and failures based on the test status
        int passes, failures;
        if (!build.getResult().equals(Result.SUCCESS)) {
            passes = 0;
            failures = 1;
        } else {
            passes = 1;
            failures = 0;
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
            printStream.println("[DevOps Insight Plugin] Failed to create dummy file in current workspace, Exception: " + e.getMessage());
        }

        return null;
    }

    /**
     * print out the response message from DLMS to the console log
     * @param response - response from DLMS
     * @param fileName - uploaded filename
     * @return true if upload succeed, otherwise return false
     */
    private boolean printUploadMessage(String response, String fileName) {
        if (response.contains("Error")) {
            printStream.println("[DevOps Insight Plugin] " + response);
        } else if (response.contains("200")) {
            printStream.println("[DevOps Insight Plugin] Upload [" + fileName + "] SUCCESSFUL");
            return true;
        } else {
            printStream.println("[DevOps Insight Plugin]" + response + ", Upload [" + fileName + "] FAILED");
        }

        return false;
    }

    /**
     * * Send POST request to DLMS back end with the result file
     * @param bluemixToken - the Bluemix token
     * @param contents - the result file
     * @param buildNumber - the build number of the build job in Jenkins
     * @param buildUrl -  the build url of the build job in Jenkins
     * @param timestamp
     * @return - response/error message from DLMS
     */
    public String sendFormToDLMS(String bluemixToken, FilePath contents, String lifecycleStage, String buildNumber, String buildUrl, String timestamp) throws IOException {

        // create http client and post method
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost postMethod = new HttpPost(this.dlmsUrl);

        // build up multi-part forms
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        if (contents != null) {

            File file = new File(root, contents.getName());
            FileBody fileBody = new FileBody(file);
            builder.addPart("contents", fileBody);


            builder.addTextBody("test_artifact", file.getName());
            if (this.isDeploy) {
                builder.addTextBody("environment_name", environmentName);
            }
            builder.addTextBody("lifecycle_stage", lifecycleStage);
            builder.addTextBody("url", buildUrl);
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
                default:
                    return "Error: " + contents.getName() + " is an invalid result file type";
            }

            builder.addTextBody("contents_type", contentType);
            HttpEntity entity = builder.build();
            postMethod.setEntity(entity);
            postMethod.setHeader("Authorization", bluemixToken);
        } else {
            return "Error: File is null";
        }


        CloseableHttpResponse response = null;
        try {
            response = httpClient.execute(postMethod);
            // parse the response json body to display detailed info
            String resStr = EntityUtils.toString(response.getEntity());
            JsonParser parser = new JsonParser();
            JsonElement element =  parser.parse(resStr);

            if (!element.isJsonObject()) {
                // 401 Forbidden
                return "Error: Upload is Forbidden, please check your org name. Error message: " + element.toString();
            } else {
                JsonObject resJson = element.getAsJsonObject();
                if (resJson != null && resJson.has("status")) {
                    return String.valueOf(response.getStatusLine()) + "\n" + resJson.get("status");
                } else {
                    // other cases
                    return String.valueOf(response.getStatusLine());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }


    /**
     * Send a request to DRA backend to get a decision
     * @param buildId - build ID, get from Jenkins environment
     * @return - the response decision Json file
     */
    private JsonObject getDecisionFromDRA(String bluemixToken, String buildId) throws IOException {
        // create http client and post method
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost postMethod = new HttpPost(this.draUrl + orgName);

        postMethod.setHeader("Authorization", bluemixToken);
        postMethod.setHeader("Content-Type", CONTENT_TYPE_JSON);

        // build up the json body
        JsonObject json = new JsonObject();
        // Before removing project name from DRA, using orgName for now
        json.addProperty("project_name", orgName);
        json.addProperty("runtime_name", applicationName);
        json.addProperty("build_id", buildId);
        json.addProperty("criteria_name", policyName);
        if (this.isDeploy) {
            json.addProperty("environment_name", environmentName);
        }

        StringEntity data = new StringEntity(json.toString());
        postMethod.setEntity(data);
        CloseableHttpResponse response = httpClient.execute(postMethod);
        String resStr = EntityUtils.toString(response.getEntity());

        try {
            if (response.getStatusLine().toString().contains("200")) {
                // get 200 response
                JsonParser parser = new JsonParser();
                JsonElement element = parser.parse(resStr);
                JsonObject resJson = element.getAsJsonObject();
                printStream.println("[DevOps Insight Plugin] Get decision successfully");
                return resJson;
            } else {
                // if gets error status
                printStream.println("[DevOps Insight Plugin] Error: Failed to get a decision, response status " + response.getStatusLine());

                JsonParser parser = new JsonParser();
                JsonElement element = parser.parse(resStr);
                JsonObject resJson = element.getAsJsonObject();
                if (resJson != null && resJson.has("message")) {
                    printStream.println("[DevOps Insight Plugin] Reason: " + resJson.get("message"));
                }
            }
        } catch (JsonSyntaxException e) {
            printStream.println("[DevOps Insight Plugin] Invalid Json response, response: " + resStr);
        }

        return null;

    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public AuthenticateAndUploadAction.AuthenticateAndUploadActionImpl getDescriptor() {
        return (AuthenticateAndUploadAction.AuthenticateAndUploadActionImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link AuthenticateAndUploadAction}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/draplugin/dra/AuthenticateAndUploadAction/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class AuthenticateAndUploadActionImpl extends BuildStepDescriptor<Publisher> {
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
        public AuthenticateAndUploadActionImpl() {
            super(AuthenticateAndUploadAction.class);
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

        public FormValidation doCheckOrgName(@QueryParameter String value)
                throws IOException, ServletException {
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
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckEnvironmentName(@QueryParameter String value)
                throws IOException, ServletException {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckPolicyName(@QueryParameter String value) {

            if (value == null || value.equals("empty")) {
                return FormValidation.errorWithMarkup("Fail to get the policies, please check your username/password or org name and make sure you have created policies in this org");
            }
            return FormValidation.validateRequired(value);
        }

        public FormValidation doTestConnection(@AncestorInPath ItemGroup context,
                                               @QueryParameter("credentialsId") final String credentialsId) {
            String targetAPI = chooseTargetAPI(environment);
            if (!credentialsId.equals(preCredentials) || Util.isNullOrEmpty(bearerToken)) {
                preCredentials = credentialsId;
                try {
                    String bluemixToken = GetBluemixToken(context, credentialsId, targetAPI);
                    if (Util.isNullOrEmpty(bluemixToken)) {
                        bearerToken = bluemixToken;
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
         * This method is called to populate the policy list on the Jenkins config page.
         * @param context
         * @param orgName
         * @param credentialsId
         * @return
         */
        public ListBoxModel doFillPolicyNameItems(@AncestorInPath ItemGroup context,
                                                  @RelativePath("..") @QueryParameter final String orgName,
                                                  @RelativePath("..") @QueryParameter final String toolchainName,
                                                  @RelativePath("..") @QueryParameter final String credentialsId) {
            String targetAPI = chooseTargetAPI(environment);
            try {
                // if user changes to a different credential, need to get a new token
                if (!credentialsId.equals(preCredentials) || Util.isNullOrEmpty(bearerToken)) {
                    bearerToken = GetBluemixToken(context, credentialsId, targetAPI);
                    preCredentials = credentialsId;
                }
            } catch (Exception e) {
                return new ListBoxModel();
            }

            return getPolicyList(bearerToken, orgName, toolchainName, environment);
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
                if (!credentialsId.equals(preCredentials) || Util.isNullOrEmpty(bearerToken)) {
                    bearerToken = GetBluemixToken(context, credentialsId, targetAPI);
                    preCredentials = credentialsId;
                }
            } catch (Exception e) {
                return new ListBoxModel();
            }
            ListBoxModel toolChainListBox = getToolchainList(bearerToken, orgName, environment);
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

            model.add("Unit Test", "unittest");
            model.add("Functional Verification Test", "fvt");
            model.add("Code Coverage", "code");
            return model;
        }

        /**
         * Required Method
         * @return The text to be displayed when selecting your build in the project
         */
        public String getDisplayName() {
            return "Publish test result to DevOps Insights";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            environment = formData.getString("environment");
            save();
            return super.configure(req,formData);
        }

        public String getEnvironment() {
            return environment;
        }
    }
}
