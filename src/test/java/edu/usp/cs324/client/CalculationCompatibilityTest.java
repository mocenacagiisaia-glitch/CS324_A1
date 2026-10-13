package edu.usp.cs324.client;

import edu.usp.cs324.api.Job;
import edu.usp.cs324.jobs.IntegerJobEngine;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Locks the input-to-distributed-aggregation semantics needed by the A2 adapter. */
class CalculationCompatibilityTest {
    private final IntegerJobEngine engine = new IntegerJobEngine();

    private BigInteger distributed(Job.Type type, String input, int workers) {
        Job job = JobInput.parse(type, input);
        return engine.aggregate(type, engine.split(job, workers).stream()
                .map(engine::compute).toList());
    }

    @Test void inclusivePrimeRangesIncludePrimeEndpointsAndExcludeNonPrimes() {
        for (int workers : List.of(1, 2, 5, 20)) {
            assertEquals(BigInteger.valueOf(10), distributed(Job.Type.PRIMESUM, "2,5", workers));
            assertEquals(BigInteger.TWO, distributed(Job.Type.PRIMESUM, "2,2", workers));
            assertEquals(BigInteger.ZERO, distributed(Job.Type.PRIMESUM, "4,4", workers));
            assertEquals(BigInteger.ZERO, distributed(Job.Type.PRIMESUM,
                    "-2147483648,1", workers));
            assertEquals(BigInteger.valueOf(Integer.MAX_VALUE), distributed(Job.Type.PRIMESUM,
                    "2147483647,2147483647", workers));
        }
    }

    @Test void listBoundariesDuplicatesAndEmptyCountSurviveCsvAndSplitting() {
        for (int workers : List.of(1, 3, 100)) {
            assertEquals(BigInteger.valueOf(-1), distributed(Job.Type.MAX,
                    "\uFEFF\"-2147483648\",-9\r\n-1,-1", workers));
            assertEquals(BigInteger.valueOf(4), distributed(Job.Type.PRIMECOUNT,
                    "2147483647,2147483647\n2,2,-2147483648,0,1,4", workers));
            assertEquals(BigInteger.ZERO, distributed(Job.Type.PRIMECOUNT, "\n\r\n", workers));
        }
    }

    @Test void validationHappensBeforeCalculation() {
        for (String invalid : List.of("", "1,", "2147483648", "-2147483649"))
            assertThrows(IllegalArgumentException.class,
                    () -> distributed(Job.Type.MAX, invalid, 4));
        assertThrows(IllegalArgumentException.class,
                () -> distributed(Job.Type.PRIMESUM, "5,2", 4));
        assertThrows(IllegalArgumentException.class,
                () -> distributed(Job.Type.PRIMESUM, "1,2,3", 4));
    }
}
