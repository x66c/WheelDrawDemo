# Java 電商轉盤抽獎功能實現

這是一個使用 Java 實現的電商轉盤抽獎系統範例。其設計目標是提供一個健壯、線程安全的抽獎機制，適用於可能有多個用戶同時參與抽獎的場景。

## 主要功能

*   **多獎品配置:** 支持設定多種不同的獎品。
*   **獨立庫存:** 每種獎品擁有獨立的初始數量。
*   **自訂機率:** 每種獎品（包括“銘謝惠顧”）可以設置不同的中獎機率，所有機率總和必須為 100%。
*   **併發安全:** 允許多個用戶同時發起抽獎請求，並確保數據一致性。
*   **防止超抽:** 嚴格控制獎品發放數量，即使在高併發下也不會超出設定的庫存。
*   **防止重複抽獎:** 透過唯一的請求 ID 識別並阻止同一個抽獎行為被重複處理。

## 設計思路與核心原則

本實現的核心設計思路圍繞以下幾點：

1.  **物件導向封裝:**
    *   **`Prize` 類:** 封裝了單個獎品的屬性，包括 ID、名稱、初始總量、**原子化的剩餘數量 (`AtomicInteger`)** 以及中獎機率。它還包含了一個關鍵的原子操作方法 `decrementStock()` 來安全地扣減庫存。
    *   **`WheelDraw` 類:** 代表抽獎輪盤/系統本身。它管理著獎品列表，並提供核心的 `draw()` 方法來執行抽獎邏輯。

2.  **機率模型 - 累積機率法:**
    *   `draw()` 方法內部使用累積機率。它生成一個 0 到 1 之間的隨機數，然後遍歷獎品列表，將每個獎品的機率累加起來。當累積機率首次超過隨機數時，對應的獎品即為本次“命中”的潛在獎品。

3.  **併發控制與原子操作:**
    *   **獎品庫存 (`AtomicInteger`):** `Prize` 類中的 `remainingQuantity` 使用 `java.util.concurrent.atomic.AtomicInteger`。這確保了對庫存數量的讀取和修改（尤其是扣減）操作是原子的，避免了在高併發環境下多個線程同時修改庫存導致的數據不一致（Race Condition）和超抽問題。`decrementStock()` 方法內部使用了 `compareAndSet` 或 `decrementAndGet` 等原子操作。
    *   **重複請求防範 (`ConcurrentHashMap.newKeySet()`):** `LotteryWheel` 使用一個線程安全的 `Set` (`ConcurrentHashMap.newKeySet()`) 來存儲已經處理過的請求 ID (`processedRequests`)。`Set.add()` 方法本身是原子的，可以保證在高併發下，對於同一個 `requestId`，只有第一次調用 `add()` 會成功返回 `true` 並被處理，後續的重複請求會因 `add()` 返回 `false` 而被直接拒絕。

4.  **線程安全隨機數:**
    *   使用 `java.util.concurrent.ThreadLocalRandom` 來生成隨機數。相比於全局共享的 `Random` 實例或 `Math.random()`，它在多線程環境下具有更好的性能，可以減少線程間的競爭。

## 核心組件詳解

*   **`Prize.java`:**
    *   `id`, `name`: 獎品標識。
    *   `totalQuantity`: 初始總量 (用於參考)。
    *   `remainingQuantity (AtomicInteger)`: 線程安全的剩餘庫存計數器。
    *   `probability`: 中獎機率 (0.0 - 1.0)。
    *   `decrementStock()`: **關鍵方法**。以原子方式嘗試扣減庫存，成功返回 `true`，庫存不足或扣減失敗返回 `false`。
    *   `isInfinite()`: 判斷是否為無限量獎品（如銘謝惠顧）。

*   **`WheelDraw.java`:**
    *   `prizes (List<Prize>)`: 持有所有獎品配置的列表。
    *   `processedRequests (Set<String>)`: 線程安全的 Set，用於記錄已處理的請求 ID，防止重複抽獎。
    *   `draw(String requestId)`: **核心抽獎邏輯**。
        1.  **檢查重複:** 使用 `processedRequests.add(requestId)` 判斷是否為重複請求。
        2.  **機率抽獎:** 生成隨機數，根據累積機率確定潛在獎品。
        3.  **庫存確認與扣減:** 如果抽中實際獎品（非銘謝惠顧），調用 `potentialPrize.decrementStock()` 嘗試原子扣減庫存。
        4.  **返回結果:** 根據庫存扣減是否成功，返回實際抽中的獎品或銘謝惠顧。

*   **`WheelDrawDemo.java`:**
    *   提供了一個使用 `ExecutorService` 模擬多線程併發抽獎的演示場景。展示了如何初始化獎品、創建抽獎輪盤以及調用 `draw` 方法。

## 如何運行 (示例)

1.  確保 `Prize.java`, `WheelDraw.java`, `WheelDrawDemo.java` 在同一項目路徑下。
2.  編譯所有 Java 文件：
    ```bash
    javac Prize.java WheelDraw.java WheelDrawDemo.java
    ```
3.  運行演示程序：
    ```bash
    java WheelDrawDemo
    ```
    觀察控制台輸出，可以看到併發抽獎的過程、重複請求被拒絕的情況以及最終的獎品剩餘數量。

## 潛在改進與注意事項

*   **`processedRequests` 清理:** 當前實現中，`processedRequests` 會持續增長。在實際生產環境中，這可能導致內存洩漏。需要實現清理策略，例如：
    *   使用帶有過期時間的緩存（如 Google Guava Cache, Caffeine）。
    *   定期任務清理過舊的請求 ID。
    *   限制 Set 的最大容量並使用某種淘汰策略（如 LRU）。
*   **配置化:** 可以將獎品配置（名稱、數量、機率）從硬編碼改為從配置文件、數據庫或配置中心讀取，提高靈活性。
*   **日誌與監控:** 添加更詳細的日誌記錄（例如，哪個用戶抽中了什麼獎）和監控指標（例如，獎品剩餘數量、抽獎速率、錯誤率）。
*   **更複雜的抽獎策略:** 可以擴展以支持更複雜的規則，例如：用戶抽獎次數限制、保底機制、根據用戶標籤區分獎池等。
*   **事務性:** 如果抽獎成功後需要執行其他操作（如發放獎品到用戶賬戶），可能需要考慮分佈式事務或最終一致性方案來確保整個流程的原子性。