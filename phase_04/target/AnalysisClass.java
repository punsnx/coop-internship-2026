public class AnalysisClass {
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