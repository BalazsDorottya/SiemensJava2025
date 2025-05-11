package com.siemens.internship;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ItemService {
    @Autowired
    private ItemRepository itemRepository;

    // Reuse Spring's executor is recommended, but keeping your structure:
    private static ExecutorService executor = Executors.newFixedThreadPool(10);

    // Use thread-safe list
    private List<Item> processedItems = new CopyOnWriteArrayList<>();

    // Use thread-safe counter
    private AtomicInteger processedCount = new AtomicInteger(0);

    public List<Item> findAll() {
        return itemRepository.findAll();
    }

    public Optional<Item> findById(Long id) {
        return itemRepository.findById(id);
    }

    public Item save(Item item) {
        return itemRepository.save(item);
    }

    public void deleteById(Long id) {
        itemRepository.deleteById(id);
    }

    @Async
    public CompletableFuture<List<Item>> processItemsAsync() {
        List<Long> itemIds = itemRepository.findAllIds();

        List<CompletableFuture<Void>> futures = itemIds.stream()
                .map(id -> CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(100); // Simulate processing

                        Item item = itemRepository.findById(id).orElse(null);
                        if (item == null) {
                            return;
                        }

                        processedCount.incrementAndGet();

                        item.setStatus("PROCESSED");
                        itemRepository.save(item);
                        processedItems.add(item);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // Proper interruption handling
                        throw new RuntimeException("Thread was interrupted while processing item with ID: " + id, e);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to process item with ID: " + id, e);
                    }
                }, executor)).toList();

        // Wait for all futures to complete, and return result list when done
        return CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> processedItems);
    }
}
