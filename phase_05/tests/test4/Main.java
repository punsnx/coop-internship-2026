public class Main {
    static int compute(int n) {
        int unusedInCompute = 100; //
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
        System.out.println("Meow");
    }

    private static int outsider = 99; //

    public static void main(String[] args) {
        int test = 67;
        testfunc(test);

        int result = compute(5);
        System.out.println(result);
        branch(true);
        int dead = 42; //
    }
}