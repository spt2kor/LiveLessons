import utils.RunTimer;
import utils.StampedLockHashMap;

import java.util.*;
import java.util.concurrent.*;

import static java.util.Map.Entry.comparingByValue;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * This example showcases and benchmarks the use of a Java
 * ConcurrentHashMap, a Java SynchronizedMap, and a HashMap protected
 * with a Java StampedLock are used to compute/cache/retrieve prime
 * numbers.  This example also demonstrates the use of stream slicing
 * with the Java streams takeWhile() and dropWhile() operations.
 */
public class ex9 {

    /**
     * True if we're running in verbose mode, else false.
     */
    private static final boolean sVERBOSE = false;

    /**
     * Number of cores known to the Java execution environment.
     */
    private static final int sNUMBER_OF_CORES =
        Runtime.getRuntime().availableProcessors();

    /**
     * This executor runs the prime number computation tasks.
     */
    private final ExecutorService mExecutor =
        // Create a pool with as many threads as the Java execution
        // environment thinks there are cores.
        Executors.newFixedThreadPool(sNUMBER_OF_CORES);

    /**
     * Main entry point into the test program.
     */
    static public void main(String[] argv) throws InterruptedException {
        // Determine the max number of iterations.
        // Number of times each thread iterates computing prime numbers.
        int sMAX = 100000;
        int maxIterations = argv.length == 0
            ? sMAX 
            : Integer.valueOf(argv[0]);

        // Create an instance to test.
        ex9 test = new ex9();

        // Create a synchronized hash map.
        Map<Integer, Integer> synchronizedHashMap = 
            Collections.synchronizedMap(new HashMap<>());

        // Create a concurrent hash map.
        Map<Integer, Integer> concurrentHashMap =
                new ConcurrentHashMap<>();

        // Create a concurrent hash map.
        Map<Integer, Integer> stampedLockHashMap =
                new StampedLockHashMap<>();

        test.timeTest(maxIterations,
                 synchronizedHashMap, "synchronizedHashMap");

        test.timeTest(maxIterations,
                 concurrentHashMap, "concurrentHashMap");

        test.timeTest(maxIterations,
                 stampedLockHashMap, "stampedLockHashMap");

        // Print the results.
        System.out.println(RunTimer.getTimingResults());

        // Demonstrate slicing.
        test.demonstrateSlicing(concurrentHashMap);

        // Shutdown the executor.
        test.mExecutor.shutdownNow();

        // Wait for a bit for the executor to shutdown and then exit.
        test.mExecutor.awaitTermination(500,
                                        TimeUnit.MILLISECONDS);
    }

    /**
     * Time the test for {@code maxIterations} using the given
     * {@code hashMap}.
     */
    private void timeTest(int maxIterations,
                          Map<Integer, Integer> hashMap,
                          String testName) {
        RunTimer
            // Time how long this test takes to run.
            .timeRun(() ->
                     // Run the test using a synchronized hash map.
                     runTest(maxIterations,
                                  hashMap,
                                  testName),
                     testName);
    }


