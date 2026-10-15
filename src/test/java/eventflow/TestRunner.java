package eventflow;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

// A tiny test runner, so no JUnit is needed.
public final class TestRunner {

    private static final List<Class<?>> SUITES = List.of(
            NoOversellingTest.class,
            HoldRulesTest.class,
            HoldExpiryTest.class,
            CheckoutTest.class,
            PaymentExpiryRaceTest.class,
            QueueTest.class,
            QueueLoadTest.class,
            TicketTest.class,
            PricingTest.class,
            ReleaseTest.class,
            ArchitectureTest.class);

    public static void main(String[] args) throws Exception {
        int passed = 0;
        int failed = 0;
        for (Class<?> suite : SUITES) {
            System.out.println(suite.getSimpleName());
            Method[] methods = suite.getDeclaredMethods();
            Arrays.sort(methods, Comparator.comparing(Method::getName));
            for (Method method : methods) {
                if (!method.isAnnotationPresent(Test.class)) {
                    continue;
                }
                long start = System.nanoTime();
                try {
                    method.invoke(suite.getDeclaredConstructor().newInstance());
                    passed++;
                    System.out.printf("  PASS  %s  (%d ms)%n", method.getName(),
                            (System.nanoTime() - start) / 1_000_000);
                } catch (InvocationTargetException e) {
                    failed++;
                    System.out.println("  FAIL  " + method.getName() + "  — " + e.getCause());
                }
            }
        }
        System.out.printf("%n%d passed, %d failed%n", passed, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }
}
