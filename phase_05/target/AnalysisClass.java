import java.io.IOException;
import java.util.List;

public class AnalysisClass {
    @FunctionalInterface
    interface ThrowableProcessor {
        int process(int val) throws IOException;
    }

    private class PrimeNumber {
        public static void main(String[] args) {
            int count = 0;
            int number = 2;
            System.out.print("First 10 primes: ");

            while (count < 10) {
                boolean isPrime = true;
                for (int i = 2; i <= number / 2; i++) {
                    if (number % i == 0) {
                        isPrime = false;
                        break;
                    }
                }
                if (isPrime) {
                    System.out.print(number + (count < 9 ? " -> " : ""));
                    count++;
                }
                number++;
            }
            System.out.println(); // Prints a new line at the end
        }
    }

    private class User {
        public boolean isValidUser(String username, int age, boolean isAdmin) {
            if (username == null || username.trim().isEmpty()) {
                return false;
            }

            if (isAdmin || (age >= 18 && age <= 65)) {
                return true;
            }

            return false;
        }
    }

    private class SwitchCase {
        public String evaluateGrade(char grade) {
            String feedback;
            switch (grade) {
                case 'A':
                case 'B':
                    feedback = "Good job";
                    break;
                case 'C':
                    feedback = "Average";
                    break;
                case 'D':
                case 'F':
                    feedback = "Needs Improvement";
                    break;
                default:
                    feedback = "Invalid Grade";
                    break;
            }
            return feedback;
        }
    }

    public static int runCFGStressTest(List<String> inputs, int mode) {
        int acc = 0;

        // 1. Exceptional edges & Multiple Catch/Finally blocks
        try {
            if (inputs == null) {
                throw new NullPointerException("Explicit NPE");
            }

            // 2. Short-circuit logical branching + Nested loops with labels
            OUTER_LOOP:
            for (int i = 0; i < inputs.size(); i++) {
                String str = inputs.get(i);

                // Complex conditional (LOOKUPSWITCH vs TABLESWITCH bytecode generation)
                switch (mode) {
                    case 1:
                    case 2:
                        if (str == null || str.isEmpty()) {
                            continue OUTER_LOOP; // Labeled continue (back-edge)
                        }
                        acc += str.length();
                        break;

                    case 100: // Sparse switch value forces LOOKUPSWITCH
                        if (str.equals("BREAK")) {
                            break OUTER_LOOP; // Labeled break (exit edge)
                        }
                        acc += 100;
                        break;

                    default:
                        // 3. Lambda / InvokeDynamic edge inside a loop
                        ThrowableProcessor processor = (v) -> {
                            if (v < 0) throw new IOException("Negative");
                            return v * 2;
                        };

                        try {
                            acc += processor.process(str.length());
                        } catch (IOException e) {
                            acc -= 1; // Exceptional control flow inside loop body
                        }
                        break;
                }

                // 4. Ternary operator + Infinite/Do-While construct
                int retryCount = 0;
                do {
                    retryCount++;
                    if (retryCount > 3) break;
                    acc += (retryCount % 2 == 0) ? 10 : 20;
                } while (true);
            }

        } catch (NullPointerException e) {
            // Implicit vs Explicit exception handling basic block
            acc = -1;
            return acc; // Early return inside catch
        } catch (Exception e) {
            acc = -999;
        } finally {
            // 5. Finally block (WALA must replicate or cleanly exit finally basic blocks)
            acc += 1;
        }

        return acc;
    }
}