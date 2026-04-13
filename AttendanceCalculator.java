public final class AttendanceCalculator {
    private AttendanceCalculator() {}

    public static final int MIN_PERCENT = 75;

    public static boolean meetsMinimum(int attended, int totalHeld) {
        if (attended < 0 || totalHeld < 0 || attended > totalHeld) {
            throw new IllegalArgumentException("Invalid counts");
        }
        return 4L * attended >= 3L * totalHeld; // attended/total >= 3/4 (75%)
    }

    // Smallest x such that (attended + x) / (totalHeld + x) >= 75%
    public static int classesNeededToReachMinimum(int totalHeld, int attended) {
        if (attended < 0 || totalHeld < 0 || attended > totalHeld) {
            throw new IllegalArgumentException("Invalid counts");
        }
        long x = 3L * totalHeld - 4L * attended;
        return (int) Math.max(0L, x);
    }

    // Largest l such that attended / (totalHeld + l) >= 75%
    public static int leavesYouCanTakeNow(int totalHeld, int attended) {
        if (attended < 0 || totalHeld < 0 || attended > totalHeld) {
            throw new IllegalArgumentException("Invalid counts");
        }
        long numerator = 4L * attended - 3L * totalHeld;
        if (numerator <= 0) return 0;
        return (int) (numerator / 3L); // floor
    }

    // Minimum number of upcoming classes to attend to ensure:
    // (attended + x) / (totalHeld + upcoming) >= 75%, where 0 <= x <= upcoming
    public static int minUpcomingAttendanceNeeded(int totalHeld, int attended, int upcoming) {
        if (attended < 0 || totalHeld < 0 || upcoming < 0 || attended > totalHeld) {
            throw new IllegalArgumentException("Invalid counts");
        }
        long needed = ceilDiv(3L * (totalHeld + (long) upcoming) - 4L * attended, 4L);
        long clamped = Math.max(0L, Math.min((long) upcoming, needed));
        return (int) clamped;
    }

    private static long ceilDiv(long a, long b) {
        if (b <= 0) throw new IllegalArgumentException("b must be positive");
        if (a <= 0) return 0;
        return (a + b - 1) / b;
    }
}

