public class Main {
    class Outer {
        int outerField = 1;

        class Inner {
            int innerField = 2;

            void innerMethod(int innerPar) {
                System.out.println("inner");
            }
        }

        void outerMethod(int outerPar) {
            System.out.println("outer");
        }
    }

    static int topField = 3;

    public static void main(String[] args) {
        int local = 4;
        System.out.println("main");
    }
}