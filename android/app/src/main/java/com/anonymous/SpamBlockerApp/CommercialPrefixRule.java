// CommercialPrefixRule.java
package com.anonymous.SpamBlockerApp;

/**
 * Regla del prefijo 400: desde el 17/10/2026, en España las llamadas comerciales
 * deben llegar desde números 400.
 *
 * Java puro, sin dependencias de Android, para poder probarla con JUnit en la JVM
 * (src/test/.../CommercialPrefixRuleTest.java).
 */
public final class CommercialPrefixRule {
    /** Clave en SharedPreferences "spam_blocker_settings" (la escribe ContactsModule). */
    public static final String PREFS_KEY = "bloquear_prefijo_400";
    public static final boolean DEFAULT_ENABLED = true;

    private static final String PREFIX = "400";

    private CommercialPrefixRule() {}

    /**
     * Quita todo lo que no sea dígito y el prefijo de país: 0034 siempre, 34 solo si
     * sobran dígitos para un número español de 9 (mismo criterio que los prefijos
     * premium de CallScreeningServiceImpl).
     */
    static String normalize(String number) {
        if (number == null) return "";
        String cleaned = number.replaceAll("[^0-9]", "");
        if (cleaned.startsWith("0034")) return cleaned.substring(4);
        if (cleaned.startsWith("34") && cleaned.length() > 10) return cleaned.substring(2);
        return cleaned;
    }

    /** 400…, +34400… y 0034400…, con o sin espacios. */
    public static boolean isPrefix400(String number) {
        return normalize(number).startsWith(PREFIX);
    }

    /** Los contactos siempre ganan: un 400 guardado en la agenda no se bloquea. */
    public static boolean shouldBlock(String number, boolean isContact, boolean enabled) {
        return enabled && !isContact && isPrefix400(number);
    }
}
