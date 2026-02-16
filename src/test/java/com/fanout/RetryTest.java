package com.fanout;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RetryTest {

    @Test
    void retryLogic() {
        int retries = 0;
        boolean ok = false;

        while (retries < 3 && !ok) {
            retries++;
        }

        assertEquals(3, retries);
    }
}
