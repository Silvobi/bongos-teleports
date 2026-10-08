package pl.bongo.teleports;

public final class Rules {
    private Rules() {}
    public static boolean inDeadZone(double x,double z,double centerX,double centerZ) {
        return Math.hypot(x-centerX,z-centerZ)<=1+1e-9;
    }
    public static int cost(double x1, double z1, double x2, double z2, boolean enabled, double rate) {
        if (!enabled || rate == 0) return 0;
        double result = Math.ceil(Math.hypot(x1-x2, z1-z2) * rate);
        return result >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }
    public static long xpAtLevel(int level) {
        if (level <= 16) return (long) level * level + 6L * level;
        if (level <= 31) return (5L * level * level - 81L * level + 720) / 2;
        return (9L * level * level - 325L * level + 4440) / 2;
    }
    public static int levelForXp(long points) {
        int low=0, high=100000;
        while (low < high) {
            int mid=(low+high+1)/2;
            if (xpAtLevel(mid) <= points) low=mid; else high=mid-1;
        }
        return low;
    }
}
