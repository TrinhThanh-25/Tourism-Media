package com.example.tourismmedia;

import com.example.tourismmedia.ui.common.Code128;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void code128_isStableAndDependsOnVoucherCode() {
        boolean[] first = Code128.modules("72D0639682D4");
        boolean[] same = Code128.modules("72D0639682D4");
        boolean[] different = Code128.modules("FF98AF464F03");
        assertArrayEquals(first, same);
        assertEquals(167, first.length);
        assertFalse(java.util.Arrays.equals(first, different));
    }
}
