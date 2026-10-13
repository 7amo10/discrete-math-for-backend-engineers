package com.backend.discrete.week02_hashing;

/**
 * Number theoretic utility methods for modular arithmetic, greatest common divisor,
 * Bézout identity, and multiplicative inverses.
 */
public final class NumberTheory {

    private NumberTheory() {
        // Utility class
    }

    /**
     * Coefficients satisfying Bézout's identity: gcd = s * a + t * b.
     */
    public record BezoutCoefficients(long gcd, long s, long t) {}

    /**
     * Computes the greatest common divisor of two integers using the Euclidean algorithm.
     */
    public static long gcd(long a, long b) {
        long x = Math.abs(a);
        long y = Math.abs(b);
        while (y != 0) {
            long temp = y;
            y = x % y;
            x = temp;
        }
        return x;
    }

    /**
     * Computes integers s and t such that s * a + t * b = gcd(a, b) using the extended Euclidean algorithm.
     */
    public static BezoutCoefficients pulverizer(long a, long b) {
        long s0 = 1, s1 = 0;
        long t0 = 0, t1 = 1;
        long r0 = a, r1 = b;

        while (r1 != 0) {
            long quotient = r0 / r1;

            long rTemp = r0 - quotient * r1;
            r0 = r1;
            r1 = rTemp;

            long sTemp = s0 - quotient * s1;
            s0 = s1;
            s1 = sTemp;

            long tTemp = t0 - quotient * t1;
            t0 = t1;
            t1 = tTemp;
        }

        return new BezoutCoefficients(r0, s0, t0);
    }

    /**
     * Computes the modular multiplicative inverse of a modulo m such that (a * x) % m == 1.
     *
     * @throws ArithmeticException if gcd(a, m) != 1
     */
    public static long modularInverse(long a, long m) {
        if (m <= 0) {
            throw new IllegalArgumentException("Modulus must be strictly positive: " + m);
        }
        BezoutCoefficients result = pulverizer(a, m);
        if (result.gcd() != 1) {
            throw new ArithmeticException(
                    "Modular inverse does not exist: gcd(" + a + ", " + m + ") = " + result.gcd() + " != 1"
            );
        }
        long inv = result.s() % m;
        return (inv < 0) ? inv + m : inv;
    }

    /**
     * Computes a non-negative remainder a mod m in the range [0, m - 1].
     */
    public static long mod(long a, long m) {
        if (m <= 0) {
            throw new IllegalArgumentException("Modulus must be positive: " + m);
        }
        long result = a % m;
        return (result < 0) ? result + m : result;
    }

    /**
     * Computes the clockwise distance from one position to another on a circular ring of given size.
     */
    public static long ringDistance(long from, long to, long ringSize) {
        return mod(to - from, ringSize);
    }

    /**
     * Computes (base^exp) mod m using binary exponentiation.
     */
    public static long modPow(long base, long exp, long m) {
        if (m <= 0) {
            throw new IllegalArgumentException("Modulus must be positive: " + m);
        }
        if (exp < 0) {
            throw new IllegalArgumentException("Exponent must be non-negative: " + exp);
        }
        long result = 1;
        long b = mod(base, m);
        long e = exp;
        while (e > 0) {
            if ((e & 1) == 1) {
                result = mod(result * b, m);
            }
            b = mod(b * b, m);
            e >>= 1;
        }
        return result;
    }
}
