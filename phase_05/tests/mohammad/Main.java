package com.example;

public class Main {
    private class MyNestedClass {
        public int myNestedField = 12; // DEAD STORE

        private void myNestedMethod(int myPar1) { // DEAD STORE
            System.out.println("Method in the nested class.");
        }
    }

    private static int myField = 13; // DEAD STORE
    public static void main(String[] args) {
        int myVar = 14;
        int myVar2 = 23; // DEAD STORE

        myMethod(myVar);
    }

    private static void myMethod(int myPar2) { // DEAD STORE
        System.out.println("Method in the class.");
    }
}