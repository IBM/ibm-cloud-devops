package draplugin.notification;

import hudson.model.AbstractBuild;
import hudson.tasks.Publisher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.List;

import static org.mockito.Mockito.mock;

/**
 * Created by patrickjoy on 4/18/17.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({BuildListener.class})
public class EventHandlerTest {

    //create a jenkins instance
    //@Rule
    //public JenkinsRule j = new JenkinsRule();

    @Test
    public void testFindPublisher() {
//        AbstractBuild r = mock(AbstractBuild.class);
//        OTCNotifier otcNotifier = mock(OTCNotifier.class);
//        BuildListener buildListener = PowerMockito.spy(new BuildListener());
//
//        List<Publisher> publisherList = r.getProject().getPublishersList().toList();
//        publisherList.add(otcNotifier);
//
//        when(buildListener, method(BuildListener.class, "findPublisher", AbstractBuild.class)).withArguments(any(AbstractBuild.class)).thenReturn

    }

    @Test
    public void testGetEnv() {

    }

    @Test
    public void testSetWebhookFromEnv() {

    }
}
