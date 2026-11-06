package com.tacz.guns.compat.jyvr;

/** Conservation rules shared by inventory actions and the regression tests. */
public final class MagazineMath {
    public record Chamber(int magazine, boolean loaded) {}
    public static Chamber rack(int magazine, boolean loaded) {
        if (magazine<0) throw new IllegalArgumentException("negative ammo");
        return loaded || magazine==0 ? new Chamber(magazine,loaded) : new Chamber(magazine-1,true);
    }
    public record Transfer(int gun, int magazine) {}
    public static Transfer insert(int gun, int magazine, int capacity) {
        if (gun != 0 || magazine < 0 || capacity < 1 || magazine > capacity)
            throw new IllegalArgumentException("incompatible magazine");
        return new Transfer(magazine, 0);
    }
    public static int take(int available, int room) {
        if (available < 0 || room < 0) throw new IllegalArgumentException("negative ammo");
        return Math.min(available, room);
    }
}
