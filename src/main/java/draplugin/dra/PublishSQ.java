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

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.entity.StringEntity;
import org.kohsuke.stapler.*;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.*;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.TimeZone;
import java.util.HashSet;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.xml.bind.DatatypeConverter;

/**
 * Authenticate with Bluemix and then upload the result file to DRA
 * Created by charlie cox on 4/19/17.
 */
public class PublishSQ extends AbstractDevOpsAction implements SimpleBuildStep, Serializable {

    private final static String API_PART = "/organizations/{org_name}/toolchainids/{toolchain_id}/buildartifacts/{build_artifact}/builds/{build_id}/results";
    private final static String CONTENT_TYPE_JSON = "application/json";

    // form fields from UI
    private String buildNumber;
    private String applicationName;
    private String buildJobName;
    private String orgName;
    private String toolchainName;
    private String environmentName;
    private String credentialsId;

    private String SQProjectKey;
    private String SQHostName;
    private String SQAuthToken;
    private String IBMusername;
    private String IBMpassword;

    private String envName;
    private boolean isDeploy;

    private PrintStream printStream;
    private static String dlmsUrl;
    public static String bluemixToken;

    // need to add projectkey
    // need to add host url
    public PublishSQ(String orgName,
                        String applicationName,
                        String toolchainName,
                        String SQProjectKey,
                        String SQHostName,
                        String SQAuthToken,
                        String IBMusername,
                        String IBMpassword) {
        this.orgName = orgName;
        this.applicationName = applicationName;
        this.toolchainName = toolchainName;
        this.SQProjectKey = SQProjectKey;
        this.SQHostName = SQHostName;
        // ':' needs to be added so the SQ api knows an auth token is being used
        this.SQAuthToken = DatatypeConverter.printBase64Binary((SQAuthToken + ":").getBytes());
        this.IBMusername = IBMusername;
        this.IBMpassword = IBMpassword;
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

    public String getEnvironmentName() {
        return environmentName;
    }

    public String getBuildJobName() {
        return buildJobName;
    }

    public String getCredentialsId() {
        return credentialsId;
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

    @Override
    public void perform(@Nonnull Run build, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {

        printStream = listener.getLogger();
        printPluginVersion(this.getClass().getClassLoader(), printStream);

        // Get the project name and build id from environment
        EnvVars envVars = build.getEnvironment(listener);

        // verify if user chooses advanced option to input customized DLMS
        String env = getDescriptor().getEnvironment();
        String targetAPI = chooseTargetAPI(env);
        String url = chooseDLMSUrl(env) + API_PART;
        // expand to support env vars
        this.orgName = envVars.expand(this.orgName);
        this.applicationName = envVars.expand(this.applicationName);
        this.toolchainName = envVars.expand(this.toolchainName);
        if (this.isDeploy || !Util.isNullOrEmpty(this.envName)) {
            this.environmentName = envVars.expand(this.envName);
        }

        String buildNumber;
        // if user does not specify the build number
        if (Util.isNullOrEmpty(this.buildNumber)) {
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
                String rootUrl = Jenkins.getInstance().getRootUrl();
            }
        } else {
            buildNumber = envVars.expand(this.buildNumber);
        }

        url = url.replace("{org_name}", this.orgName);
        url = url.replace("{toolchain_id}", this.toolchainName);
        url = url.replace("{build_artifact}", URLEncoder.encode(this.applicationName, "UTF-8").replaceAll("\\+", "%20"));
        url = url.replace("{build_id}", URLEncoder.encode(buildNumber, "UTF-8").replaceAll("\\+", "%20"));
        this.dlmsUrl = url;

        // get the Bluemix token
        try {
            if (Util.isNullOrEmpty(this.credentialsId)) {
                bluemixToken = GetBluemixToken(IBMusername, IBMpassword, targetAPI);
            } else {
                bluemixToken = GetBluemixToken(build.getParent(), this.credentialsId, targetAPI);
            }

            printStream.println("[IBM Cloud DevOps] Log in successfully, get the Bluemix token");
        } catch (Exception e) {
            printStream.println("[IBM Cloud DevOps] Username/Password is not correct, fail to authenticate with Bluemix");
            printStream.println("[IBM Cloud DevOps]" + e.toString());
            return;
        }

        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Basic " + this.SQAuthToken);
        try {
            JsonObject SQqualityGate = sendGETRequest(this.SQHostName + "/api/qualitygates/project_status?projectKey=" + this.SQProjectKey, headers);
            printStream.println("[IBM Cloud DevOps] Successfully queried SonarQube for quality gate information");
            JsonObject SQissues = sendGETRequest(this.SQHostName + "/api/issues/search?statuses=OPEN&componentKeys=" + this.SQProjectKey, headers);
            printStream.println("[IBM Cloud DevOps] Successfully queried SonarQube for issue information");
            JsonObject SQratings = sendGETRequest(this.SQHostName + "/api/measures/component?metricKeys=reliability_rating,security_rating,sqale_rating&componentKey=" + this.SQProjectKey, headers);
            printStream.println("[IBM Cloud DevOps] Successfully queried SonarQube for metric information");

            JsonObject payload = createDLMSPayload(SQqualityGate, SQissues, SQratings);
            JsonArray urls = createPayloadUrls(this.SQHostName, this.SQProjectKey);
            sendPayloadToDLMS(bluemixToken, payload, urls);

        } catch (Exception e) {
            printStream.println("[IBM Cloud DevOps] Error: Unable to upload results. Please make sure all parameters are valid");
            e.printStackTrace();
        }
    }

    /**
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
     *
     * @param qualityGateData information pertaining to SQ gate status
     * @param issuesData information pertaining to SQ issues raised
     * @param ratingsData information pertaining to SQ ratings
     * @return combined gson object
     */
    public JsonObject createDLMSPayload(JsonObject qualityGateData, JsonObject issuesData, JsonObject ratingsData) {

        JsonObject payload = new JsonObject();

        payload.add("qualityGate", qualityGateData.get("projectStatus"));
        payload.add("issues", issuesData.get("issues"));

        JsonParser parser = new JsonParser();
        JsonObject component = (JsonObject)parser.parse(ratingsData.get("component").toString());
        payload.add("ratings", component.get("measures"));

        return payload;
    }

    /**
     * Sends a GET request to the provided url
     *
     * @param url the endpoint of the request
     * @param headers a map of headers where key is the header name and the map value is the header value
     * @return a JSON parsed representation of the payload returneds
     * @throws Exception
     */
    private JsonObject sendGETRequest(String url, Map<String, String> headers) throws Exception {

        URL obj = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) obj.openConnection();

        connection.setRequestMethod("GET");

        //add request headers
        for(String headerName: headers.keySet()) {
            connection.setRequestProperty(headerName, headers.get(headerName));
        }

        BufferedReader in = new BufferedReader(
                new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        JsonParser parser = new JsonParser();
        return parser.parse(response.toString()).getAsJsonObject();
    }

    /**
     * Sends POST method to DLMS to upload SQ results
     *
     * @param bluemixToken the bluemix auth header that allows us to talk to dlms
     * @param payload the content part of the payload to send to dlms
     * @param urls a json array that holds the urls for a payload
     * @return boolean based on if the request was successful or not
     */
    private boolean sendPayloadToDLMS(String bluemixToken, JsonObject payload, JsonArray urls) {
        String resStr = "";
        printStream.println("[IBM Cloud DevOps] Uploading SonarQube results...");
        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();

            HttpPost postMethod = new HttpPost(this.dlmsUrl);
            postMethod = addProxyInformation(postMethod);
            postMethod.setHeader("Authorization", bluemixToken);
            postMethod.setHeader("Content-Type", CONTENT_TYPE_JSON);

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            TimeZone utc = TimeZone.getTimeZone("UTC");
            dateFormat.setTimeZone(utc);
            String timestamp = dateFormat.format(new java.util.Date());

            JsonObject body = new JsonObject();

            body.addProperty("contents", DatatypeConverter.printBase64Binary(payload.toString().getBytes()));
            body.addProperty("contents_type", CONTENT_TYPE_JSON);
            body.addProperty("timestamp", timestamp);
            body.add("url", urls);

            StringEntity data = new StringEntity(body.toString());
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
    public PublishSQ.PublishTestImpl getDescriptor() {
        return (PublishSQ.PublishTestImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link PublishSQ}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/draplugin/dra/PublishTest/*.jelly</tt>
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

        private String environment;
        private boolean debug_mode;

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
            return "Publish test result to IBM Cloud DevOps";
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
