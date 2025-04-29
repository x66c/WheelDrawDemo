package com.example.demo;

import java.util.concurrent.atomic.AtomicInteger;

public class Prize {
    private final String id;        // 獎品唯一ID
    private final String name;      // 獎品名稱
    private final int totalQuantity; // 初始總數量
    private final AtomicInteger remainingQuantity; // 剩餘數量 (線程安全)
    private final double probability; // 中獎機率 (0.0 to 1.0)

    // 特殊獎品：銘謝惠顧
    public static final Prize THANK_YOU = new Prize("THANK_YOU", "銘謝惠顧", -1, 0.0); // 數量無限，機率由外部計算補足

    public Prize(String id, String name, int initialQuantity, double probability) {
        if (probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("Probability must be between 0.0 and 1.0");
        }
        if (initialQuantity < 0 && initialQuantity != -1) { // -1 代表無限
            throw new IllegalArgumentException("Initial quantity cannot be negative (use -1 for infinite)");
        }
        this.id = id;
        this.name = name;
        this.totalQuantity = initialQuantity;
        // 如果是無限數量，原子計數器內部可以不嚴格管理，但為了統一接口，還是創建
        this.remainingQuantity = new AtomicInteger(initialQuantity);
        this.probability = probability;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public AtomicInteger getRemainingQuantity() {
        return remainingQuantity;
    }

    public double getProbability() {
        return probability;
    }

    public boolean isInfinite() {
        return this.totalQuantity == -1;
    }

    // 嘗試扣減庫存，成功返回 true，失敗返回 false
    public boolean decrementStock() {
        if (isInfinite()) {
            return true; // 無限庫存永遠成功
        }
        // 使用 CAS (Compare-And-Swap) 循環來確保原子性扣減
        while (true) {
            int currentStock = remainingQuantity.get();
            if (currentStock <= 0) {
                return false; // 沒有庫存了
            }
            // 嘗試將庫存從 currentStock 減到 currentStock - 1
            if (remainingQuantity.compareAndSet(currentStock, currentStock - 1)) {
                return true; // 扣減成功
            }
            // 如果 compareAndSet 返回 false，表示在讀取和嘗試設置之間，值被其他線程改變了，循環重試
        }
    }

    @Override
    public String toString() {
        return "Prize{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               ", remaining=" + (isInfinite() ? "∞" : remainingQuantity.get()) +
               ", probability=" + probability +
               '}';
    }
}