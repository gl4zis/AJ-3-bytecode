package org.example.agent.demo;

// Line markers are read by CoverageAgentTest: a "hit" marker means covered, "miss" means never executed.
public class CoverageDemo { // miss (implicit constructor is never called)
    public static void main(String[] args) {
        int sum = 0; // hit
        for (int i = 0; i < 3; i++) { // hit
            sum += i; // hit
        }
        if (sum > 100) { // hit
            System.out.println("big"); // miss
        }
        System.out.println(classify(sum)); // hit
    } // hit (implicit return of a void method)

    static String classify(int n) {
        if (n % 2 == 0) { // hit
            return "even"; // miss
        }
        return "odd"; // hit
    }

    static void neverCalled() {
        System.out.println("never"); // miss
    } // miss (implicit return)
}
