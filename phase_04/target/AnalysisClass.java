public class AnalysisClass {
    public int testMethod(int x) {
        int y = 0;
        if (x > 10) {
            y = x * 2;
        } else {
            y = x + 5;
        }
        while (y > 0) {
            y--;
        }
        return y;
    }
}