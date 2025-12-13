// SpamPatternDetector.java
package com.anonymous.SpamBlockerApp;

import android.util.Log;

/**
 * Detector de patrones de spam específicos para España (2025)
 *
 * Basado en regulación española:
 * - Orden TDF/149/2025: Prohibición de móviles (6xx, 7xx) para telemarketing
 * - Obligatorio: Prefijos 800, 900 para llamadas comerciales
 * - Detecta números premium y tarificación especial
 *
 * NO requiere APIs externas - 100% local y gratuito
 */
public class SpamPatternDetector {
    private static final String TAG = "SpamPatternDetector";

    /**
     * Categorías de spam detectadas
     */
    public enum SpamCategory {
        TELEMARKETING_LEGAL,      // 800, 900 (legal pero comercial)
        TELEMARKETING_ILEGAL,     // 6xx, 7xx (prohibido desde junio 2025)
        PREMIUM,                  // 901, 902, 803, 806, 807, 905
        UNKNOWN,                  // Desconocido/privado
        NOT_SPAM                  // Normal
    }

    /**
     * Resultado de detección
     */
    public static class SpamResult {
        public final boolean isSpam;
        public final int score;           // 0-100
        public final SpamCategory category;
        public final String description;
        public final String source;

        public SpamResult(boolean isSpam, int score, SpamCategory category, String description) {
            this.isSpam = isSpam;
            this.score = score;
            this.category = category;
            this.description = description;
            this.source = "local_spain";
        }

        @Override
        public String toString() {
            return String.format("SpamResult{spam=%s, score=%d, cat=%s, desc=%s}",
                isSpam, score, category, description);
        }
    }

    /**
     * Analiza un número de teléfono español y retorna resultado de spam
     *
     * @param phoneNumber Número de teléfono (puede incluir +34, espacios, etc.)
     * @return SpamResult con score y categoría
     */
    public static SpamResult analyze(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return new SpamResult(false, 0, SpamCategory.NOT_SPAM, "Número vacío");
        }

        // Limpiar número: quitar +34, 0034, espacios, guiones
        String cleaned = cleanPhoneNumber(phoneNumber);

        Log.d(TAG, "Analizando número: " + phoneNumber + " → limpio: " + cleaned);

        // 1. Números desconocidos/privados
        if (isUnknownNumber(phoneNumber)) {
            Log.d(TAG, "→ Número desconocido/privado: SPAM");
            return new SpamResult(true, 95, SpamCategory.UNKNOWN,
                "Número privado/oculto");
        }

        // 2. Telemarketing ILEGAL (móviles 6xx, 7xx - prohibido desde junio 2025)
        if (isIllegalTelemarketing(cleaned)) {
            Log.d(TAG, "→ Telemarketing ILEGAL (móvil comercial): SPAM");
            return new SpamResult(true, 90, SpamCategory.TELEMARKETING_ILEGAL,
                "Móvil comercial (prohibido desde junio 2025)");
        }

        // 3. Números premium/tarificación especial (901, 902, 803, 806, 807, 905)
        if (isPremiumNumber(cleaned)) {
            Log.d(TAG, "→ Número premium/tarificación especial: SPAM");
            return new SpamResult(true, 85, SpamCategory.PREMIUM,
                "Número de tarificación especial");
        }

        // 4. Telemarketing LEGAL (800, 900 - obligatorios para comercial)
        if (isLegalTelemarketing(cleaned)) {
            Log.d(TAG, "→ Telemarketing LEGAL (800/900): SPAM");
            return new SpamResult(true, 75, SpamCategory.TELEMARKETING_LEGAL,
                "Número comercial (800/900)");
        }

        // 5. Número normal
        Log.d(TAG, "→ Número normal: NO SPAM");
        return new SpamResult(false, 0, SpamCategory.NOT_SPAM,
            "Número normal");
    }

    /**
     * Limpia el número de teléfono dejando solo dígitos
     * Quita: +34, 0034, espacios, guiones, paréntesis
     */
    private static String cleanPhoneNumber(String phoneNumber) {
        // Quitar todo excepto números
        String cleaned = phoneNumber.replaceAll("[^0-9]", "");

        // Quitar prefijo internacional español
        if (cleaned.startsWith("34")) {
            cleaned = cleaned.substring(2);
        } else if (cleaned.startsWith("0034")) {
            cleaned = cleaned.substring(4);
        }

        return cleaned;
    }

    /**
     * Verifica si es número desconocido/privado
     */
    private static boolean isUnknownNumber(String phoneNumber) {
        String lower = phoneNumber.toLowerCase();
        return lower.contains("desconocido") ||
               lower.contains("unknown") ||
               lower.contains("privado") ||
               lower.contains("private") ||
               lower.contains("oculto") ||
               lower.contains("hidden") ||
               lower.contains("anonymous");
    }

    /**
     * Telemarketing ILEGAL: Móviles 6xx, 7xx usados para comercial
     * Prohibido desde junio 2025 (Orden TDF/149/2025)
     */
    private static boolean isIllegalTelemarketing(String cleaned) {
        if (cleaned.length() < 3) return false;

        String prefix = cleaned.substring(0, 1);

        // Móviles españoles que NO deberían usarse para telemarketing
        return prefix.equals("6") || prefix.equals("7");
    }

    /**
     * Números premium y tarificación especial
     */
    private static boolean isPremiumNumber(String cleaned) {
        if (cleaned.length() < 3) return false;

        String prefix3 = cleaned.substring(0, Math.min(3, cleaned.length()));

        return prefix3.equals("901") ||  // Tarificación especial
               prefix3.equals("902") ||  // Tarificación especial
               prefix3.equals("803") ||  // Servicios de participación
               prefix3.equals("806") ||  // Servicios de entretenimiento
               prefix3.equals("807") ||  // Servicios de entretenimiento
               prefix3.equals("905");    // Servicios de valor añadido
    }

    /**
     * Telemarketing LEGAL: Prefijos 800, 900
     * Obligatorios para llamadas comerciales desde 2025
     */
    private static boolean isLegalTelemarketing(String cleaned) {
        if (cleaned.length() < 3) return false;

        String prefix3 = cleaned.substring(0, Math.min(3, cleaned.length()));

        return prefix3.equals("800") ||  // Gratuitos comerciales
               prefix3.equals("900");    // Tarificación especial comercial
    }

    /**
     * Obtiene descripción legible de la categoría
     */
    public static String getCategoryDescription(SpamCategory category) {
        switch (category) {
            case TELEMARKETING_LEGAL:
                return "Telemarketing (800/900)";
            case TELEMARKETING_ILEGAL:
                return "Móvil comercial ilegal";
            case PREMIUM:
                return "Tarificación especial";
            case UNKNOWN:
                return "Número oculto";
            default:
                return "Normal";
        }
    }

    /**
     * Determina si un score indica spam (>= 75)
     */
    public static boolean isSpamScore(int score) {
        return score >= 75;
    }

    /**
     * Determina nivel de riesgo basado en score
     */
    public static String getRiskLevel(int score) {
        if (score >= 90) return "MUY ALTO";
        if (score >= 85) return "ALTO";
        if (score >= 75) return "MEDIO";
        if (score >= 50) return "BAJO";
        return "NORMAL";
    }
}
