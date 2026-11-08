// IncomingCallNotice.java
package com.anonymous.SpamBlockerApp;

/**
 * Notificación "¿Se la pasas a Manolo?" para las llamadas que la app NO ha filtrado,
 * mientras suenan. Solo en los modos 2 y 3: al rechazar, el desvío "si está ocupado"
 * del operador la manda al servidor.
 *
 * Java puro, sin dependencias de Android, para probarlo con JUnit en la JVM
 * (src/test/.../IncomingCallNoticeTest.java).
 */
public final class IncomingCallNotice {
    public static final String ACCION_PASAR = "com.anonymous.SpamBlockerApp.PASAR_A_MANOLO";
    public static final String ACCION_PASAR_Y_BLOQUEAR = "com.anonymous.SpamBlockerApp.PASAR_Y_BLOQUEAR";
    public static final String EXTRA_NUMERO = "numero";

    /** Una llamada entrante a la vez: con un id fijo, la nueva sustituye a la anterior. */
    public static final int NOTIFICATION_ID = 3001;

    /** Lo que queda en el historial local. */
    public static final String MOTIVO_HISTORIAL = "Pasada a Manolo";

    private IncomingCallNotice() {}

    /**
     * Solo en los modos 2 y 3 (Answer+Hangup activado y modo de servidor). En el modo 1
     * rechazar no lleva a ningún sitio: no hay desvío.
     */
    public static boolean mostrar(boolean answerHangupActivado, AnswerHangupHelper.Mode modo) {
        return motivoNoMostrar(answerHangupActivado, modo) == null;
    }

    /** Por qué no se muestran los botones, para el registro de Logs de Debug; null si se muestran. */
    public static String motivoNoMostrar(boolean answerHangupActivado, AnswerHangupHelper.Mode modo) {
        if (!answerHangupActivado) return "Answer+Hangup desactivado";
        if (modo == null) return "modo desconocido";
        if (modo == AnswerHangupHelper.Mode.HANGUP_IMMEDIATELY) return "modo 1 (no hay desvío al servidor)";
        return null;
    }

    /** Línea de Logs de Debug con la decisión, para verla sin cable. */
    public static String lineaDecision(String numero, String resultado) {
        return "🔘 Pasar a Manolo para " + numero + ": " + resultado;
    }

    public static String titulo(String numero) {
        return "📞 Llamada de " + numero;
    }

    public static String texto() {
        return "¿Se la pasas al contestador IA? No hace falta descolgar.";
    }

    /** Acción del historial: distingue si además se bloqueó. */
    public static String accionHistorial(boolean bloquear) {
        return bloquear ? "Rechazada y bloqueada" : "Rechazada a mano";
    }

    /** Un número oculto no se puede bloquear: no hay nada que guardar en la lista negra. */
    public static boolean sePuedeBloquear(String numero) {
        if (numero == null) return false;
        String limpio = numero.trim();
        return !limpio.isEmpty() && !limpio.equalsIgnoreCase("Desconocido");
    }
}
