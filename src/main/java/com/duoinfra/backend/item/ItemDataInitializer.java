package com.duoinfra.backend.item;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ItemDataInitializer implements CommandLineRunner {

    private final ItemRepository itemRepository;

    public ItemDataInitializer(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Override
    public void run(String... args) {
        if (itemRepository.count() == 0) {
            itemRepository.save(new Item("Item A", "첫 번째 샘플 아이템"));
            itemRepository.save(new Item("Item B", "두 번째 샘플 아이템"));
            itemRepository.save(new Item("Item C", "세 번째 샘플 아이템"));
        }
    }
}
