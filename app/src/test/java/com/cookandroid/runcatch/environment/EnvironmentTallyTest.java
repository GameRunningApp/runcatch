package com.cookandroid.runcatch.environment;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class EnvironmentTallyTest {

    @Test
    public void noMatchIsCity() {
        EnvironmentTally tally = new EnvironmentTally();
        tally.add(Collections.emptySet());
        assertEquals(EnvironmentFeature.CITY, tally.finalEnvironment());
    }

    @Test
    public void mostCountedWins_tieGoesToPark() {
        EnvironmentTally tally = new EnvironmentTally();
        tally.add(new HashSet<>(Arrays.asList(EnvironmentFeature.RIVER, EnvironmentFeature.PARK)));
        assertEquals(EnvironmentFeature.PARK, tally.finalEnvironment());
        tally.add(Collections.singleton(EnvironmentFeature.RIVER));
        assertEquals(EnvironmentFeature.RIVER, tally.finalEnvironment());
    }

    @Test
    public void resetClearsCounts() {
        EnvironmentTally tally = new EnvironmentTally();
        tally.add(Collections.singleton(EnvironmentFeature.LAKE));
        tally.reset();
        assertEquals(EnvironmentFeature.CITY, tally.finalEnvironment());
    }
}
