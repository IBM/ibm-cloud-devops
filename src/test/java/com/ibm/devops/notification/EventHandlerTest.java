/*
 <notice>

 Copyright 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.notification;

import com.ibm.devops.dra.Util;
import hudson.EnvVars;
import hudson.model.*;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import org.junit.Test;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;
import static junit.framework.TestCase.*;

public class EventHandlerTest {

    @Test
    public void testFindPublisher() {
        OTCNotifier otcNotifier = mock(OTCNotifier.class);
        AbstractBuild r = mock(AbstractBuild.class);
        AbstractProject project = mock(AbstractProject.class);
        DescribableList<Publisher, Descriptor<Publisher>> describableList = mock(DescribableList.class);
        ArrayList<Publisher> publisherList = new ArrayList<Publisher>();

        publisherList.add(otcNotifier);
        when(r.getProject()).thenReturn(project);
        when(project.getPublishersList()).thenReturn(describableList);
        when(describableList.toList()).thenReturn(publisherList);

        assertEquals(otcNotifier, EventHandler.findPublisher(r));
    }

    @Test
    public void testGetEnv() throws IOException, InterruptedException {
        AbstractBuild r = mock(AbstractBuild.class);
        TaskListener listener = mock(TaskListener.class);
        PrintStream printStream = mock(PrintStream.class);

        when(r.getEnvironment(listener)).thenReturn(new EnvVars());
        assertNotNull(EventHandler.getEnv(r, listener, printStream));

        when(r.getEnvironment(listener)).thenThrow(new IOException("..."));
        assertNull(EventHandler.getEnv(r, listener, printStream));

        reset(r);
        when(r.getEnvironment(listener)).thenThrow(new InterruptedException("..."));
        assertNull(EventHandler.getEnv(r, listener, printStream));
    }

    @Test
    public void testIsRelevant(){
        OTCNotifier notifier = mock(OTCNotifier.class);
        String[] phases = {"STARTED", "COMPLETED", "FINALIZED"};//check all possible phases

        when(notifier.getOnStarted()).thenReturn(true);
        when(notifier.getOnCompleted()).thenReturn(true);
        when(notifier.getOnFinalized()).thenReturn(true);
        when(notifier.getFailureOnly()).thenReturn(false);

        assertFalse(EventHandler.isRelevant(notifier, "BAD_PHASE", null));//check bad phase
        assertTrue(EventHandler.isRelevant(notifier, "STARTED", null));//check started with no status

        for(String phase : phases){
            assertTrue(EventHandler.isRelevant(notifier, phase, Result.SUCCESS));
        }

        for(String phase : phases){
            assertTrue(EventHandler.isRelevant(notifier, phase, Result.FAILURE));
        }

        //Check failures only
        when(notifier.getFailureOnly()).thenReturn(true);

        for(String phase : phases){
            assertFalse(EventHandler.isRelevant(notifier, phase, Result.SUCCESS));
        }

        for(String phase : phases){
            assertTrue(EventHandler.isRelevant(notifier, phase, Result.FAILURE));
        }
    }

    @Test
    public void testGetWebhookFromEnv() {
        Map<String, String> m = new HashMap<>();
        EnvVars envVars = new EnvVars(m);

        assertNull(Util.getWebhookUrl(envVars));

        m.put("ICD_WEBHOOK_URL", "compatibility_test");
        envVars = new EnvVars(m);
        assertEquals("compatibility_test", Util.getWebhookUrl(envVars));

        m.put("IBM_CLOUD_DEVOPS_WEBHOOK_URL", "test");
        envVars = new EnvVars(m);
        assertEquals("test", Util.getWebhookUrl(envVars));
    }
}
