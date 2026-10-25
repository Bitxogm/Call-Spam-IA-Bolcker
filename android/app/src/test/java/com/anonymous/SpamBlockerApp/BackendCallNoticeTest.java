package com.anonymous.SpamBlockerApp;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * cd android && ./gradlew :app:testDebugUnitTest --tests '*BackendCallNoticeTest'
 */
public class BackendCallNoticeTest {

    @Test
    public void tituloModo3() {
        assertEquals("🤖 Llamada spam de +34600111222 → contestador IA",
                     BackendCallNotice.titulo("+34600111222", true));
    }

    @Test
    public void tituloModo2() {
        assertEquals("🤖 Llamada spam de 900123456 → mensaje fijo",
                     BackendCallNotice.titulo("900123456", false));
    }

    @Test
    public void lineaDelResumen() {
        assertEquals("18:42 · +34600111222 → contestador IA",
                     BackendCallNotice.linea("18:42", "+34600111222", true));
        assertEquals("09:05 · 900123456 → mensaje fijo",
                     BackendCallNotice.linea("09:05", "900123456", false));
    }

    @Test
    public void losHuecosRotanSinPasarDelMaximo() {
        assertEquals(0, BackendCallNotice.hueco(0));
        assertEquals(4, BackendCallNotice.hueco(4));
        assertEquals(0, BackendCallNotice.hueco(5));     // la sexta sustituye a la primera
        assertEquals(2, BackendCallNotice.hueco(12));
        assertEquals(4, BackendCallNotice.hueco(-1));    // un contador corrupto no rompe nada
    }

    @Test
    public void recientesLaNuevaPrimero() {
        assertEquals(Arrays.asList("c", "b", "a"),
                     BackendCallNotice.recientes(Arrays.asList("b", "a"), "c"));
        assertEquals(Collections.singletonList("a"),
                     BackendCallNotice.recientes(Collections.emptyList(), "a"));
    }

    @Test
    public void recientesNoCrecenSinControl() {
        List<String> llenas = Arrays.asList("5", "4", "3", "2", "1");
        assertEquals(Arrays.asList("6", "5", "4", "3", "2"),
                     BackendCallNotice.recientes(llenas, "6"));
    }

    @Test
    public void tituloDelResumen() {
        assertEquals("1 llamada spam desviada", BackendCallNotice.tituloResumen(1));
        assertEquals("7 llamadas spam desviadas", BackendCallNotice.tituloResumen(7));
    }
}
