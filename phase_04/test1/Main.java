public class Main {
    public static void main(String[] args) {
        int active = 67;
        int dead = 69;
        
        // Only print 67 from "active", 69 in "dead" is never used
        System.out.println(active);
    }
}