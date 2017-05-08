package com.ibm.devops.dra;

import org.junit.Test;
import static junit.framework.TestCase.*;


public class UtilTest {

    @Test
    public void testIsNullOrEmpty() {
        Boolean result = Util.isNullOrEmpty("");
        assertTrue(result);

        result = Util.isNullOrEmpty(null);
        assertTrue(result);

        result = Util.isNullOrEmpty("Not Null");
        assertFalse(result);
    }

    @Test
    public void testAllNotNullOrEmpty() {
        Boolean result = Util.allNotNullOrEmpty("Not Null", "Not Null", "Not Null", "");
        assertFalse(result);

        result = Util.allNotNullOrEmpty("Not Null", "Not Null", "Not Null", null);
        assertFalse(result);

        result = Util.allNotNullOrEmpty("Not Null", "Not Null", "Not Null", "Not Null");
        assertTrue(result);
    }
}
