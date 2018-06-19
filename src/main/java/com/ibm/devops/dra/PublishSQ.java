/*
 <notice>

 Copyright 2017 IBM Corporation

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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.ItemGroup;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
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
import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TimeZone;

import static com.ibm.devops.dra.UIMessages.*;
import static com.ibm.devops.dra.Util.*;

/**
 * Authenticate with Bluemix and then upload the result file to DRA
 */
public class PublishSQ extends AbstractDevOpsAction implements SimpleBuildStep {

    private final static String API_PART = "/toolchainids/{toolchain_id}/buildartifacts/{build_artifact}/builds/{build_id}/results";
    private final static String CONTENT_TYPE_JSON = "application/json";
    private final static String SQ_QUALITY_API_PART = "/api/qualitygates/project_status?projectKey=";
    private final static String SQ_RATING_API_PART = "/api/measures/component?metricKeys=reliability_rating,security_rating,sqale_rating&componentKey=";
    private final static String SQ_ISSUE_API_PART = "/api/issues/search?statuses=OPEN&projectKeys=";

    // form fields from UI
    private String applicationName;
    private String buildJobName;
    private String toolchainName;
    private String credentialsId;
    private String buildNumber;

    private String SQProjectKey;
    private String SQHostName;
    private String SQAuthToken;
    private String username;
    private String password;
    private String apikey;

    private static PrintStream printStream;
    private static String bluemixToken;
    private static String preCredentials;

    @DataBoundConstructor
    public PublishSQ(String credentialsId,
                     String toolchainName,
                     String buildJobName,
                     String applicationName,
                     String SQHostName,
                     String SQAuthToken,
                     String SQProjectKey,
                     OptionalBuildInfo additionalBuildInfo) {
        this.credentialsId = credentialsId;
        this.toolchainName = toolchainName;
        this.buildJobName = buildJobName;
        this.applicationName = applicationName;
        this.SQHostName = SQHostName;
        this.SQAuthToken = SQAuthToken;
        this.SQProjectKey = SQProjectKey;

        if (additionalBuildInfo == null) {
            this.buildNumber = null;
        } else {
            this.buildNumber = additionalBuildInfo.buildNumber;
        }
    }

