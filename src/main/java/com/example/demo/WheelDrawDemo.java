package com.example.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WheelDrawDemo {

    public static void main(String[] args) throws InterruptedException {
        // 1. 初始化獎品列表
        List<Prize> prizes = new ArrayList<>();
        prizes.add(new Prize("P001", "一等獎：iPhone 16 Pro Max 一支", 1, 0.01)); // 1% 機率
        prizes.add(new Prize("P002", "二等獎：AirPods 4 一副", 5, 0.05)); // 5% 機率
        prizes.add(new Prize("P003", "三等獎：$10 優惠券", 50, 0.20)); // 20% 機率
        // 銘謝惠顧的機率是 1.0 減去其他所有獎品的機率總和
        double thankYouProbability = 1.0 - prizes.stream().mapToDouble(Prize::getProbability).sum();
        prizes.add(new Prize(Prize.THANK_YOU.getId(), Prize.THANK_YOU.getName(), -1, thankYouProbability)); // 74% 機率

        // 2. 創建抽獎輪盤
        WheelDraw wheelDraw = new WheelDraw(prizes);

        System.out.println("抽獎開始前獎品狀態:");
        wheelDraw.getCurrentPrizeStatus().forEach(System.out::println);
        System.out.println("------------------------------------");

        // 3. 模擬併發抽獎
        int numberOfDraws = 100; // 模擬抽獎總次數
        ExecutorService executor = Executors.newFixedThreadPool(10); // 創建一個有 10 個線程的線程池

        // 模擬重複抽獎的情況
        String duplicateRequestId = "REQ-DUPLICATE-123";

        for (int i = 0; i < numberOfDraws; i++) {
            final String requestId = "REQ-" + UUID.randomUUID().toString().substring(0, 8); // 產生唯一請求 ID
            executor.submit(() -> {
                wheelDraw.draw(requestId);
            });

            // 隨機插入一些重複請求的嘗試
            if (i % 20 == 0) {
                 System.out.println(">>> 嘗試提交重複請求: " + duplicateRequestId);
                 executor.submit(() -> wheelDraw.draw(duplicateRequestId));
                 executor.submit(() -> wheelDraw.draw(duplicateRequestId)); // 再次嘗試同一個
            }
        }

        // 4. 等待所有抽獎任務完成
        executor.shutdown(); // 不再接受新任務
        // 等待最多 1 分鐘讓現有任務完成
        if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
            System.err.println("抽獎任務未在指定時間內完成！");
            executor.shutdownNow(); // 強制停止
        }

        System.out.println("------------------------------------");
        System.out.println("抽獎結束後獎品狀態:");
        wheelDraw.getCurrentPrizeStatus().forEach(System.out::println);
        System.out.println("------------------------------------");
        System.out.println("已處理的請求數量 (包含被拒絕的重複請求的首次嘗試): " + wheelDraw.processedRequests.size());
    }
}