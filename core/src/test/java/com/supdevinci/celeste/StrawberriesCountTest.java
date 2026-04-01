package com.supdevinci.celeste;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class StrawberriesCountTest {

    @Test
    void StrawberriesCountTest() {
        StrawberriesCount strawberriesCount = new StrawberriesCount();

        assertEquals(0, strawberriesCount.getStrawberriesCount(), "Initial score should be 0");

        strawberriesCount.addStrawberry();
        assertEquals(1, strawberriesCount.getStrawberriesCount(), "Score should be 1 after adding one strawberry");

        strawberriesCount.addStrawberry();
        strawberriesCount.addStrawberry();
        assertEquals(3, strawberriesCount.getStrawberriesCount(), "Score should be 3 after adding three strawberries");
    }
}