    public PublishSQ(HashMap<String, String> envVarsMap, HashMap<String, String> paramsMap) {
        this.SQProjectKey = paramsMap.get("SQProjectKey");
        this.SQHostName = paramsMap.get("SQHostURL");
        this.SQAuthToken = paramsMap.get("SQAuthToken");
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
    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getApplicationName() {
        return applicationName;
    }

    public String getToolchainName() {
        return toolchainName;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getBuildJobName() {
        return buildJobName;
    }

    public String getSQHostName() {
        return this.SQHostName;
    }

    public String getSQAuthToken() {
        return this.SQAuthToken;
    }

    public String getSQProjectKey() {
        return this.SQProjectKey;
    }

    public String getBuildNumber() {
        return buildNumber;
    }

    public static class OptionalBuildInfo {
        private String buildNumber;

        @DataBoundConstructor
        public OptionalBuildInfo(String buildNumber) {
            this.buildNumber = buildNumber;
        }
    }

    @Override
    public void perform(@Nonnull Run build, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        printStream = listener.getLogger();
        printPluginVersion(this.getClass().getClassLoader(), printStream);
        EnvVars envVars = build.getEnvironment(listener);
        String env = getDescriptor().getEnvironment();

        try {
            // Get the project name and build id from environment
            String applicationName = expandVariable(this.applicationName, envVars, true);
            String toolchainId = expandVariable(this.toolchainName, envVars, true);
            String SQHostName = expandVariable(this.SQHostName, envVars, true);
            String SQAuthToken = expandVariable(this.SQAuthToken, envVars, true);
            String SQProjectKey = expandVariable(this.SQProjectKey, envVars, true);

            // get IBM cloud environment and token
            String buildNumber = isNullOrEmpty(this.buildNumber) ?
                    getBuildNumber(envVars, buildJobName, build, printStream) : envVars.expand(this.buildNumber);
            String bluemixToken = getIBMCloudToken(this.credentialsId, this.apikey, this.username, this.password,
                    env, build.getParent(), printStream);

            String baseUrl = chooseDLMSUrl(env) + API_PART;
            String dlmsUrl = setDLMSUrl(baseUrl, toolchainId, applicationName, buildNumber);
            JsonObject payload = createDLMSPayload(SQHostName, SQProjectKey, SQAuthToken);
            JsonArray urls = createPayloadUrls(SQHostName, SQProjectKey);
            sendPayloadToDLMS(bluemixToken, payload, urls, toolchainId, dlmsUrl);
        } catch (Exception e) {
            printStream.println(getMessageWithPrefix(GOT_ERRORS) + e.getMessage());
            return;
        }
    }

    /**
     * Constructs the urls that should be sent with the DLMS message
     *
     * @param SQHostname hostname of the SQ instance
     * @param SQKey project key of the SQ instance
     * @return an array of URLs that should be sent to dlms along with the payload
     */
    public JsonArray createPayloadUrls(String SQHostname, String SQKey) {
        JsonArray urls = new JsonArray();
        String url = SQHostname + "/dashboard/index/" + SQKey;
        urls.add(url);
        return urls;
    }

    /**
     * Combines all SQ information into one gson that can be sent to DLMS
     * @param hostname
     * @param projectKey
     * @param authToken
     * @return
     * @throws Exception
     */
    public JsonObject createDLMSPayload(String hostname, String projectKey, String authToken) throws Exception {
        Map<String, String> headers = new HashMap<String, String>();
        JsonObject payload = new JsonObject();
        // ':' needs to be added so the SQ api knows an auth token is being used
        String SQAuthToken = DatatypeConverter.printBase64Binary((authToken + ":").getBytes("UTF-8"));
        headers.put("Authorization", "Basic " + SQAuthToken);

        JsonObject SQqualityGate = sendGETRequest(hostname + SQ_QUALITY_API_PART + projectKey, hostname, projectKey, authToken);
        payload.add("qualityGate", SQqualityGate.get("projectStatus"));
        printStream.println(getMessageWithPrefix(QUERY_SQ_QUALITY_SUCCESS));

        JsonObject SQissues = getFullResponse(hostname + SQ_ISSUE_API_PART + projectKey, hostname, projectKey, authToken);
        if (SQissues == null) {
            printStream.println(getMessageWithPrefix(FAIL_TO_QUERY_SQ_ISSUE));
            JsonObject error = new JsonObject();
            error.addProperty("errorCode", "overLimit");
            error.addProperty("message", getMessage(SQ_ISSUE_FAILURE_MESSAGE));
            payload.add("error", error);
            payload.addProperty("issues", "[]");
        } else
            printStream.println(getMessageWithPrefix(QUERY_SQ_ISSUE_SUCCESS));

        JsonObject SQratings = sendGETRequest(hostname + SQ_RATING_API_PART + projectKey, hostname, projectKey, authToken);
        JsonParser parser = new JsonParser();
        JsonObject component = (JsonObject)parser.parse(SQratings.get("component").toString());
        payload.add("ratings", component.get("measures"));
        printStream.println(getMessageWithPrefix(QUERY_SQ_METRIC_SUCCESS));

        return payload;
    }

    /**
     * get data from the SQ server
     * @param url
     * @param hostname
     * @param projectKey
     * @param authToken
     * @return
     * @throws Exception
     */
    private JsonObject sendGETRequest(String url, String hostname, String projectKey, String authToken) throws Exception {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet getMethod = new HttpGet(url);
        getMethod = addProxyInformation(getMethod);
        String SQAuthToken = DatatypeConverter.printBase64Binary((authToken + ":").getBytes("UTF-8"));
        getMethod.setHeader("Authorization", "Basic " + SQAuthToken);

        CloseableHttpResponse response = httpClient.execute(getMethod);
        int statusCode = response.getStatusLine().getStatusCode();
        String resStr = EntityUtils.toString(response.getEntity());
        JsonParser parser = new JsonParser();

        if (statusCode == 200) {
            JsonElement element = parser.parse(resStr);
            JsonObject resJson = element.getAsJsonObject();
            return resJson;
        } else if (statusCode == 401){
            throw new Exception(getMessage(FAIL_TO_AUTH_SQ) + response.getStatusLine());
        } else if (statusCode == 400){
            printStream.println(getMessageWithPrefix(SQ_ISSUE_OVER_LIMIT));
            return null;
        } else if (statusCode == 404) {
            throw new Exception(getMessageWithVar(SO_PROJECT_KEY_NOT_FOUND, projectKey, hostname));
        } else {
            throw new Exception(getMessageWithVar(SQ_OTHER_EXCEPTION, String.valueOf(statusCode), resStr));
        }
    }
    /**
     * Get all the pages of response, a call to the api returns a single page response, this function will iterate thru all the
     * pages to get a full response. Gets 250 records per page at a time.
     * @param url
     * @param hostname
     * @param projectKey
     * @param authToken
     * @return
     */
    private JsonObject getFullResponse(String url, String hostname, String projectKey, String authToken)  {
    	JsonArray intermediateArray = new JsonArray();
    	int recordCount = 0;
    	int page = 1;
    	try {
            do {
                String uurl = url + "&ps=250&p=" + page;
                JsonObject partResponse = sendGETRequest(uurl, hostname, projectKey, authToken);
                if (partResponse == null) {
                    return null;
                }
                JsonArray issues = partResponse.getAsJsonArray("issues");
                intermediateArray.addAll(issues);
                recordCount = issues.size();
                page += 1;
            } while(recordCount > 0);

            // reduce the amount of data uploaded to DevOps Insights to only what is essential
            JsonArray finalArray = new JsonArray();
            Iterator<JsonElement> iter = intermediateArray.iterator();
            while(iter.hasNext()) {
                    JsonObject aObj = (JsonObject)iter.next();
                    JsonObject newObj = new JsonObject();
                    newObj.add("severity", aObj.get("severity"));
                    newObj.add("project", aObj.get("project"));
                    newObj.add("component", aObj.get("component"));
                    newObj.add("type", aObj.get("type"));
                    finalArray.add(newObj);
            }

            JsonObject payload = new JsonObject();
            payload.add("issues", finalArray);
            return payload;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Sends POST method to DLMS to upload SQ results
     *F
     * @param bluemixToken the bluemix auth header that allows us to talk to dlms
     * @param payload the content part of the payload to send to dlms
     * @param urls a json array that holds the urls for a payload
     * @return boolean based on if the request was successful or not
     */
    private void sendPayloadToDLMS(String bluemixToken, JsonObject payload, JsonArray urls, String toolchainName, String dlmsUrl) throws Exception {
        String resStr = "";
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost postMethod = new HttpPost(dlmsUrl);
        postMethod = addProxyInformation(postMethod);
        postMethod.setHeader("Authorization", bluemixToken);
        postMethod.setHeader("Content-Type", CONTENT_TYPE_JSON);

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        TimeZone utc = TimeZone.getTimeZone("UTC");
        dateFormat.setTimeZone(utc);
        String timestamp = dateFormat.format(System.currentTimeMillis());

        JsonObject body = new JsonObject();
        body.addProperty("contents", DatatypeConverter.printBase64Binary(payload.toString().getBytes("UTF-8")));
        body.addProperty("contents_type", CONTENT_TYPE_JSON);
        body.addProperty("timestamp", timestamp);
        body.addProperty("tool_name", "sonarqube");
        body.addProperty("lifecycle_stage", "sonarqube");
        body.add("url", urls);
        StringEntity data = new StringEntity(body.toString(), "UTF-8");
        postMethod.setEntity(data);
        CloseableHttpResponse response = httpClient.execute(postMethod);
        resStr = EntityUtils.toString(response.getEntity());

        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode == 200) {
            printStream.println(getMessageWithPrefix(UPLOAD_SQ_SUCCESS));
        } else if (statusCode == 401 || statusCode == 403) {
            // if gets 401 or 403, it returns html
            throw new Exception(getMessageWithVar(FAIL_TO_UPLOAD_DATA, String.valueOf(statusCode), toolchainName));
        } else {
            JsonParser parser = new JsonParser();
            JsonElement element = parser.parse(resStr);
            JsonObject resJson = element.getAsJsonObject();
            if (resJson != null && resJson.has("message")) {
                throw new Exception(getMessageWithVar(FAIL_TO_UPLOAD_DATA_WITH_REASON, String.valueOf(statusCode), resJson.get("message").getAsString()));
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
    public PublishSQImpl getDescriptor() {
        return (PublishSQImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link PublishSQ}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/com/ibm/devops/dra/PublishTest/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class PublishSQImpl extends BuildStepDescriptor<Publisher> {
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
        public PublishSQImpl() {
            super(PublishSQ.class);
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

        public FormValidation doCheckSQHostName(@QueryParameter String value)
                throws IOException, ServletException {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckSQAuthToken(@QueryParameter String value)
                throws IOException, ServletException {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckSQProjectKey(@QueryParameter String value)
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
            return getMessage(PUBLISH_SQ_DISPLAY);
        }

        public String getEnvironment() {
            return getEnv(Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getConsoleUrl());
        }

        public boolean isDebug_mode() {
            return Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).isDebug_mode();
        }
    }
}
