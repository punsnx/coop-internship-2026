public class Main {
    static int compute(int n) {
        int unusedInCompute = 100;
        int total = 0;
        for (int i = 0; i < n; i++) {
            total = total + i;
        }
        return total;
    }

    static void branch(boolean flag) {
        int x = 1;
        if (flag) {
            x = 2;
        } else {
            x = 3;
        }
        System.out.println(x);
    }

    private static void testfunc(int meow) {
        System.out.println("Meow"); // Should report deadstore
    }

    private static int outsider = 99; // This should also report deadstore

    // Next goal:
    // - Support multi classes within one file, nested classes
    // - (Future and Optional) Support multi across files

    public static void main(String[] args) {
        int test = 67;
        testfunc(test); // Should report deadstore

        int result = compute(5);
        System.out.println(result);
        branch(true);
        int dead = 42;
    }
}