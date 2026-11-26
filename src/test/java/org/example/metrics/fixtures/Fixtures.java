package org.example.metrics.fixtures;

public class Fixtures {
    public interface Shape { double area(); Object self(); }

    public static class Base implements Shape {
        int a;
        static String s;
        public double area() { return 0; }
        public Object self() { return this; }
        void pkg() {}
        private void priv() {}
        static void stat() {}
    }

    public static class Mid extends Base {
        @Override public double area() { return 1; }   // override
        public double area(int scale) { return scale; } // overload
        private void priv() {}                          // not an override
        static void stat() {}                           // hiding, not override
    }

    public static class Leaf extends Mid implements Comparable<Leaf> {
        long x, y, z;
        @Override public Leaf self() { return this; }   // covariant -> bridge
        @Override void pkg() {}                         // package-private override
        public int compareTo(Leaf o) { return 0; }      // Comparable not in jar -> not counted
    }

    public static class Abc {
        int f(int x) {
            int y = x + 1;
            if (x > 0) {
                y = g(y);
            }
            return y;
        }
        static int g(int v) { return v; }
    }
}
