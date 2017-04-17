package draplugin.notification;

import hudson.model.Job;
import hudson.model.Run;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.*;
import static junit.framework.TestCase.assertEquals;

/**
 * Created by patrickjoy on 4/14/17.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({Job.class, HttpClients.class, CloseableHttpResponse.class})
@PowerMockIgnore({"javax.crypto.*" })
public class MessageHandlerTest {
    //create a jenkins instance
    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    public void testBuildMessage() {
        String phase = "COMPLETED";
        String result = "SUCCESS";

        Run r = mock(Run.class);
        Job job = PowerMockito.mock(Job.class);

        when(r.getParent()).thenReturn(job);
        when(r.getNumber()).thenReturn(0);
        when(r.getQueueId()).thenReturn((long)0);
        when(r.getUrl()).thenReturn("job/test/1/");
        when(r.getDuration()).thenReturn((long)0);
        when(job.getName()).thenReturn("test");
        when(job.getUrl()).thenReturn("job/test/");

        JSONObject message = new JSONObject();
        JSONObject build = new JSONObject();
        JSONObject scm = new JSONObject();

        build.put("number", r.getNumber());
        build.put("queue_id", r.getQueueId());
        build.put("phase", phase);
        build.put("url", r.getUrl());
        build.put("full_url", Jenkins.getInstance().getRootUrl() + r.getUrl());
        build.put("status", result);
        build.put("duration", r.getDuration());
        build.put("scm", scm);
        message.put("name", job.getName());
        message.put("url", job.getUrl());
        message.put("build", build);

        assertEquals(message, MessageHandler.buildMessage(r, null, phase, result));
    }

    @Test
    public void testPostToWebhook() throws java.io.IOException {
        CloseableHttpClient httpClient = PowerMockito.mock(CloseableHttpClient.class);
        CloseableHttpResponse response = PowerMockito.mock(CloseableHttpResponse.class);
        PowerMockito.mockStatic(HttpClients.class);
        StatusLine statusLine = mock(StatusLine.class);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(baos);

        when(HttpClients.createDefault()).thenReturn(httpClient);
        when(httpClient.execute(any(HttpPost.class))).thenReturn(response);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(statusLine.toString()).thenReturn("200");

        JSONObject message = new JSONObject();
        MessageHandler.postToWebhook("http://fakewebhook", message, printStream);

        String content = new String(baos.toByteArray(), StandardCharsets.UTF_8);

        assertEquals("[IBM Cloud DevOps] Message successfully posted to webhook.", content.trim());
    }
}
