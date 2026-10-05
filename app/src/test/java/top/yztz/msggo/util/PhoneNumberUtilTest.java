package top.yztz.msggo.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PhoneNumberUtilTest {
    @Test public void convertsArabicDigits() {
        assertEquals("733222087", PhoneNumberUtil.fromSpreadsheet("٧٣٣٢٢٢٠٨٧"));
    }

    @Test public void repairsExcelDecimalAndScientificValues() {
        assertEquals("733222087", PhoneNumberUtil.fromSpreadsheet("733222087.0"));
        assertEquals("733222087", PhoneNumberUtil.fromSpreadsheet("7.33222087E8"));
    }

    @Test public void removesDisplaySeparatorsButKeepsCountryPrefix() {
        assertEquals("+967733222087", PhoneNumberUtil.fromSpreadsheet("+967 733-222-087"));
    }

    @Test public void validatesLocalAndInternationalNumbers() {
        assertTrue(PhoneNumberUtil.isPlausible("733222087"));
        assertTrue(PhoneNumberUtil.isPlausible("+967733222087"));
        assertFalse(PhoneNumberUtil.isPlausible("رقم هاتف"));
    }
}
