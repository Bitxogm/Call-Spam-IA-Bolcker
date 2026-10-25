// BackendCallNotice.java
package com.anonymous.SpamBlockerApp;

import java.util.ArrayList;
import java.util.List;

/**
 * Textos y tope de la notificación de los modos 2 y 3 (llamada desviada al servidor).
 *
 * Java puro, sin dependencias de Android, para probarlo con JUnit en la JVM
 * (src/test/.../BackendCallNoticeTest.java).
 */
public final class BackendCallNotice {
    /** Notificaciones individuales visibles a la vez; las más viejas se sustituyen. */
    public static final int MAX_VISIBLES = 5;

    private BackendCallNotice() {}

    /**
     * "🤖 Llamada spam de <número> → contestador IA" en el Modo 3, "→ mensaje fijo" en el
     * Modo 2. Sin nombre de personaje: lo elige el servidor al descolgar, cuando la app
     * ya ha notificado. El historial muestra quién atendió.
     */
    public static String titulo(String numero, boolean modoIA) {
        return "🤖 Llamada spam de " + numero + destino(modoIA);
    }

    /** Línea del resumen del grupo: "18:42 · +34600111222 → contestador IA". */
    public static String linea(String hora, String numero, boolean modoIA) {
        return hora + " · " + numero + destino(modoIA);
    }

    private static String destino(boolean modoIA) {
        return modoIA ? " → contestador IA" : " → mensaje fijo";
    }

    /** Hueco de notificación (0..MAX_VISIBLES-1) para la llamada número `contador`. */
    public static int hueco(int contador) {
        return Math.floorMod(contador, MAX_VISIBLES);
    }

    /** La lista del resumen: la nueva primero, como mucho MAX_VISIBLES líneas. */
    public static List<String> recientes(List<String> anteriores, String nueva) {
        List<String> lista = new ArrayList<>();
        lista.add(nueva);
        for (String linea : anteriores) {
            if (lista.size() >= MAX_VISIBLES) break;
            lista.add(linea);
        }
        return lista;
    }

    /** Título del resumen del grupo: "3 llamadas spam desviadas". */
    public static String tituloResumen(int total) {
        return total == 1 ? "1 llamada spam desviada" : total + " llamadas spam desviadas";
    }
}
