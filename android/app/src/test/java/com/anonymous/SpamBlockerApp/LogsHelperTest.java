package com.anonymous.SpamBlockerApp;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * cd android && ./gradlew :app:testDebugUnitTest --tests '*LogsHelperTest'
 */
public class LogsHelperTest {

    @Test
    public void laNuevaVaDelanteYNoBorraLasAnteriores() {
        // El fallo era put(0, …): sustituía la primera en vez de insertar
        assertEquals(Arrays.asList("c", "b", "a"),
                     LogsHelper.anteponer(Arrays.asList("b", "a"), "c", 100));
    }

    @Test
    public void conElRegistroVacio() {
        assertEquals(Collections.singletonList("a"),
                     LogsHelper.anteponer(Collections.<String>emptyList(), "a", 100));
    }

    @Test
    public void nuncaPasaDelMaximo() {
        List<Integer> llenas = new ArrayList<>();
        for (int i = 100; i >= 1; i--) llenas.add(i);          // 100 líneas, de la 100 a la 1
        List<Integer> resultado = LogsHelper.anteponer(llenas, 101, 100);
        assertEquals(100, resultado.size());
        assertEquals(Integer.valueOf(101), resultado.get(0));
        assertEquals(Integer.valueOf(2), resultado.get(99));    // la más vieja (1) sale
    }
}
