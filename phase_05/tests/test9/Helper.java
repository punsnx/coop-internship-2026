package demo;
public class Helper {
    public static int usedByMain = 1;   // read in Main  -> should be ALIVE
    public static int neverRead   = 2;   // read nowhere  -> should be DEAD
}