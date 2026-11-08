package com.anonymous.SpamBlockerApp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * cd android && ./gradlew :app:testDebugUnitTest --tests '*IncomingCallNoticeTest'
 */
public class IncomingCallNoticeTest {

    @Test
    public void seMuestraEnLosModos2y3() {
        assertTrue(IncomingCallNotice.mostrar(true, AnswerHangupHelper.Mode.BACKEND_FIXED));
        assertTrue(IncomingCallNotice.mostrar(true, AnswerHangupHelper.Mode.BACKEND_AI));
    }

    @Test
    public void noSeMuestraEnElModo1() {
        assertFalse(IncomingCallNotice.mostrar(true, AnswerHangupHelper.Mode.HANGUP_IMMEDIATELY));
    }

    @Test
    public void noSeMuestraConAnswerHangupDesactivado() {
        assertFalse(IncomingCallNotice.mostrar(false, AnswerHangupHelper.Mode.BACKEND_AI));
        assertFalse(IncomingCallNotice.mostrar(true, null));
    }

    @Test
    public void motivoVisibleEnLogsDeDebug() {
        assertEquals(null, IncomingCallNotice.motivoNoMostrar(true, AnswerHangupHelper.Mode.BACKEND_AI));
        assertEquals("Answer+Hangup desactivado",
                     IncomingCallNotice.motivoNoMostrar(false, AnswerHangupHelper.Mode.BACKEND_AI));
        assertEquals("modo 1 (no hay desvío al servidor)",
                     IncomingCallNotice.motivoNoMostrar(true, AnswerHangupHelper.Mode.HANGUP_IMMEDIATELY));
        assertEquals("modo desconocido", IncomingCallNotice.motivoNoMostrar(true, null));
    }

    @Test
    public void lineaDeLaDecision() {
        assertEquals("🔘 Pasar a Manolo para 600111222: mostrada",
                     IncomingCallNotice.lineaDecision("600111222", "mostrada"));
    }

    @Test
    public void textos() {
        assertEquals("📞 Llamada de +34600111222", IncomingCallNotice.titulo("+34600111222"));
        assertEquals("Pasada a Manolo", IncomingCallNotice.MOTIVO_HISTORIAL);
        assertEquals("Rechazada a mano", IncomingCallNotice.accionHistorial(false));
        assertEquals("Rechazada y bloqueada", IncomingCallNotice.accionHistorial(true));
    }

    @Test
    public void unNumeroOcultoNoSeBloquea() {
        assertTrue(IncomingCallNotice.sePuedeBloquear("+34600111222"));
        assertFalse(IncomingCallNotice.sePuedeBloquear("Desconocido"));
        assertFalse(IncomingCallNotice.sePuedeBloquear("  "));
        assertFalse(IncomingCallNotice.sePuedeBloquear(null));
    }

    @Test
    public void lasDosAccionesSonDistintas() {
        assertFalse(IncomingCallNotice.ACCION_PASAR.equals(IncomingCallNotice.ACCION_PASAR_Y_BLOQUEAR));
    }
}