    /**
     * Run the prime number test.
     * 
     * @param maxIterations Number of iterations to run the test
     * @param primeCache Cache that maps candidate primes to their
     * smallest factor (if they aren't prime) or 0 if they are prime
     * @param testName Name of the test
     */
    private void runTest(int maxIterations,
                         Map<Integer, Integer> primeCache,
                         String testName) {
        try {
            System.out.println("Starting " + testName);

            // Exit barrier that keeps track of when the tasks finish.
            CountDownLatch exitBarrier =
                new CountDownLatch(sNUMBER_OF_CORES);

            // Random number generator.
            Random random = new Random();

            // Runnable checks if maxIterations random # are prime.
            Runnable primeChecker = () -> {
                for (int i = 0; i < maxIterations; i++) {
                    // Get the next random number.
                    int primeCandidate = 
                    Math.abs(random.nextInt(maxIterations) + 1);

                    // computeIfAbsent() first checks to see if the
                    // factor for this number is already in the cache.
                    // If not, it atomically determines if this number
                    // is prime and stores it in the cache.
                    int smallestFactor = primeCache
                                .computeIfAbsent(primeCandidate,
                                             this::isPrime);

                    if (smallestFactor != 0) {
                        logDebug(""
                                + Thread.currentThread()
                                + ": "
                                + primeCandidate
                                + " is not prime with smallest factor "
                                + smallestFactor);
                    } else {
                        logDebug(""
                                + Thread.currentThread()
                                + ": "
                                + primeCandidate
                                + " is prime");
                    }
                }

                // Inform the waiting thread that we're done.
                exitBarrier.countDown();
            };

            // Create a group of tasks that run the prime checker
            // lambda.
            for (int i = 0; i < sNUMBER_OF_CORES; i++)
                mExecutor.execute(primeChecker);

            // Wait until we're done.
            exitBarrier.await();

            System.out.println("Leaving " + testName);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method provides a brute-force determination of whether
     * number {@code primeCandidate} is prime.  Returns 0 if it is
     * prime, or the smallest factor if it is not prime.
     */
    private Integer isPrime(Integer primeCandidate) {
        int n = primeCandidate;

        if (n > 3)
            // This algorithm is intentionally inefficient to burn
            // lots of CPU time!
            for (int factor = 2;
                 factor <= n / 2;
                 ++factor)
                if (Thread.interrupted()) {
                    System.out.println(""
                                       + Thread.currentThread()
                                       + " Prime checker thread interrupted");
                    break;
                } else if (n / factor * factor == n)
                    return factor;

        return 0;
    }

    /**
     * Demonstrate how to slice by applying the Java streams {@code
     * dropWhile()} and {@code takeWhile()} operations to the {@code
     * map} parameter.
     */
    private void demonstrateSlicing(Map<Integer, Integer> map) {
        // Create a map that's sorted by the value in map.
        Map<Integer, Integer> sortedMap = map
            // Get the EntrySet of the map.
            .entrySet()
            
            // Convert the EntrySet into a stream.
            .stream()

            // Sort the elements in the stream by the value.
            .sorted(comparingByValue())

            // Trigger intermediate processing and collect the
            // key/value pairs in the stream into a LinkedHashMap,
            // which preserves the sorted order.
            .collect(toMap(Map.Entry::getKey,
                           Map.Entry::getValue,
                           (e1, e2) -> e2,
                           LinkedHashMap::new));

        // Print out the entire contents of the sorted map.
        System.out.println("map sorted by value = \n" + sortedMap);

        // Print out the prime numbers using takeWhile().
        printPrimes(sortedMap);

        // Print out the non-prime numbers using dropWhile().
        printNonPrimes(sortedMap);
    }
    
    /**
     * Print out the prime numbers in the {@code sortedMap}.
     */
    private void printPrimes(Map<Integer, Integer> sortedMap) {
        // Create a list of prime integers.
        List<Integer> primes = sortedMap
            // Get the EntrySet of the map.
            .entrySet()
            
            // Convert the EntrySet into a stream.
            .stream()

            // Slice the stream using a predicate that stops after a
            // non-prime number (i.e., getValue() != 0) is reached.
            .takeWhile(entry -> entry.getValue() == 0)

            // Map the EntrySet into just the key.
            .map(Map.Entry::getKey)

            // Collect the results into a list.
            .collect(toList());

        // Print out the list of primes.
        System.out.println("primes =\n" + primes);
    }

    /**
     * Print out the non-prime numbers and their factors in the {@code
     * sortedMap}.
     */
    private void printNonPrimes(Map<Integer, Integer> sortedMap) {
        // Create a list of non-prime integers and their factors.
        List<Map.Entry<Integer, Integer>> nonPrimes = sortedMap
            // Get the EntrySet of the map.
            .entrySet()
            
            // Convert the EntrySet into a stream.
            .stream()

            // Slice the stream using a predicate that skips over the
            // non-prime numbers (i.e., getValue() == 0);
            .dropWhile(entry -> entry.getValue() == 0)

            // Collect the results into a list.
            .collect(toList());

        // Print out the list of primes.
        System.out.println("non-prime numbers and their factors =\n"
                           + nonPrimes);
    }

    /**
     * Display the string if sVERBOSE is set.
     */
    private void logDebug(String string) {
        if (sVERBOSE)
            System.out.println();
    }

}
    
