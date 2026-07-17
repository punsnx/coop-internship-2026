public class DeadCode {

    public static int getActiveValue() {
        return 10;
    }

    public static int getDeadValue() {
        return 20;
    }

    public static void main(String[] args) {
        int active = getActiveValue();
        int dead = getDeadValue();
        System.out.println(active);

        int declare = getActiveValue();
        declare = getDeadValue();
        System.out.println(declare);
    }
}