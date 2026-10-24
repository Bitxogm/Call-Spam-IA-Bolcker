package com.anonymous.SpamBlockerApp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * cd android && ./gradlew :app:testDebugUnitTest --tests '*CommercialPrefixRuleTest'
 */
public class CommercialPrefixRuleTest {

    @Test
    public void reconoceElFormatoNacional() {
        assertTrue(CommercialPrefixRule.isPrefix400("400123456"));
    }

    @Test
    public void reconoceElFormatoConMas34() {
        assertTrue(CommercialPrefixRule.isPrefix400("+34400123456"));
    }

    @Test
    public void reconoceElFormatoCon0034() {
        assertTrue(CommercialPrefixRule.isPrefix400("0034400123456"));
    }

    @Test
    public void ignoraEspaciosYGuiones() {
        assertTrue(CommercialPrefixRule.isPrefix400("+34 400 12 34 56"));
        assertTrue(CommercialPrefixRule.isPrefix400("400-123-456"));
    }

    @Test
    public void noConfundeOtrosNumeros() {
        assertFalse(CommercialPrefixRule.isPrefix400("612345678"));
        assertFalse(CommercialPrefixRule.isPrefix400("+34612345678"));
        assertFalse(CommercialPrefixRule.isPrefix400("914000000"));   // 400 en medio
        assertFalse(CommercialPrefixRule.isPrefix400("+44400123456"));  // otro país
        assertFalse(CommercialPrefixRule.isPrefix400(null));
        assertFalse(CommercialPrefixRule.isPrefix400("Desconocido"));
    }

    @Test
    public void bloqueaUn400QueNoEsContacto() {
        assertTrue(CommercialPrefixRule.shouldBlock("+34400123456", false, true));
    }

    @Test
    public void unContactoGuardadoCon400Pasa() {
        assertFalse(CommercialPrefixRule.shouldBlock("+34400123456", true, true));
        assertFalse(CommercialPrefixRule.shouldBlock("400123456", true, true));
        assertFalse(CommercialPrefixRule.shouldBlock("0034400123456", true, true));
    }

    @Test
    public void desactivadaNoBloquea() {
        assertFalse(CommercialPrefixRule.shouldBlock("400123456", false, false));
    }

    @Test
    public void activadaPorDefecto() {
        assertTrue(CommercialPrefixRule.DEFAULT_ENABLED);
    }
}
