package com.example.demo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class WheelDraw {

    private final List<Prize> prizes;
    private final double totalProbability;
    // 用於儲存已處理的抽獎請求 ID，防止重複處理 (線程安全)
    final Set<String> processedRequests = ConcurrentHashMap.newKeySet();

    public WheelDraw(List<Prize> initialPrizes) {
        if (initialPrizes == null || initialPrizes.isEmpty()) {
            throw new IllegalArgumentException("Prize list cannot be empty.");
        }

        // 確保獎品列表是不可變的或至少是線程安全的副本
        this.prizes = Collections.unmodifiableList(new ArrayList<>(initialPrizes));

        // 驗證總機率接近 1.0 (考慮浮點數精度)
        this.totalProbability = prizes.stream().mapToDouble(Prize::getProbability).sum();
        if (Math.abs(totalProbability - 1.0) > 1e-9) { // 允許微小的精度誤差
            throw new IllegalArgumentException("Total probability of prizes must sum up to 1.0. Current sum: " + totalProbability);
        }
    }

    /**
     * 執行抽獎
     * @param requestId 唯一的請求 ID，用於防止重複抽獎
     * @return 返回抽中的獎品 (可能是 Prize.THANK_YOU)
     */
    public Prize draw(String requestId) {
        // 1. 防止重複抽獎
        // ConcurrentHashMap.newKeySet().add() 是原子操作
        // 如果 requestId 已經存在，add 會返回 false
        if (!processedRequests.add(requestId)) {
            System.out.println("Request ID: " + requestId + " - 重複抽獎請求，已忽略。");
            // 可以返回一個特殊的狀態或 null，或者拋出異常，這裡返回 null 示意
            return null; // 或者返回一個表示重複的特殊 Prize 實例
        }

        // 2. 根據機率選擇潛在獎品
        double randomValue = ThreadLocalRandom.current().nextDouble(); // 產生 0.0 (含) 到 1.0 (不含) 的隨機數
        double cumulativeProbability = 0.0;
        Prize potentialPrize = Prize.THANK_YOU; // 預設為銘謝惠顧

        for (Prize prize : prizes) {
            cumulativeProbability += prize.getProbability();
            if (randomValue < cumulativeProbability) {
                potentialPrize = prize;
                break;
            }
        }

        // 3. 檢查庫存並嘗試扣減 (原子操作)
        if (potentialPrize != Prize.THANK_YOU) {
            if (potentialPrize.decrementStock()) {
                // 成功扣減庫存，中獎！
                System.out.println("Request ID: " + requestId + " - 恭喜！抽中了: " + potentialPrize.getName());
                return potentialPrize;
            } else {
                // 庫存不足，雖然按機率抽中了，但實際無獎品可發
                System.out.println("Request ID: " + requestId + " - 抽中了 " + potentialPrize.getName() + "，但獎品已發完。");
                // 這裡可以選擇返回銘謝惠顧，或者一個表示“抽中但無庫存”的狀態
                return Prize.THANK_YOU;
            }
        } else {
            // 按機率抽中了銘謝惠顧
            System.out.println("Request ID: " + requestId + " - 銘謝惠顧。");
            return Prize.THANK_YOU;
        }

        // 注意：processedRequests 會持續增長，實際生產環境需要考慮清理策略，
        // 例如：使用有過期機制的緩存 (如 Guava Cache, Caffeine) 或定期清理。
    }

    // 獲取當前獎品庫存情況 (用於觀察)
    public List<Prize> getCurrentPrizeStatus() {
        // 返回一個副本以避免外部修改
        return prizes.stream()
                     .map(p -> new Prize(p.getId(), p.getName(), p.getRemainingQuantity().get(), p.getProbability()))
                     .collect(Collectors.toList());
    }
}