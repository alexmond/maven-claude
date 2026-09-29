package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CalcTest {

    @Test
    void addsNumbers() {
        assertEquals(4, new Calc().add(2, 2));
    }

    @Test
    void addsNegatives() {
        assertEquals(-4, new Calc().add(-2, -2));
    }

}
