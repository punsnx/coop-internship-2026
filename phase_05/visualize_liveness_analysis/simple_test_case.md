## Sample Input

`Main.java`

```java
public class Main {
    public static void main(String[] args) {
        int alive = compute();
        int dead = compute2();
        System.out.println(alive);
    }

    static int compute() { return 10; }
    static int compute2() { return 20; }
}
```

## Run Instructions

```bash
./gradlew run -PmainClass=com.ibm.wala.examples.drivers.LivenessAnalysisDriver \
--args="testdata/com/deadstoretest/01_straight_line/scopeFile.txt testdata/com/deadstoretest/01_straight_line/Main.java true"
```